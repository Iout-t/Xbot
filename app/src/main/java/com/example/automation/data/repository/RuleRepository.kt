package com.example.automation.data.repository

import com.example.automation.core.executor.ExecutionLogger
import com.example.automation.core.executor.RuleRepository as CoreRuleRepository
import com.example.automation.core.model.AutomationRule
import com.example.automation.core.model.ExecutionContext
import com.example.automation.core.model.ExecutionLogEntry
import com.example.automation.core.model.ExecutionResult
import com.example.automation.data.local.AppDatabase
import com.example.automation.data.local.ExecutionLogEntity
import com.example.automation.data.local.RuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.time.Instant

interface RuleRepository : CoreRuleRepository {
    suspend fun getRecentLogs(limit: Int): List<ExecutionLogEntity>
}

class RuleRepositoryImpl(
    private val database: AppDatabase,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) : RuleRepository {
    private val rules get() = database.ruleDao()
    private val logs get() = database.executionLogDao()

    override suspend fun getAllRules(): List<AutomationRule> = withContext(Dispatchers.IO) {
        rules.getAllRules().mapNotNull { it.toDomain(json) }
    }

    override suspend fun getEnabledRules(): List<AutomationRule> = withContext(Dispatchers.IO) {
        rules.getEnabledRules().mapNotNull { it.toDomain(json) }
    }

    override suspend fun getRule(id: String): AutomationRule? = withContext(Dispatchers.IO) {
        rules.getRule(id)?.toDomain(json)
    }

    override suspend fun saveRule(rule: AutomationRule) = withContext(Dispatchers.IO) {
        rules.insert(rule.toEntity(json))
    }

    override suspend fun updateRule(rule: AutomationRule) = withContext(Dispatchers.IO) {
        rules.update(rule.toEntity(json))
    }

    override suspend fun deleteRule(id: String) = withContext(Dispatchers.IO) {
        rules.deleteById(id)
    }

    override suspend fun setRuleEnabled(id: String, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            rules.getRule(id)?.let { rules.update(it.copy(enabled = enabled, updatedAt = Instant.now())) }
        }
    }

    override suspend fun recordExecution(rule: AutomationRule, result: ExecutionResult, context: ExecutionContext) =
        withContext(Dispatchers.IO) {
            rules.update(rule.copyWithExecutionStats(result.isSuccess).toEntity(json))
            insertLog(rule, result, context)
        }

    override suspend fun getRecentLogs(limit: Int): List<ExecutionLogEntity> = withContext(Dispatchers.IO) {
        logs.getRecentLogs(limit)
    }

    private suspend fun insertLog(rule: AutomationRule, result: ExecutionResult, context: ExecutionContext) {
        logs.insert(
            ExecutionLogEntity(
                ruleId = rule.id,
                ruleName = rule.name,
                triggerContextJson = context.triggerContext.toString(),
                resultJson = result.toString(),
                variablesJson = context.variables.toString(),
                startTime = context.startTime,
                endTime = context.endTime,
                durationMs = context.duration,
                success = result.isSuccess
            )
        )
    }
}

class ExecutionLoggerImpl(private val database: AppDatabase) : ExecutionLogger {
    override suspend fun log(context: ExecutionContext, result: ExecutionResult) {
        database.executionLogDao().insert(
            ExecutionLogEntity(
                ruleId = context.ruleId,
                ruleName = context.ruleName,
                triggerContextJson = context.triggerContext.toString(),
                resultJson = result.toString(),
                variablesJson = context.variables.toString(),
                startTime = context.startTime,
                endTime = context.endTime,
                durationMs = context.duration,
                success = result.isSuccess
            )
        )
    }

    override suspend fun log(entry: ExecutionLogEntry) = Unit
}

private fun RuleEntity.toDomain(json: Json): AutomationRule? = runCatching {
    AutomationRule(
        id = id,
        name = name,
        description = description.orEmpty(),
        enabled = enabled,
        trigger = json.decodeFromString(triggerJson),
        actionPlan = json.decodeFromString(actionPlanJson),
        preconditions = json.decodeFromString(preconditionsJson),
        errorHandling = json.decodeFromString(errorHandlingJson),
        createdAt = createdAt,
        updatedAt = updatedAt,
        executionCount = executionCount,
        lastExecutionAt = lastExecutionAt,
        successCount = successCount,
        failureCount = failureCount
    )
}.getOrNull()

private fun AutomationRule.toEntity(json: Json) = RuleEntity(
    id = id,
    name = name,
    description = description,
    enabled = enabled,
    triggerJson = json.encodeToString(trigger),
    actionPlanJson = json.encodeToString(actionPlan),
    preconditionsJson = json.encodeToString(preconditions),
    errorHandlingJson = json.encodeToString(errorHandling),
    createdAt = createdAt,
    updatedAt = updatedAt,
    executionCount = executionCount,
    lastExecutionAt = lastExecutionAt,
    successCount = successCount,
    failureCount = failureCount
)
