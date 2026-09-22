package com.example.automation.core.executor

import com.example.automation.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface RuleRepository {
    suspend fun getAllRules(): List<AutomationRule>
    suspend fun getEnabledRules(): List<AutomationRule>
    suspend fun getRule(id: String): AutomationRule?
    suspend fun saveRule(rule: AutomationRule)
    suspend fun updateRule(rule: AutomationRule)
    suspend fun deleteRule(id: String)
    suspend fun setRuleEnabled(id: String, enabled: Boolean)
    suspend fun recordExecution(rule: AutomationRule, result: ExecutionResult, context: ExecutionContext)
}

interface ExecutionLogger {
    suspend fun log(context: ExecutionContext, result: ExecutionResult)
    suspend fun log(entry: ExecutionLogEntry)
}

class AutomationEngine(
    val accessibilityController: AccessibilityController,
    val actionExecutorRegistry: ActionExecutorRegistry,
    val ruleRepository: RuleRepository,
    private val executionLogger: ExecutionLogger,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val mutex = Mutex()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val _activeExecutions = MutableStateFlow<List<ExecutionContext>>(emptyList())
    val activeExecutions: StateFlow<List<ExecutionContext>> = _activeExecutions.asStateFlow()
    private val _executionState = MutableStateFlow<EngineState>(EngineState.Idle)
    val executionState: StateFlow<EngineState> = _executionState.asStateFlow()
    private var eventJob: Job? = null

    suspend fun start() {
        if (eventJob?.isActive == true) return
        _executionState.value = EngineState.Starting
        accessibilityController.startEventStream()
        eventJob = scope.launch {
            _executionState.value = EngineState.Running
            accessibilityController.eventFlow.collect { event ->
                ruleRepository.getEnabledRules()
                    .filter { it.shouldTrigger(event) }
                    .sortedByDescending { it.priority }
                    .forEach { execute(it, TriggerContext.Event(event)) }
            }
        }
    }

    suspend fun stop() {
        _executionState.value = EngineState.Stopping
        eventJob?.cancel()
        eventJob = null
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        accessibilityController.stopEventStream()
        _activeExecutions.value = emptyList()
        _executionState.value = EngineState.Stopped
    }

    fun execute(rule: AutomationRule, triggerContext: TriggerContext = TriggerContext.Manual): Job {
        val job = scope.launch { executeNow(rule, triggerContext) }
        jobs[rule.id] = job
        job.invokeOnCompletion { jobs.remove(rule.id) }
        return job
    }

    suspend fun executeNow(rule: AutomationRule, triggerContext: TriggerContext = TriggerContext.Manual): ExecutionResult = mutex.withLock {
        val initial = ExecutionContext(
            ruleId = rule.id,
            ruleName = rule.name,
            triggerContext = triggerContext,
            totalActions = rule.actionPlan.actions.size
        )
        _activeExecutions.value += initial
        val variables = mutableMapOf<String, Any>()
        val result = try {
            val failed = rule.preconditions.firstOrNull { !it.evaluate(variables, accessibilityController) }
            if (failed != null) ExecutionResult.PreconditionFailed("Precondition failed: $failed")
            else executeActions(rule.actionPlan.actions, variables, rule.errorHandling)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            ExecutionResult.Cancelled
        } catch (error: Throwable) {
            ExecutionResult.Failure(error.message ?: error::class.simpleName.orEmpty())
        }
        val finished = initial.copy(
            variables = variables.toMap(),
            endTime = Instant.now(),
            status = if (result.isSuccess) ExecutionStatus.COMPLETED else ExecutionStatus.FAILED,
            result = result,
            completedActions = rule.actionPlan.actions.size
        )
        _activeExecutions.value = _activeExecutions.value.filterNot { it.id == initial.id }
        executionLogger.log(finished, result)
        ruleRepository.recordExecution(rule, result, finished)
        result
    }

    private suspend fun executeActions(
        actions: List<Action>,
        variables: MutableMap<String, Any>,
        errorHandling: ErrorHandling
    ): ExecutionResult {
        var completed = 0
        for (action in actions.filter { it.enabled }) {
            val executor = when (val type = action.type) {
                is ActionType.Custom -> actionExecutorRegistry.getCustomExecutor(type.name)
                else -> actionExecutorRegistry.getExecutor(type)
            } ?: return ExecutionResult.Failure("No executor registered for ${action.type}")
            var result = withContext(executor.executionDispatcher) {
                executor.execute(action, variables, accessibilityController)
            }
            if (!result.isSuccess) {
                when (errorHandling) {
                    ErrorHandling.ContinueOnError -> Unit
                    ErrorHandling.RetryOnce -> result = executor.execute(action, variables, accessibilityController)
                    is ErrorHandling.Retry -> repeat(errorHandling.times) {
                        if (result.isSuccess) return@repeat
                        result = executor.execute(action, variables, accessibilityController)
                    }
                    is ErrorHandling.Fallback -> {
                        val fallbackResult = executeActions(errorHandling.fallbackActions, variables, ErrorHandling.StopOnError)
                        if (!fallbackResult.isSuccess) return result
                    }
                    ErrorHandling.StopOnError -> return if (completed > 0) {
                        ExecutionResult.PartialSuccess(completed, actions.size, result.errorMessage)
                    } else result
                }
            }
            if (result.isSuccess) completed++ else if (errorHandling != ErrorHandling.ContinueOnError) return result
            if (action.delayAfter > 0) delay(action.delayAfter)
        }
        return ExecutionResult.Success(mapOf("completedActions" to completed))
    }

    fun registerDynamicTrigger(trigger: Trigger, actionPlan: ActionPlan): String = UUID.randomUUID().toString()
    fun getActiveExecutions(): List<ExecutionContext> = activeExecutions.value
}
