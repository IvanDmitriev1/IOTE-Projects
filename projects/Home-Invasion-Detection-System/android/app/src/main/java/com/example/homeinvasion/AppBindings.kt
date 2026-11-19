package com.example.homeinvasion

import com.example.homeinvasion.data.AuthRepository
import com.example.homeinvasion.data.DevicePreferences
import com.example.homeinvasion.data.DeviceRegistrationRepository
import com.example.homeinvasion.data.DemoSessionRepository
import com.example.homeinvasion.data.FirebaseMotionRepository
import com.example.homeinvasion.data.FirebasePushCoordinator
import com.example.homeinvasion.data.MotionRepository
import com.example.homeinvasion.data.PushCoordinator
import com.example.homeinvasion.data.RealtimeDeviceRegistrationRepository
import com.example.homeinvasion.data.SharedPreferencesDevicePreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {
@Binds @Singleton abstract fun bindAuthRepository(implementation: DemoSessionRepository): AuthRepository
    @Binds @Singleton abstract fun bindMotionRepository(implementation: FirebaseMotionRepository): MotionRepository
    @Binds @Singleton abstract fun bindDevicePreferences(implementation: SharedPreferencesDevicePreferences): DevicePreferences
    @Binds @Singleton abstract fun bindDeviceRegistration(implementation: RealtimeDeviceRegistrationRepository): DeviceRegistrationRepository
    @Binds @Singleton abstract fun bindPushCoordinator(implementation: FirebasePushCoordinator): PushCoordinator
}
