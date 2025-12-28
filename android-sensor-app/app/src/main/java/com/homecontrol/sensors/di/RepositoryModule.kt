package com.homecontrol.sensors.di

import com.homecontrol.sensors.data.repository.*
import com.homecontrol.sensors.service.SensorServiceBridge
import com.homecontrol.sensors.service.SensorServiceBridgeImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindEntityRepository(
        impl: EntityRepositoryImpl
    ): EntityRepository

    @Binds
    @Singleton
    abstract fun bindHueRepository(
        impl: HueRepositoryImpl
    ): HueRepository

    @Binds
    @Singleton
    abstract fun bindSpotifyRepository(
        impl: SpotifyRepositoryImpl
    ): SpotifyRepository

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(
        impl: CalendarRepositoryImpl
    ): CalendarRepository

    @Binds
    @Singleton
    abstract fun bindCameraRepository(
        impl: CameraRepositoryImpl
    ): CameraRepository

    @Binds
    @Singleton
    abstract fun bindEntertainmentRepository(
        impl: EntertainmentRepositoryImpl
    ): EntertainmentRepository

    @Binds
    @Singleton
    abstract fun bindWeatherRepository(
        impl: WeatherRepositoryImpl
    ): WeatherRepository

    @Binds
    @Singleton
    abstract fun bindSensorServiceBridge(
        impl: SensorServiceBridgeImpl
    ): SensorServiceBridge
}
