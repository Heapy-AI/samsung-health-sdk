package com.example.shealthpoc

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.data.AggregateOperation
import com.samsung.android.sdk.health.data.data.AggregatedData
import com.samsung.android.sdk.health.data.data.Field
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.UserDataPoint
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.AggregateRequest
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalDateFilter
import com.samsung.android.sdk.health.data.request.LocalDateGroup
import com.samsung.android.sdk.health.data.request.LocalDateGroupUnit
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.LocalTimeGroup
import com.samsung.android.sdk.health.data.request.LocalTimeGroupUnit
import com.samsung.android.sdk.health.data.request.Ordering
import com.samsung.android.sdk.health.data.request.ReadDataRequest
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Reads the selected Samsung Health data types and writes one JSON file per type.
 *
 * Read paths, all verified against samsung-health-data-api-1.1.0.aar:
 *  - [HealthDataChoice.ReadMode.READ] - `DataTypes.X.readDataRequestBuilder`
 *    (`ReadDataRequest.DualTimeBuilder<HealthDataPoint>`) + [HealthDataStore.readData]
 *  - [HealthDataChoice.ReadMode.READ_USER_PROFILE] - `ReadDataRequest.UserProfileBuilder`
 *    -> `DataResponse<UserDataPoint>` (no time filter)
 *  - [HealthDataChoice.ReadMode.AGGREGATE_LOCAL_TIME] - `AggregateRequest.LocalTimeBuilder`
 *  - [HealthDataChoice.ReadMode.AGGREGATE_LOCAL_DATE] - `AggregateRequest.AllSourceLocalDateBuilder`
 *
 * Types with no read builder (STEPS, ACTIVITY_SUMMARY and the goal types) genuinely have no
 * raw records in the SDK - only aggregate operations.
 *
 * Every response is paged on `DataResponse.pageToken` so long ranges are not truncated.
 */
