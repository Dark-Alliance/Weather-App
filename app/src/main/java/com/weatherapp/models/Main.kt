package com.weatherapp.models

import java.io.Serializable

data class Main(
    val temperature: Double,
    val pressure: Double,
    val humidity: Int,
    val temp_min: Double,
    val temp_max: Double
): Serializable
