package com.example.automation.di

import android.content.Context
import com.example.automation.core.executor.ActionExecutorRegistry
import com.example.automation.core.executor.AutomationEngine
import com.example.automation.core.executor.ExecutionLogger
import com.example.automation.data.repository.RuleRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HiltEntryPoints {
    fun automationEngine(): AutomationEngine
    fun actionExecutorRegistry(): ActionExecutorRegistry
    fun ruleRepository(): RuleRepository
    fun executionLogger(): ExecutionLogger
}

fun Context.getHiltEntryPoints(): HiltEntryPoints =
    EntryPointAccessors.fromApplication(applicationContext, HiltEntryPoints::class.java)
