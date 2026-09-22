package com.example.automation.core.executor.comm

import com.example.automation.core.executor.*
import com.example.automation.core.model.*
import android.telephony.SmsManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SendSmsExecutor(private val context: Context) : ActionExecutor {
    override val supportedType = ActionType.SEND_SMS
    override val executionDispatcher = Dispatchers.IO

    override suspend fun execute(
        action: Action,
        variables: MutableMap<String, Any>,
        accessibility: AccessibilityController
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val phoneNumber = action.getString("phoneNumber")
            ?: variables["contact_phone"] as String?
            ?: return ExecutionResult.Failure("Missing phone number")

        val message = action.getString("message")
            ?: return ExecutionResult.Failure("Missing message text")

        val simulateOnly = action.getBoolean("simulateOnly") ?: false

        if (simulateOnly) {
            return ExecutionResult.Success(mapOf("simulated" to true, "to" to phoneNumber, "message" to message))
        }

        return try {
            val smsManager = SmsManager.getDefault()
            
            // Handle long messages (multipart)
            val parts = smsManager.divideMessage(message)
            
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
            
            ExecutionResult.Success(mapOf("sent" to true, "parts" to parts.size, "to" to phoneNumber))
        } catch (e: Exception) {
            ExecutionResult.Failure("SMS send failed: ${e.message}")
        }
    }

    override fun validateParameters(parameters: Map<String, Any>): ValidationResult {
        val errors = mutableListOf<String>()
        if (!parameters.containsKey("phoneNumber") && !parameters.containsKey("contact")) {
            errors.add("Missing phoneNumber or contact parameter")
        }
        if (!parameters.containsKey("message")) {
            errors.add("Missing message parameter")
        }
        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}
