package com.example.automation.di

import android.content.Context
import androidx.room.Room
import com.example.automation.core.executor.AccessibilityController
import com.example.automation.core.executor.ActionExecutorRegistry
import com.example.automation.core.executor.AutomationEngine
import com.example.automation.core.executor.ExecutionLogger
import com.example.automation.data.local.AppDatabase
import com.example.automation.data.repository.ExecutionLoggerImpl
import com.example.automation.data.repository.RuleRepository
import com.example.automation.data.repository.RuleRepositoryImpl
import com.example.automation.service.AccessibilityServiceImpl
import com.example.automation.service.MediaProjectionManagerWrapper
import com.example.automation.service.MediaProjectionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "automation.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideRuleRepository(database: AppDatabase): RuleRepository =
        RuleRepositoryImpl(database)

    @Provides
    @Singleton
    fun provideExecutionLogger(database: AppDatabase): ExecutionLogger =
        ExecutionLoggerImpl(database)

    @Provides
    @Singleton
    fun provideActionExecutorRegistry(
        @ApplicationContext context: Context,
        mediaProjectionManager: MediaProjectionManagerWrapper
    ): ActionExecutorRegistry = ActionExecutorRegistry(context, mediaProjectionManager)

    @Provides
    @Singleton
    fun provideAccessibilityController(): AccessibilityController =
        checkNotNull(AccessibilityServiceImpl.getInstance()) {
            "Accessibility service is not enabled"
        }

    @Provides
    @Singleton
    fun provideAutomationEngine(
        accessibilityController: AccessibilityController,
        executorRegistry: ActionExecutorRegistry,
        ruleRepository: RuleRepository,
        executionLogger: ExecutionLogger
    ): AutomationEngine = AutomationEngine(
        accessibilityController,
        executorRegistry,
        ruleRepository,
        executionLogger
    )

    @Provides
    @Singleton
    fun provideMediaProjectionManagerWrapper(): MediaProjectionManagerWrapper =
        MediaProjectionManagerWrapper()
}
