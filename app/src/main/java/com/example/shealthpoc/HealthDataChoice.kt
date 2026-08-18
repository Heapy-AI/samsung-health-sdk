package com.example.shealthpoc

import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes

/**
 * Every data type this PoC can export, and how it has to be read.
 *
 * The list is the full `DataTypes` surface of samsung-health-data-api-1.1.0 (25 constants),
 * minus `EXERCISE_LOCATION`, which implements neither `DataType.Readable` nor any
 * `AggregateOperation` - its data comes back inside `ExerciseSession.route` instead.
 *
 * [mode] was determined from the AAR bytecode, not guessed:
 *  - READ            : the type implements `DataType.Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<..>>`
 *  - READ_USER_PROFILE: readable, but through `ReadDataRequest.UserProfileBuilder` -> `UserDataPoint`
 *  - AGGREGATE_*     : the type has no read builder, only `AggregateOperation`s
 */
enum class HealthDataChoice(
    val fileBaseName: String,
    val label: String,
    /** Exact `DataTypes.X` constant name, used in the `sdkCall` field of the JSON output. */
    val sdkConstant: String,
    val dataType: DataType,
    val mode: ReadMode,
) {
    // STEPS / SLEEP / HEART_RATE first - they are the default selection.
    STEPS("steps", "걸음 수 (집계 전용)", "STEPS", DataTypes.STEPS, ReadMode.AGGREGATE_LOCAL_TIME),
    SLEEP("sleep", "수면", "SLEEP", DataTypes.SLEEP, ReadMode.READ),
    HEART_RATE("heart_rate", "심박수", "HEART_RATE", DataTypes.HEART_RATE, ReadMode.READ),
    EXERCISE("exercise", "운동", "EXERCISE", DataTypes.EXERCISE, ReadMode.READ),
    BLOOD_OXYGEN("blood_oxygen", "혈중 산소", "BLOOD_OXYGEN", DataTypes.BLOOD_OXYGEN, ReadMode.READ),
    SKIN_TEMPERATURE("skin_temperature", "피부 온도", "SKIN_TEMPERATURE", DataTypes.SKIN_TEMPERATURE, ReadMode.READ),
    BODY_TEMPERATURE("body_temperature", "체온", "BODY_TEMPERATURE", DataTypes.BODY_TEMPERATURE, ReadMode.READ),
    BLOOD_GLUCOSE("blood_glucose", "혈당", "BLOOD_GLUCOSE", DataTypes.BLOOD_GLUCOSE, ReadMode.READ),
    BLOOD_PRESSURE("blood_pressure", "혈압", "BLOOD_PRESSURE", DataTypes.BLOOD_PRESSURE, ReadMode.READ),
    BODY_COMPOSITION("body_composition", "체성분", "BODY_COMPOSITION", DataTypes.BODY_COMPOSITION, ReadMode.READ),
    FLOORS_CLIMBED("floors_climbed", "오른 층수", "FLOORS_CLIMBED", DataTypes.FLOORS_CLIMBED, ReadMode.READ),
    WATER_INTAKE("water_intake", "물 섭취", "WATER_INTAKE", DataTypes.WATER_INTAKE, ReadMode.READ),
    NUTRITION("nutrition", "영양 (음식)", "NUTRITION", DataTypes.NUTRITION, ReadMode.READ),
    ENERGY_SCORE("energy_score", "에너지 점수", "ENERGY_SCORE", DataTypes.ENERGY_SCORE, ReadMode.READ),
    SLEEP_APNEA("sleep_apnea", "수면 무호흡", "SLEEP_APNEA", DataTypes.SLEEP_APNEA, ReadMode.READ),
    IRREGULAR_HEART_RHYTHM(
        "irregular_heart_rhythm", "불규칙 심장 리듬 알림", "IRREGULAR_HEART_RHYTHM_NOTIFICATION",
        DataTypes.IRREGULAR_HEART_RHYTHM_NOTIFICATION, ReadMode.READ,
    ),
    USER_PROFILE("user_profile", "사용자 프로필", "USER_PROFILE", DataTypes.USER_PROFILE, ReadMode.READ_USER_PROFILE),

    // ---- aggregateData() only: these types have no read builder ----------
    ACTIVITY_SUMMARY(
        "activity_summary", "활동 요약 (집계 전용)", "ACTIVITY_SUMMARY",
        DataTypes.ACTIVITY_SUMMARY, ReadMode.AGGREGATE_LOCAL_TIME,
    ),
    STEPS_GOAL("steps_goal", "걸음 수 목표 (집계 전용)", "STEPS_GOAL", DataTypes.STEPS_GOAL, ReadMode.AGGREGATE_LOCAL_DATE),
    SLEEP_GOAL("sleep_goal", "수면 목표 (집계 전용)", "SLEEP_GOAL", DataTypes.SLEEP_GOAL, ReadMode.AGGREGATE_LOCAL_DATE),
    ACTIVE_TIME_GOAL(
        "active_time_goal", "활동 시간 목표 (집계 전용)", "ACTIVE_TIME_GOAL",
        DataTypes.ACTIVE_TIME_GOAL, ReadMode.AGGREGATE_LOCAL_DATE,
    ),
    ACTIVE_CALORIES_BURNED_GOAL(
        "active_calories_burned_goal", "활동 칼로리 목표 (집계 전용)", "ACTIVE_CALORIES_BURNED_GOAL",
        DataTypes.ACTIVE_CALORIES_BURNED_GOAL, ReadMode.AGGREGATE_LOCAL_DATE,
    ),
    WATER_INTAKE_GOAL(
        "water_intake_goal", "물 섭취 목표 (집계 전용)", "WATER_INTAKE_GOAL",
        DataTypes.WATER_INTAKE_GOAL, ReadMode.AGGREGATE_LOCAL_DATE,
    ),
    NUTRITION_GOAL(
        "nutrition_goal", "영양 목표 (집계 전용)", "NUTRITION_GOAL",
        DataTypes.NUTRITION_GOAL, ReadMode.AGGREGATE_LOCAL_DATE,
    ),
    ;

    /** Samsung Health consent this type needs. */
    val permission: Permission = Permission.of(dataType, AccessType.READ)

    enum class ReadMode { READ, READ_USER_PROFILE, AGGREGATE_LOCAL_TIME, AGGREGATE_LOCAL_DATE }

    companion object {
        /** Ticked by default - the three types the PoC started with. */
        val DEFAULT_SELECTION: Set<HealthDataChoice> = setOf(STEPS, SLEEP, HEART_RATE)

        fun permissionsOf(choices: Set<HealthDataChoice>): Set<Permission> =
            choices.map { it.permission }.toSet()
    }
}
