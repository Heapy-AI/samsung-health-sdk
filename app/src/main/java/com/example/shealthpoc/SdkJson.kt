package com.example.shealthpoc

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.samsung.android.sdk.health.data.data.entries.BloodGlucose
import com.samsung.android.sdk.health.data.data.entries.ExerciseLocation
import com.samsung.android.sdk.health.data.data.entries.ExerciseLog
import com.samsung.android.sdk.health.data.data.entries.ExerciseSession
import com.samsung.android.sdk.health.data.data.entries.HeartRate
import com.samsung.android.sdk.health.data.data.entries.OxygenSaturation
import com.samsung.android.sdk.health.data.data.entries.SkinTemperature
import com.samsung.android.sdk.health.data.data.entries.SleepSession
import com.samsung.android.sdk.health.data.data.entries.SwimmingLog
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Converts Samsung Health Data SDK values to JSON **explicitly** - every branch below maps to
 * public getters verified in samsung-health-data-api-1.1.0.aar. No reflection is used on SDK
 * objects, so obfuscation or an SDK update can never silently change the output shape; an
 * unmapped type is logged loudly instead.
 *
 * Covers every entry class the SDK exposes:
 * SleepSession(+SleepStage), HeartRate, OxygenSaturation, SkinTemperature, BloodGlucose,
 * ExerciseSession, ExerciseLog, ExerciseLocation, SwimmingLog(+SwimmingInterval).
 */
object SdkJson {

    private const val TAG = HealthDataExporter.TAG

    fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull.INSTANCE

        // --- primitives -----------------------------------------------------
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Enum<*> -> JsonPrimitive(value.name)

        // --- java.time ------------------------------------------------------
        is Instant -> JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format(value))
        is LocalDateTime -> JsonPrimitive(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value))
        is LocalDate -> JsonPrimitive(DateTimeFormatter.ISO_LOCAL_DATE.format(value))
        is LocalTime -> JsonPrimitive(DateTimeFormatter.ISO_LOCAL_TIME.format(value))
        is Duration -> JsonPrimitive(value.toString()) // ISO-8601, e.g. "PT7H21M"
        is ZoneOffset -> JsonPrimitive(value.id)

        is List<*> -> JsonArray().apply { value.forEach { add(toJson(it)) } }

        // --- SDK entry objects ----------------------------------------------
        is SleepSession -> obj {
            put("startTime", value.startTime)
            put("endTime", value.endTime)
            put("duration", value.duration)
            put("stages", value.stages)
        }

        is SleepSession.SleepStage -> obj {
            put("startTime", value.startTime)
            put("endTime", value.endTime)
            put("stage", value.stage)
        }

        is HeartRate -> obj {
            put("heartRate", value.heartRate)
            put("min", value.min)
            put("max", value.max)
            put("startTime", value.startTime)
            put("endTime", value.endTime)
        }

        is OxygenSaturation -> obj {
            put("oxygenSaturation", value.oxygenSaturation)
            put("min", value.min)
            put("max", value.max)
            put("startTime", value.startTime)
            put("endTime", value.endTime)
        }

        is SkinTemperature -> obj {
            put("skinTemperature", value.skinTemperature)
            put("min", value.min)
            put("max", value.max)
            put("startTime", value.startTime)
            put("endTime", value.endTime)
        }

        is BloodGlucose -> obj {
            put("glucose", value.glucose)
            put("timestamp", value.timestamp)
        }

        is ExerciseSession -> obj {
            put("exerciseType", value.exerciseType)
            put("customTitle", value.customTitle)
            put("startTime", value.startTime)
            put("endTime", value.endTime)
            put("duration", value.duration)
            put("distance", value.distance)
            put("calories", value.calories)
            put("altitudeGain", value.altitudeGain)
            put("altitudeLoss", value.altitudeLoss)
            put("count", value.count)
            put("countType", value.countType)
            put("maxSpeed", value.maxSpeed)
            put("meanSpeed", value.meanSpeed)
            put("maxCalorieBurnRate", value.maxCalorieBurnRate)
            put("meanCalorieBurnRate", value.meanCalorieBurnRate)
            put("maxCadence", value.maxCadence)
            put("meanCadence", value.meanCadence)
            put("minHeartRate", value.minHeartRate)
            put("maxHeartRate", value.maxHeartRate)
            put("meanHeartRate", value.meanHeartRate)
            put("minAltitude", value.minAltitude)
            put("maxAltitude", value.maxAltitude)
            put("inclineDistance", value.inclineDistance)
            put("declineDistance", value.declineDistance)
            put("maxPower", value.maxPower)
            put("meanPower", value.meanPower)
            put("maxRpm", value.maxRpm)
            put("meanRpm", value.meanRpm)
            put("vo2Max", value.vo2Max)
            put("autoDetected", value.autoDetected)
            put("comment", value.comment)
            put("swimmingLog", value.swimmingLog)
            put("route", value.route)
            put("log", value.log)
        }

        is ExerciseLog -> obj {
            put("timestamp", value.timestamp)
            put("heartRate", value.heartRate)
            put("cadence", value.cadence)
            put("count", value.count)
            put("power", value.power)
            put("speed", value.speed)
        }

        is ExerciseLocation -> obj {
            put("timestamp", value.timestamp)
            put("latitude", value.latitude)
            put("longitude", value.longitude)
            put("altitude", value.altitude)
            put("accuracy", value.accuracy)
        }

        is SwimmingLog -> obj {
            put("poolLength", value.poolLength)
            put("poolLengthUnit", value.poolLengthUnit)
            put("totalDistance", value.totalDistance)
            put("totalDuration", value.totalDuration)
            put("swimmingIntervals", value.swimmingIntervals)
        }

        is SwimmingLog.SwimmingInterval -> obj {
            put("interval", value.interval)
            put("duration", value.duration)
            put("strokeCount", value.strokeCount)
            put("strokeType", value.strokeType)
        }

        else -> {
            Log.w(TAG, "unmapped SDK value type: ${value.javaClass.name} - falling back to toString()")
            JsonPrimitive(value.toString())
        }
    }

    private inline fun obj(build: JsonObject.() -> Unit): JsonObject = JsonObject().apply(build)

    private fun JsonObject.put(name: String, value: Any?) = add(name, toJson(value))
}
