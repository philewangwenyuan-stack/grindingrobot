package com.sinelynx.grindingrobot.navigation.di

import com.sinelynx.grindingrobot.navigation.AppNavigator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NavigatorEntryPoint {
    fun getAppNavigator(): AppNavigator
}
