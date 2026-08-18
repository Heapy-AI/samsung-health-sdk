package com.example.shealthpoc

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Gson configured for the DTOs in HealthRecords.kt.
 *
 * Only our own data classes are serialized; SDK objects are converted to DTOs first, so no
 * reflection ever touches Samsung Health Data SDK classes.
 */
object JsonSupport {

    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .registerTypeAdapter(Instant::class.java, JsonSerializer<Instant> { src, _, _ ->
            JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format(src))
        })
        .registerTypeAdapter(LocalDateTime::class.java, JsonSerializer<LocalDateTime> { src, _, _ ->
            JsonPrimitive(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(src))
        })
        .registerTypeAdapter(ZoneOffset::class.java, JsonSerializer<ZoneOffset> { src, _, _ ->
            JsonPrimitive(src.id)
        })
        .registerTypeAdapter(Duration::class.java, JsonSerializer<Duration> { src, _, _ ->
            JsonPrimitive(src.toString()) // ISO-8601, e.g. "PT7H21M"
        })
        .create()
}
