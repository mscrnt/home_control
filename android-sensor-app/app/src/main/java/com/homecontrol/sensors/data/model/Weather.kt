package com.homecontrol.sensors.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Weather(
    val current: CurrentWeather,
    val hourly: List<HourlyWeather> = emptyList(),
    val daily: List<DailyWeather> = emptyList(),
    val timezone: String = "",
    val fetchedAt: String = ""
)

@Serializable
data class CurrentWeather(
    val temp: Double,
    val feelsLike: Double = 0.0,
    val humidity: Int = 0,
    val windSpeed: Double = 0.0,
    val windDeg: Int = 0,
    val clouds: Int = 0,
    val uvi: Double = 0.0,
    val condition: String = "",
    val icon: String = "",
    val sunrise: Long = 0,
    val sunset: Long = 0
)

@Serializable
data class HourlyWeather(
    val time: Long,
    val temp: Double,
    val feelsLike: Double = 0.0,
    val humidity: Int = 0,
    val condition: String = "",
    val icon: String = "",
    val pop: Double = 0.0
)

@Serializable
data class DailyWeather(
    val time: Long,
    val tempMin: Double,
    val tempMax: Double,
    val humidity: Int = 0,
    val condition: String = "",
    val icon: String = "",
    val pop: Double = 0.0,
    val sunrise: Long = 0,
    val sunset: Long = 0,
    val summary: String = ""
)
