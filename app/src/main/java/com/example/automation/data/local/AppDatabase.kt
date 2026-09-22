package com.example.automation.data.local

import androidx.room.*
import com.example.automation.core.model.*
import kotlinx.serialization.json.Json
import java.time.Instant

@Database(entities = [RuleEntity::class, ExecutionLogEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun executionLogDao(): ExecutionLogDao
}

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val enabled: Boolean,
    val triggerJson: String,      // Serialized Trigger
    val actionPlanJson: String,   // Serialized ActionPlan
    val preconditionsJson: String,// Serialized List<Precondition>
    val errorHandlingJson: String,// Serialized ErrorHandling
    val createdAt: Instant,
    val updatedAt: Instant,
    val executionCount: Int = 0,
    val lastExecutionAt: Instant?,
    val successCount: Int = 0,
    val failureCount: Int = 0
)

@Entity(tableName = "execution_logs", indices = [Index("ruleId"), Index("startTime")])
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: String,
    val ruleName: String,
    val triggerContextJson: String,
    val resultJson: String,
    val variablesJson: String,
    val startTime: Instant,
    val endTime: Instant?,
    val durationMs: Long,
    val success: Boolean
)

@Dao
interface RuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RuleEntity)

    @Update
    suspend fun update(rule: RuleEntity)

    @Delete
    suspend fun delete(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY updatedAt DESC")
    suspend fun getEnabledRules(): List<RuleEntity>

    @Query("SELECT * FROM rules ORDER BY updatedAt DESC")
    suspend fun getAllRules(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getRule(id: String): RuleEntity?

    @Query("UPDATE rules SET executionCount = executionCount + 1, lastExecutionAt = :now, successCount = successCount + :success, failureCount = failureCount + :failure WHERE id = :id")
    suspend fun updateExecutionStats(id: String, now: Instant, success: Int, failure: Int)
}

@Dao
interface ExecutionLogDao {
    @Insert
    suspend fun insert(log: ExecutionLogEntity)

    @Query("SELECT * FROM execution_logs WHERE ruleId = :ruleId ORDER BY startTime DESC LIMIT :limit")
    suspend fun getLogsForRule(ruleId: String, limit: Int): List<ExecutionLogEntity>

    @Query("SELECT * FROM execution_logs ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int): List<ExecutionLogEntity>

    @Query("DELETE FROM execution_logs WHERE startTime < :cutoff")
    suspend fun deleteOldLogs(cutoff: Instant)
}

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromInstant(value: Instant?): String? = value?.toString()

    @TypeConverter
    fun toInstant(value: String?): Instant? = value?.let { Instant.parse(it) }

    @TypeConverter
    fun fromTrigger(trigger: Trigger): String = json.encodeToString(trigger)
    @TypeConverter
    fun toTrigger(value: String): Trigger = json.decodeFromString(value)

    @TypeConverter
    fun fromActionPlan(plan: ActionPlan): String = json.encodeToString(plan)
    @TypeConverter
    fun toActionPlan(value: String): ActionPlan = json.decodeFromString(value)

    @TypeConverter
    fun fromPreconditions(preconditions: List<Precondition>): String = json.encodeToString(preconditions)
    @TypeConverter
    fun toPreconditions(value: String): List<Precondition> = json.decodeFromString(value)

    @TypeConverter
    fun fromErrorHandling(handling: ErrorHandling): String = json.encodeToString(handling)
    @TypeConverter
    fun toErrorHandling(value: String): ErrorHandling = json.decodeFromString(value)

    @TypeConverter
    fun fromTriggerContext(context: TriggerContext): String = json.encodeToString(context)
    @TypeConverter
    fun toTriggerContext(value: String): TriggerContext = json.decodeFromString(value)

    @TypeConverter
    fun fromExecutionResult(result: ExecutionResult): String = json.encodeToString(result)
    @TypeConverter
    fun toExecutionResult(value: String): ExecutionResult = json.decodeFromString(value)

    @TypeConverter
    fun fromVariables(variables: Map<String, Any>): String = json.encodeToString(variables)
    @TypeConverter
    fun toVariables(value: String): Map<String, Any> = json.decodeFromString(value)
}