class HealthDataExporter(
    private val context: Context,
    private val store: HealthDataStore,
) {

    data class ExportResult(
        val dataType: String,
        val count: Int,
        val file: File?,
        /** User-visible shared-storage path, e.g. "Download/SHealthPoC/steps.json". */
        val publicPath: String?,
        val skippedReason: String?,
        val error: String?,
    )

    private val downloads = DownloadsExporter(context)

    /** fileName -> public path, filled in by [writeBoth]. */
    private val publishedPaths = linkedMapOf<String, String>()

    /** Every file successfully published to Download/SHealthPoC/, in write order. */
    fun publicPaths(): List<String> = publishedPaths.values.toList()

    /**
     * Primary output: /sdcard/Android/data/&lt;applicationId&gt;/files/data on a real device.
     * Reachable from a PC with `adb pull`.
     */
    fun outputDir(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "data").apply { mkdirs() }
    }

    /**
     * Mirror output: /data/data/&lt;applicationId&gt;/files/data, reachable with
     * `adb exec-out run-as <applicationId> cat files/data/<file>`.
     */
    fun mirrorDir(): File = File(context.filesDir, "data").apply { mkdirs() }

    // --------------------------------------------------------------- 실행

    suspend fun exportAll(
        days: Long,
        selected: Set<HealthDataChoice>,
        grantedPermissions: Set<Permission>,
    ): List<ExportResult> {
        val end = LocalDate.now().plusDays(1).atStartOfDay()
        val start = LocalDate.now().minusDays(days - 1).atStartOfDay()
        val range = RequestedRange(start, end)

        Log.i(TAG, "Export range: $start .. $end, types=${selected.joinToString { it.fileBaseName }}")

        // Stable order regardless of how the selection set was built.
        val results = HealthDataChoice.entries.filter { it in selected }.map { choice ->
            exportOne(choice, start, end, range, grantedPermissions)
        }
        writeSummary(results, range, days, selected)
        return results
    }

    private suspend fun exportOne(
        choice: HealthDataChoice,
        start: LocalDateTime,
        end: LocalDateTime,
        range: RequestedRange,
        granted: Set<Permission>,
    ): ExportResult {
        val name = choice.fileBaseName
        if (choice.permission !in granted) {
            Log.w(TAG, "[$name] skipped - permission not granted")
            return ExportResult(name, 0, null, null, "permission not granted", null)
        }
        return try {
            val records = when (choice.mode) {
                HealthDataChoice.ReadMode.READ -> readRecords(choice, start, end)
                HealthDataChoice.ReadMode.READ_USER_PROFILE -> userProfileRecords(choice)
                HealthDataChoice.ReadMode.AGGREGATE_LOCAL_TIME,
                HealthDataChoice.ReadMode.AGGREGATE_LOCAL_DATE,
                -> aggregateRecords(choice, start, end)
            }
            val file = writeEnvelope(choice, range, records)
            Log.i(TAG, "[$name] ${records.size} record(s) -> ${file.absolutePath}")
            ExportResult(name, records.size, file, publishedPaths[file.name], null, null)
        } catch (t: Throwable) {
            Log.e(TAG, "[$name] export failed", t)
            ExportResult(name, 0, null, null, null, "${t.javaClass.simpleName}: ${t.message}")
        }
    }

    // ------------------------------------------------------- readData() 경로

    /**
     * Safe by construction: [HealthDataChoice.ReadMode.READ] is only assigned to types whose
     * bytecode declares `DataType.Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<..>>`.
     */
    @Suppress("UNCHECKED_CAST")
    private fun readableOf(choice: HealthDataChoice) =
        choice.dataType as DataType.Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>

    private suspend fun readRecords(
        choice: HealthDataChoice,
        start: LocalDateTime,
        end: LocalDateTime,
    ): List<JsonObject> {
        val readable = readableOf(choice)
        val localTimeFilter = LocalTimeFilter.of(start, end)

        val points = readPages { pageToken ->
            val builder = readable.readDataRequestBuilder
                .setLocalTimeFilter(localTimeFilter)
                .setOrdering(Ordering.ASC)
            if (pageToken != null) builder.setPageToken(pageToken)
            builder.build()
        }

        val fields = fieldsOf(choice)
        return points.map { point ->
            JsonObject().apply {
                // HealthDataPoint properties every record carries
                add("uid", SdkJson.toJson(point.uid))
                add("startTime", SdkJson.toJson(point.startTime))
                add("endTime", SdkJson.toJson(point.endTime))
                add("startLocalDateTime", SdkJson.toJson(point.getStartLocalDateTime()))
                add("endLocalDateTime", SdkJson.toJson(point.getEndLocalDateTime()))
                add("zoneOffset", SdkJson.toJson(point.zoneOffset))
                add("updateTime", SdkJson.toJson(point.updateTime))
                add("clientDataId", SdkJson.toJson(point.clientDataId))
                add("clientVersion", SdkJson.toJson(point.clientVersion))
                add("dataSource", dataSourceJson(point))
                // type-specific Field values
                fields.forEach { (key, field) -> add(key, SdkJson.toJson(point.value(field))) }
            }
        }
    }

    private suspend fun userProfileRecords(choice: HealthDataChoice): List<JsonObject> {
        val request: ReadDataRequest<UserDataPoint> = DataTypes.USER_PROFILE.readDataRequestBuilder.build()
        val points: List<UserDataPoint> = store.readData(request).dataList
        val fields = fieldsOf(choice)
        return points.map { point ->
            JsonObject().apply {
                fields.forEach { (key, field) -> add(key, SdkJson.toJson(point.value(field))) }
            }
        }
    }

    // -------------------------------------------------- aggregateData() 경로

    private suspend fun aggregateRecords(
        choice: HealthDataChoice,
        start: LocalDateTime,
        end: LocalDateTime,
    ): List<JsonObject> {
        val timeFilter = LocalTimeFilter.of(start, end)
        val timeGroup = LocalTimeGroup.of(LocalTimeGroupUnit.DAILY, 1)
        val dateFilter = LocalDateFilter.of(start.toLocalDate(), end.toLocalDate())
        val dateGroup = LocalDateGroup.of(LocalDateGroupUnit.DAILY, 1)

        return when (choice) {
            HealthDataChoice.STEPS ->
                byTime("TOTAL", DataType.StepsType.TOTAL, timeFilter, timeGroup)

            HealthDataChoice.ACTIVITY_SUMMARY ->
                byTime(
                    "TOTAL_ACTIVE_TIME",
                    DataType.ActivitySummaryType.TOTAL_ACTIVE_TIME, timeFilter, timeGroup,
                ) + byTime(
                    "TOTAL_ACTIVE_CALORIES_BURNED",
                    DataType.ActivitySummaryType.TOTAL_ACTIVE_CALORIES_BURNED, timeFilter, timeGroup,
                ) + byTime(
                    "TOTAL_CALORIES_BURNED",
                    DataType.ActivitySummaryType.TOTAL_CALORIES_BURNED, timeFilter, timeGroup,
                ) + byTime(
                    "TOTAL_DISTANCE",
                    DataType.ActivitySummaryType.TOTAL_DISTANCE, timeFilter, timeGroup,
                )

            HealthDataChoice.STEPS_GOAL ->
                byDate("LAST", DataType.StepsGoalType.LAST, dateFilter, dateGroup)

            HealthDataChoice.SLEEP_GOAL ->
                byDate("LAST_BED_TIME", DataType.SleepGoalType.LAST_BED_TIME, dateFilter, dateGroup) +
                    byDate("LAST_WAKE_UP_TIME", DataType.SleepGoalType.LAST_WAKE_UP_TIME, dateFilter, dateGroup)

            HealthDataChoice.ACTIVE_TIME_GOAL ->
                byDate("LAST", DataType.ActiveTimeGoalType.LAST, dateFilter, dateGroup)

            HealthDataChoice.ACTIVE_CALORIES_BURNED_GOAL ->
                byDate("LAST", DataType.ActiveCaloriesBurnedGoalType.LAST, dateFilter, dateGroup)

            HealthDataChoice.WATER_INTAKE_GOAL ->
                byDate("LAST", DataType.WaterIntakeGoalType.LAST, dateFilter, dateGroup)

            HealthDataChoice.NUTRITION_GOAL ->
                byDate("LAST_CALORIES", DataType.NutritionGoalType.LAST_CALORIES, dateFilter, dateGroup)

            else -> error("${choice.name} has no aggregate operation")
        }
    }

    /** `AggregateRequest.LocalTimeBuilder` operations. */
    private suspend fun <T : Any> byTime(
        operation: String,
        op: AggregateOperation<T, AggregateRequest.LocalTimeBuilder<T>>,
        filter: LocalTimeFilter,
        group: LocalTimeGroup,
    ): List<JsonObject> = aggregatePages { pageToken ->
        val builder = op.requestBuilder
            .setLocalTimeFilterWithGroup(filter, group)
            .setOrdering(Ordering.ASC)
        if (pageToken != null) builder.setPageToken(pageToken)
        builder.build()
    }.map { aggregateJson(operation, it) }

    /** `AggregateRequest.AllSourceLocalDateBuilder` operations (goal types). */
    private suspend fun <T : Any> byDate(
        operation: String,
        op: AggregateOperation<T, AggregateRequest.AllSourceLocalDateBuilder<T>>,
        filter: LocalDateFilter,
        group: LocalDateGroup,
    ): List<JsonObject> = aggregatePages { pageToken ->
        val builder = op.requestBuilder
            .setLocalDateFilterWithGroup(filter, group)
            .setOrdering(Ordering.ASC)
        if (pageToken != null) builder.setPageToken(pageToken)
        builder.build()
    }.map { aggregateJson(operation, it) }

    private fun aggregateJson(operation: String, data: AggregatedData<*>): JsonObject =
        JsonObject().apply {
            add("operation", SdkJson.toJson(operation))
            add("startTime", SdkJson.toJson(data.startTime))
            add("endTime", SdkJson.toJson(data.endTime))
            add("startLocalDateTime", SdkJson.toJson(data.getStartLocalDateTime()))
            add("endLocalDateTime", SdkJson.toJson(data.getEndLocalDateTime()))
            add("value", SdkJson.toJson(data.value))
        }

    // ------------------------------------------------------------- 페이징

    private suspend fun readPages(
        build: (pageToken: String?) -> ReadDataRequest<HealthDataPoint>,
    ): List<HealthDataPoint> {
        val out = mutableListOf<HealthDataPoint>()
        var pageToken: String? = null
        var page = 0
        do {
            val response = store.readData(build(pageToken))
            out += response.dataList
            pageToken = response.pageToken
            page++
        } while (!pageToken.isNullOrEmpty() && page < MAX_PAGES)
        warnIfTruncated(page, pageToken)
        return out
    }

    private suspend fun <T : Any> aggregatePages(
        build: (pageToken: String?) -> AggregateRequest<T>,
    ): List<AggregatedData<T>> {
        val out = mutableListOf<AggregatedData<T>>()
        var pageToken: String? = null
        var page = 0
        do {
            val response = store.aggregateData(build(pageToken))
            out += response.dataList
            pageToken = response.pageToken
            page++
        } while (!pageToken.isNullOrEmpty() && page < MAX_PAGES)
        warnIfTruncated(page, pageToken)
        return out
    }

    private fun warnIfTruncated(page: Int, pageToken: String?) {
        if (page >= MAX_PAGES && !pageToken.isNullOrEmpty()) {
            Log.w(TAG, "stopped paging at $MAX_PAGES pages - result may be incomplete")
        }
    }

    // ------------------------------------------------------------ 필드 목록

    /**
     * The `Field` constants each data type exposes, with the JSON key used for them.
     * Taken verbatim from the AAR - nothing here is invented.
     */
    private fun fieldsOf(choice: HealthDataChoice): List<Pair<String, Field<*>>> = when (choice) {
        HealthDataChoice.SLEEP -> listOf(
            "duration" to DataType.SleepType.DURATION,
            "sleepScore" to DataType.SleepType.SLEEP_SCORE,
            "sessions" to DataType.SleepType.SESSIONS,
        )

        HealthDataChoice.HEART_RATE -> listOf(
            "heartRate" to DataType.HeartRateType.HEART_RATE,
            "minHeartRate" to DataType.HeartRateType.MIN_HEART_RATE,
            "maxHeartRate" to DataType.HeartRateType.MAX_HEART_RATE,
            "seriesData" to DataType.HeartRateType.SERIES_DATA,
        )

        HealthDataChoice.EXERCISE -> listOf(
            "exerciseType" to DataType.ExerciseType.EXERCISE_TYPE,
            "customTitle" to DataType.ExerciseType.CUSTOM_TITLE,
            "sessions" to DataType.ExerciseType.SESSIONS,
        )

        HealthDataChoice.BLOOD_OXYGEN -> listOf(
            "oxygenSaturation" to DataType.BloodOxygenType.OXYGEN_SATURATION,
            "minOxygenSaturation" to DataType.BloodOxygenType.MIN_OXYGEN_SATURATION,
            "maxOxygenSaturation" to DataType.BloodOxygenType.MAX_OXYGEN_SATURATION,
            "seriesData" to DataType.BloodOxygenType.SERIES_DATA,
        )

        HealthDataChoice.SKIN_TEMPERATURE -> listOf(
            "skinTemperature" to DataType.SkinTemperatureType.SKIN_TEMPERATURE,
            "minSkinTemperature" to DataType.SkinTemperatureType.MIN_SKIN_TEMPERATURE,
            "maxSkinTemperature" to DataType.SkinTemperatureType.MAX_SKIN_TEMPERATURE,
            "seriesData" to DataType.SkinTemperatureType.SERIES_DATA,
        )

        HealthDataChoice.BODY_TEMPERATURE -> listOf(
            "bodyTemperature" to DataType.BodyTemperatureType.BODY_TEMPERATURE,
        )

        HealthDataChoice.BLOOD_GLUCOSE -> listOf(
            "glucoseLevel" to DataType.BloodGlucoseType.GLUCOSE_LEVEL,
            "measurementType" to DataType.BloodGlucoseType.MEASUREMENT_TYPE,
            "sampleSourceType" to DataType.BloodGlucoseType.SAMPLE_SOURCE_TYPE,
            "mealTime" to DataType.BloodGlucoseType.MEAL_TIME,
            "mealStatus" to DataType.BloodGlucoseType.MEAL_STATUS,
            "insulinInjected" to DataType.BloodGlucoseType.INSULIN_INJECTED,
            "medicationTaken" to DataType.BloodGlucoseType.MEDICATION_TAKEN,
            "seriesData" to DataType.BloodGlucoseType.SERIES_DATA,
        )

        HealthDataChoice.BLOOD_PRESSURE -> listOf(
            "systolic" to DataType.BloodPressureType.SYSTOLIC,
            "diastolic" to DataType.BloodPressureType.DIASTOLIC,
            "mean" to DataType.BloodPressureType.MEAN,
            "pulseRate" to DataType.BloodPressureType.PULSE_RATE,
            "medicationTaken" to DataType.BloodPressureType.MEDICATION_TAKEN,
        )

        HealthDataChoice.BODY_COMPOSITION -> listOf(
            "weight" to DataType.BodyCompositionType.WEIGHT,
            "height" to DataType.BodyCompositionType.HEIGHT,
            "bodyFat" to DataType.BodyCompositionType.BODY_FAT,
            "bodyFatMass" to DataType.BodyCompositionType.BODY_FAT_MASS,
            "fatFree" to DataType.BodyCompositionType.FAT_FREE,
            "fatFreeMass" to DataType.BodyCompositionType.FAT_FREE_MASS,
            "skeletalMuscle" to DataType.BodyCompositionType.SKELETAL_MUSCLE,
            "skeletalMuscleMass" to DataType.BodyCompositionType.SKELETAL_MUSCLE_MASS,
            "muscleMass" to DataType.BodyCompositionType.MUSCLE_MASS,
            "totalBodyWater" to DataType.BodyCompositionType.TOTAL_BODY_WATER,
            "basalMetabolicRate" to DataType.BodyCompositionType.BASAL_METABOLIC_RATE,
            "bodyMassIndex" to DataType.BodyCompositionType.BODY_MASS_INDEX,
        )

        HealthDataChoice.FLOORS_CLIMBED -> listOf(
            "floor" to DataType.FloorsClimbedType.FLOOR,
        )

        HealthDataChoice.WATER_INTAKE -> listOf(
            "amount" to DataType.WaterIntakeType.AMOUNT,
        )

        HealthDataChoice.NUTRITION -> listOf(
            "mealType" to DataType.NutritionType.MEAL_TYPE,
            "title" to DataType.NutritionType.TITLE,
            "calories" to DataType.NutritionType.CALORIES,
            "totalFat" to DataType.NutritionType.TOTAL_FAT,
            "saturatedFat" to DataType.NutritionType.SATURATED_FAT,
            "polysaturatedFat" to DataType.NutritionType.POLYSATURATED_FAT,
            "monosaturatedFat" to DataType.NutritionType.MONOSATURATED_FAT,
            "transFat" to DataType.NutritionType.TRANS_FAT,
            "carbohydrate" to DataType.NutritionType.CARBOHYDRATE,
            "dietaryFiber" to DataType.NutritionType.DIETARY_FIBER,
            "sugar" to DataType.NutritionType.SUGAR,
            "protein" to DataType.NutritionType.PROTEIN,
            "cholesterol" to DataType.NutritionType.CHOLESTEROL,
            "sodium" to DataType.NutritionType.SODIUM,
            "potassium" to DataType.NutritionType.POTASSIUM,
            "vitaminA" to DataType.NutritionType.VITAMIN_A,
            "vitaminC" to DataType.NutritionType.VITAMIN_C,
            "calcium" to DataType.NutritionType.CALCIUM,
            "iron" to DataType.NutritionType.IRON,
        )

        HealthDataChoice.ENERGY_SCORE -> listOf(
            "energyScore" to DataType.EnergyScoreType.ENERGY_SCORE,
        )

        HealthDataChoice.SLEEP_APNEA -> listOf(
            "detectedSign" to DataType.SleepApneaType.DETECTED_SIGN,
        )

        HealthDataChoice.IRREGULAR_HEART_RHYTHM -> listOf(
            "status" to DataType.IrregularHeartRhythmNotificationType.STATUS,
        )

        HealthDataChoice.USER_PROFILE -> listOf(
            "nickname" to DataType.UserProfileDataType.NICKNAME,
            "gender" to DataType.UserProfileDataType.GENDER,
            "dateOfBirth" to DataType.UserProfileDataType.DATE_OF_BIRTH,
            "height" to DataType.UserProfileDataType.HEIGHT,
            "weight" to DataType.UserProfileDataType.WEIGHT,
        )

        else -> emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun HealthDataPoint.value(field: Field<*>): Any? = getValue(field as Field<Any>)

    @Suppress("UNCHECKED_CAST")
    private fun UserDataPoint.value(field: Field<*>): Any? = getValue(field as Field<Any>)

    private fun dataSourceJson(point: HealthDataPoint) =
        point.dataSource?.let {
            JsonObject().apply {
                add("appId", SdkJson.toJson(it.appId))
                add("deviceId", SdkJson.toJson(it.deviceId))
            }
        } ?: com.google.gson.JsonNull.INSTANCE

    // -------------------------------------------------------------- 파일 쓰기

    private fun writeEnvelope(
        choice: HealthDataChoice,
        range: RequestedRange,
        records: List<JsonObject>,
    ): File {
        val envelope = JsonObject().apply {
            addProperty("dataType", choice.fileBaseName)
            addProperty("retrievedAt", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            addProperty("sdkCall", sdkCallOf(choice))
            add("requestedRange", JsonObject().apply {
                add("startLocalDateTime", SdkJson.toJson(range.startLocalDateTime))
                add("endLocalDateTime", SdkJson.toJson(range.endLocalDateTime))
            })
            addProperty("count", records.size)
            add("records", JsonArray().apply { records.forEach { add(it) } })
        }
        return writeBoth("${choice.fileBaseName}.json", JsonSupport.gson.toJson(envelope))
    }

    /**
     * `DataTypes.SLEEP` is the constant; `DataType.SleepType` is its class. Both are named
     * explicitly so the `sdkCall` string in the JSON is copy-pasteable Kotlin.
     */
    private fun sdkCallOf(choice: HealthDataChoice): String {
        val constant = "DataTypes.${choice.sdkConstant}"
        val typeClass = "DataType.${choice.dataType.javaClass.simpleName}"
        return when (choice.mode) {
            HealthDataChoice.ReadMode.READ ->
                "readData($constant.readDataRequestBuilder" +
                    ".setLocalTimeFilter(LocalTimeFilter.of(start, end)).setOrdering(Ordering.ASC).build())"

            HealthDataChoice.ReadMode.READ_USER_PROFILE ->
                "readData($constant.readDataRequestBuilder.build()) - no time filter, so the " +
                    "requested range does not apply"

            HealthDataChoice.ReadMode.AGGREGATE_LOCAL_TIME ->
                "aggregateData($typeClass.<operation>.requestBuilder" +
                    ".setLocalTimeFilterWithGroup(LocalTimeFilter.of(start, end), " +
                    "LocalTimeGroup.of(LocalTimeGroupUnit.DAILY, 1)).setOrdering(Ordering.ASC).build()) " +
                    "- see \"operation\" in each record"

            HealthDataChoice.ReadMode.AGGREGATE_LOCAL_DATE ->
                "aggregateData($typeClass.<operation>.requestBuilder" +
                    ".setLocalDateFilterWithGroup(LocalDateFilter.of(start, end), " +
                    "LocalDateGroup.of(LocalDateGroupUnit.DAILY, 1)).setOrdering(Ordering.ASC).build()) " +
                    "- see \"operation\" in each record"
        }
    }

    private fun writeSummary(
        results: List<ExportResult>,
        range: RequestedRange,
        days: Long,
        selected: Set<HealthDataChoice>,
    ) {
        val summary = ExportSummary(
            retrievedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            sdkVersion = SDK_VERSION,
            requestedDays = days,
            requestedDataTypes = HealthDataChoice.entries.filter { it in selected }.map { it.fileBaseName },
            requestedRange = range,
            results = results.map {
                ExportSummaryEntry(
                    it.dataType, it.count, it.file?.name, it.publicPath, it.skippedReason, it.error,
                )
            },
        )
        val file = writeBoth("_export_summary.json", JsonSupport.gson.toJson(summary))
        Log.i(TAG, "summary -> ${file.absolutePath}")
    }

    private fun writeBoth(fileName: String, content: String): File {
        val primary = File(outputDir(), fileName)
        primary.writeText(content)

        // internal-storage mirror (adb exec-out run-as fallback)
        runCatching { File(mirrorDir(), fileName).writeText(content) }
            .onFailure { Log.w(TAG, "mirror write failed for $fileName", it) }

        // shared Downloads copy, readable on the phone itself without USB/ADB
        runCatching { downloads.publish(fileName, content) }
            .onSuccess {
                publishedPaths[fileName] = it
                Log.i(TAG, "public -> $it")
            }
            .onFailure { Log.w(TAG, "Downloads export failed for $fileName", it) }

        return primary
    }

    companion object {
        const val TAG = "SHealthPoC"

        private const val SDK_VERSION = "samsung-health-data-api-1.1.0"
        private const val MAX_PAGES = 1000
    }
}
