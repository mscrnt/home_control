package com.homecontrol.sensors.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Weather(
    val current: CurrentWeather,
    val hourly: List<HourlyWeather> = emptyList(),
    val daily: List<DailyWeather> = emptyList(),
    val alerts: List<WeatherAlert> = emptyList()
)

@Serializable
data class CurrentWeather(
    val temp: Double,
    @SerialName("feels_like")
    val feelsLike: Double,
    val humidity: Int,
    val pressure: Int,
    @SerialName("wind_speed")
    val windSpeed: Double,
    @SerialName("wind_deg")
    val windDeg: Int,
    val clouds: Int,
    val visibility: Int,
    val uvi: Double,
    val weather: List<WeatherCondition>,
    val sunrise: Long,
    val sunset: Long,
    val dt: Long
)

@Serializable
data class HourlyWeather(
    val dt: Long,
    val temp: Double,
    @SerialName("feels_like")
    val feelsLike: Double,
    val humidity: Int,
    val weather: List<WeatherCondition>,
    val pop: Double = 0.0  // Probability of precipitation
)

@Serializable
data class DailyWeather(
    val dt: Long,
    val temp: DailyTemp,
    @SerialName("feels_like")
    val feelsLike: DailyFeelsLike,
    val humidity: Int,
    val weather: List<WeatherCondition>,
    val pop: Double = 0.0,
    val sunrise: Long,
    val sunset: Long,
    @SerialName("moon_phase")
    val moonPhase: Double = 0.0
)

@Serializable
data class DailyTemp(
    val day: Double,
    val min: Double,
    val max: Double,
    val night: Double,
    val eve: Double,
    val morn: Double
)

@Serializable
data class DailyFeelsLike(
    val day: Double,
    val night: Double,
    val eve: Double,
    val morn: Double
)

@Serializable
data class WeatherCondition(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

@Serializable
data class WeatherAlert(
    val event: String,
    val sender: String? = null,
    val start: Long,
    val end: Long,
    val description: String
)
