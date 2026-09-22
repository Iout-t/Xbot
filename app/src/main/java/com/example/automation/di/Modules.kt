package com.example.automation.di

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.automation.core.executor.*
import com.example.automation.core.model.*
import com.example.automation.data.local.*
import com.example.automation.data.repository.*
import com.example.automation.service.*
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.Singleton
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context) =
        context.preferencesDataStore("automation_prefs")

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context) =
        Room.databaseBuilder(context, AppDatabase::class.java, "automation.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideRuleRepository(db: AppDatabase, dataStore: androidx.datastore.preferences.DataStore<Preferences>): RuleRepository {
        return RuleRepositoryImpl(db.ruleDao(), db.executionLogDao(), dataStore)
    }

    @Provides
    @Singleton
    fun provideExecutionLogger(db: AppDatabase): ExecutionLogger {
        return ExecutionLoggerImpl(db.executionLogDao())
    }

    @Provides
    @Singleton
    fun provideActionExecutorRegistry(): ActionExecutorRegistry {
        return ActionExecutorRegistry()
    }

    @Provides
    @Singleton
    fun provideAutomationEngine(
        accessibilityController: AccessibilityController,
        executorRegistry: ActionExecutorRegistry,
        ruleRepository: RuleRepository,
        executionLogger: ExecutionLogger
    ): AutomationEngine {
        return AutomationEngine(
            accessibilityController,
            executorRegistry,
            ruleRepository,
            executionLogger
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface ServiceModule {
    @Binds
    fun bindAccessibilityController(service: AccessibilityServiceImpl): AccessibilityController
}

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {
    @Provides
    @Singleton
    fun provideMediaProjectionManagerWrapper(service: MediaProjectionService): MediaProjectionManagerWrapper {
        return MediaProjectionManagerWrapper(service)
    }
}
