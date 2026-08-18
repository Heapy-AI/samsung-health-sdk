# Samsung Health Data SDK 1.1.0 - verified API surface

Everything below was verified two ways:

1. `javap -public` over `app/libs/samsung-health-data-api-1.1.0.aar` → `classes.jar`
2. The official **Health Diary 1.1.0** sample app
   (`health-diary-sample-app-1.1.0/HealthDiary`)

The AAR shipped with the sample (`samsung-health-data-1.1.0.aar`) and the one in `app/libs/`
(`samsung-health-data-api-1.1.0.aar`) have **byte-identical `classes.jar`** (md5
`092081c6aaa8011c630add377421ab1f`) - only the file name differs.

No symbol in this project comes from guesswork, the old Samsung Health SDK for Android, or
Health Connect.

---

## 1. HealthDataStore creation / connection

```java
// com.samsung.android.sdk.health.data.HealthDataService  (Kotlin object)
public static final HealthDataStore getStore(android.content.Context);
public final HealthDataStore getStore(android.content.Context, kotlinx.coroutines.CoroutineScope);
```

Sample (`HealthViewModelFactory.kt`):

```kotlin
HealthDataService.getStore(context)
```

There is no explicit `connect()` / `disconnect()`; the store binds lazily. Connection problems
surface as `ResolvablePlatformException` from the first suspend call.

## 2. Permissions

```java
// com.samsung.android.sdk.health.data.permission.Permission
public static final Permission of(DataType, AccessType);
public final DataType getDataType();
public final AccessType getAccessType();

// com.samsung.android.sdk.health.data.permission.AccessType  (enum)
READ, WRITE
```

```java
// com.samsung.android.sdk.health.data.HealthDataStore   (suspend fns)
getGrantedPermissions(Set<Permission>)            : Set<Permission>
requestPermissions(Set<Permission>, Activity)     : Set<Permission>
```

> **Note** - `requestPermissions` takes an **`android.app.Activity`**, not a `Context`
> (the online guide page says `Context`; the bytecode says `Activity`).

Sample (`HealthMainViewModel.kt`) - check first, request only if incomplete:

```kotlin
val grantedPermissions = healthDataStore.getGrantedPermissions(permSet)
if (grantedPermissions.containsAll(permSet)) { /* ok */ } else {
    val result = healthDataStore.requestPermissions(permSet, activity)
}
```

## 3. DataTypes

`com.samsung.android.sdk.health.data.request.DataTypes` is an interface holding 26 constants.
Relevant here:

```java
public static final DataType$StepsType     STEPS;
public static final DataType$SleepType     SLEEP;
public static final DataType$HeartRateType HEART_RATE;
```

## 4. Steps - aggregate only (confirmed)

```java
public final class DataType$StepsType extends DataType {          // <- no Readable interface
  public static final AggregateOperation<Long, AggregateRequest$LocalTimeBuilder<Long>> TOTAL;
  public String getName();
}
```

`StepsType` does **not** implement `DataType.Readable`, so it has **no
`readDataRequestBuilder`** and cannot be used with `readData()`. `TOTAL` is its only member.
The Health Diary sample reads steps the same way.

* metric: `DataType.StepsType.TOTAL` → `AggregateOperation<Long, …>`
* builder: `TOTAL.requestBuilder` → `AggregateRequest.LocalTimeBuilder<Long>` with
  `setLocalTimeFilter`, `setLocalTimeFilterWithGroup`, `setOrdering`, `setSourceFilter`,
  `setPageSize`, `setPageToken`, `build`
* call: `healthDataStore.aggregateData(request)` → `DataResponse<AggregatedData<Long>>`
* returned fields (`AggregatedData<T>`):
  `getValue(): T?`, `getStartTime(): Instant`, `getEndTime(): Instant`,
  `getStartLocalDateTime(): LocalDateTime`, `getEndLocalDateTime(): LocalDateTime`,
  `getValueOrDefault(T): T`

Sample uses `LocalTimeGroupUnit.HOURLY, 1` over one day; this PoC uses
`LocalTimeGroupUnit.DAILY, 1` over 7 days. Units available:
`MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY`.

## 5. Sleep

```java
public final class DataType$SleepType extends DataType
    implements DataType$Readable<HealthDataPoint, ReadDataRequest$DualTimeBuilder<HealthDataPoint>>, … {
  public static final AggregateOperation<Duration, …> TOTAL_DURATION;
  public static final Field<Duration>            DURATION;
  public static final Field<List<SleepSession>>  SESSIONS;
  public static final Field<Integer>             SLEEP_SCORE;
}
```

```kotlin
DataTypes.SLEEP.readDataRequestBuilder
    .setLocalTimeFilter(LocalTimeFilter.of(start, end))
    .setOrdering(Ordering.ASC)
    .build()
// -> healthDataStore.readData(request): DataResponse<HealthDataPoint>
```

`com.samsung.android.sdk.health.data.data.entries.SleepSession`:

```java
Instant getStartTime();  Instant getEndTime();  Duration getDuration();
List<SleepSession$SleepStage> getStages();      // nullable
```

`SleepSession.SleepStage`:

```java
Instant getStartTime();  Instant getEndTime();
DataType$SleepType$StageType getStage();        // UNDEFINED, AWAKE, LIGHT, DEEP, REM
```

## 6. Heart rate

```java
public final class DataType$HeartRateType extends DataType
    implements DataType$Readable<HealthDataPoint, ReadDataRequest$DualTimeBuilder<HealthDataPoint>>, … {
  public static final AggregateOperation<Float, …> MIN;
  public static final AggregateOperation<Float, …> MAX;
  public static final Field<Float>            HEART_RATE;
  public static final Field<Float>            MIN_HEART_RATE;
  public static final Field<Float>            MAX_HEART_RATE;
  public static final Field<List<HeartRate>>  SERIES_DATA;
}
```

`SERIES_DATA` is a plain `Field` on the same data point - **no extra SDK call is required**:

```kotlin
point.getValue(DataType.HeartRateType.SERIES_DATA)   // List<HeartRate>?
```

`com.samsung.android.sdk.health.data.data.entries.HeartRate`:

```java
float getHeartRate();  float getMin();  float getMax();
Instant getStartTime();  Instant getEndTime();
```

## 7. HealthDataPoint

```java
String   getUid();              String   getClientDataId();   Integer getClientVersion();
DataSource getDataSource();     Instant  getUpdateTime();
Instant  getStartTime();        Instant  getEndTime();        ZoneOffset getZoneOffset();
<T> T    getValue(Field<T>);    <T> T    getValueOrDefault(Field<T>, T);
LocalDateTime getStartLocalDateTime();   LocalDateTime getEndLocalDateTime();
```

`getStartLocalDateTime()` / `getEndLocalDateTime()` / `getValue()` are **functions**, not
Kotlin properties - they must be called with parentheses.

`DataSource`: `getAppId(): String`, `getDeviceId(): String`.

## 8. Time filters, grouping, ordering

```java
LocalTimeFilter.of(LocalDateTime, LocalDateTime)
LocalTimeFilter.of(LocalDateTime, LocalDateTime, boolean inclusiveStart, boolean inclusiveEnd)
LocalTimeFilter.since(LocalDateTime)   LocalTimeFilter.to(LocalDateTime)
LocalTimeGroup.of(LocalTimeGroupUnit, int multiplier)
Ordering.ASC | Ordering.DESC
```

Also available: `LocalDateFilter/Group`, `InstantTimeFilter/Group`, `IdFilter`,
`ReadSourceFilter`, `AggregateSourceFilter`.

## 9. Return objects and paging

```java
public final class DataResponse<T extends Parcelable> {
  public final String  getPageToken();
  public final List<T> getDataList();
}
```

`readData` → `DataResponse<HealthDataPoint>`, `aggregateData` → `DataResponse<AggregatedData<T>>`.
Because `pageToken` exists, this PoC loops on `setPageToken(...)` until the token is empty;
otherwise long ranges would be silently truncated.

## 10. Lifecycle / coroutines

Every data call is a Kotlin `suspend` function (`Continuation` parameter in the bytecode).
The sample runs them in `viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler)`;
this PoC runs them in `lifecycleScope.launch` with a try/catch, which is equivalent for a
single-activity PoC. `*Async` variants returning `AsyncSingleFuture` exist for Java callers.

## 11. Errors

```java
HealthDataException extends RuntimeException { Integer getErrorCode(); String getErrorMessage(); }
  ├ AuthorizationException
  ├ InvalidRequestException
  ├ PlatformInternalException
  └ ResolvablePlatformException { boolean getHasResolution(); void resolve(Activity); }
```

`ErrorCode` constants include `ERR_PLATFORM_NOT_INSTALLED`, `ERR_OLD_VERSION_PLATFORM`,
`ERR_PLATFORM_DISABLED`, `ERR_PLATFORM_NOT_INITIALIZED`, `ERR_NO_USER_PERMISSION`,
`ERR_INVALID_PLATFORM_SIGNATURE`, `ERR_CONNECTION_FAIL`, …

## 12. AndroidManifest

**Nothing Samsung-specific is required in the app manifest** - the Health Diary sample's
manifest has no SDK entries either. The AAR's own manifest is merged in:

```xml
<uses-sdk android:minSdkVersion="29" />
<queries>
    <package android:name="com.sec.android.app.shealth" />
    <package android:name="com.samsung.android.wear.shealth" />
</queries>
<uses-permission android:name="android.permission.INTERNET" />
```

(Confirmed in `app/build/intermediates/merged_manifest/debug/.../AndroidManifest.xml`.)

## 13. Gradle

Sample `app/build.gradle`:

```groovy
plugins { id 'com.android.application'; id 'kotlin-android'; id 'kotlin-kapt'; id 'kotlin-parcelize' }
android { compileSdk 34; minSdkVersion 29; targetSdkVersion 34
          compileOptions { sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 }
          kotlinOptions { jvmTarget = "17" } }
dependencies {
    implementation (files("libs/samsung-health-data-1.1.0.aar"))
    implementation 'androidx.activity:activity-ktx:1.9.0'
    implementation 'androidx.core:core-ktx:1.13.1'
    implementation "com.google.code.gson:gson:2.11.0"
}
```

The AAR has **no POM**, and `classes.jar` bundles neither `kotlin` nor `kotlinx` - so
`kotlin-stdlib` (via the Kotlin plugin) and `kotlinx-coroutines-android` must come from the app.
`kotlin-kapt` in the sample is only for its data binding; this PoC does not need it.

The AAR also ships `proguard.txt` (consumer rules keeping all public SDK members and
`Parcelable` CREATORs), applied automatically by AGP - no manual SDK keep-rules needed.

## 14. Sources

* `app/libs/samsung-health-data-api-1.1.0.aar` (javap)
* `health-diary-sample-app-1.1.0/HealthDiary` (official sample)
* <https://developer.samsung.com/health/data/overview.html>
* <https://developer.samsung.com/health/data/guide/developer-mode.html>
* <https://developer.samsung.com/health/data/guide/app-verification.html>

---

# 부록: DataTypes 25종 능력 매트릭스 (AAR 바이트코드 검증)

`javap` 로 각 `DataType` 서브클래스가 구현한 인터페이스와 보유 멤버를 확인한 결과입니다.
READ = `DataType.Readable` 구현 (readData 가능), AGG = `AggregateOperation` 보유.

| DataTypes 상수 | READ | AGG | 반환 | 이 PoC |
| --- | --- | --- | --- | --- |
| SLEEP | O | TOTAL_DURATION | HealthDataPoint | readData |
| HEART_RATE | O | MIN, MAX | HealthDataPoint | readData |
| EXERCISE | O | TOTAL_CALORIES, TOTAL_DURATION | HealthDataPoint | readData |
| BLOOD_OXYGEN | O | - | HealthDataPoint | readData |
| SKIN_TEMPERATURE | O | - | HealthDataPoint | readData |
| BODY_TEMPERATURE | O | - | HealthDataPoint | readData |
| BLOOD_GLUCOSE | O | - | HealthDataPoint | readData |
| BLOOD_PRESSURE | O | - | HealthDataPoint | readData |
| BODY_COMPOSITION | O | - | HealthDataPoint | readData |
| FLOORS_CLIMBED | O | TOTAL | HealthDataPoint | readData |
| WATER_INTAKE | O | TOTAL | HealthDataPoint | readData |
| NUTRITION | O | TOTAL_CALORIES | HealthDataPoint | readData |
| ENERGY_SCORE | O | - | HealthDataPoint | readData |
| SLEEP_APNEA | O | - | HealthDataPoint | readData |
| IRREGULAR_HEART_RHYTHM_NOTIFICATION | O | - | HealthDataPoint | readData |
| USER_PROFILE | O | - | **UserDataPoint** | readData (UserProfileBuilder, 필터 없음) |
| STEPS | **X** | TOTAL | - | aggregateData |
| ACTIVITY_SUMMARY | **X** | TOTAL_ACTIVE_TIME, TOTAL_ACTIVE_CALORIES_BURNED, TOTAL_CALORIES_BURNED, TOTAL_DISTANCE | - | aggregateData |
| STEPS_GOAL | **X** | LAST | - | aggregateData |
| SLEEP_GOAL | **X** | LAST_BED_TIME, LAST_WAKE_UP_TIME | - | aggregateData |
| ACTIVE_TIME_GOAL | **X** | LAST | - | aggregateData |
| ACTIVE_CALORIES_BURNED_GOAL | **X** | LAST | - | aggregateData |
| WATER_INTAKE_GOAL | **X** | LAST | - | aggregateData |
| NUTRITION_GOAL | **X** | LAST_CALORIES | - | aggregateData |
| EXERCISE_LOCATION | **X** | **없음** | - | **제외** (아래 참고) |

`EXERCISE_LOCATION` 은 `Readable` 도 아니고 `AggregateOperation` 도 없어 단독 조회 경로가
없습니다. 위치 데이터는 `DataType.ExerciseType.SESSIONS` 의 `ExerciseSession.route`
(`List<ExerciseLocation>`) 안에 들어오므로 EXERCISE 를 선택하면 함께 추출됩니다.

## 집계 빌더 종류

| 빌더 | 필터 메서드 | 사용 타입 |
| --- | --- | --- |
| `AggregateRequest.LocalTimeBuilder` | `setLocalTimeFilter(WithGroup)` | STEPS.TOTAL, ACTIVITY_SUMMARY.*, FLOORS_CLIMBED.TOTAL, EXERCISE.TOTAL_CALORIES |
| `AggregateRequest.LocalDateBuilder` | `setLocalDateFilter(WithGroup)` | SLEEP.TOTAL_DURATION, HEART_RATE.MIN/MAX, EXERCISE.TOTAL_DURATION |
| `AggregateRequest.AllSourceLocalDateBuilder` | `setLocalDateFilter(WithGroup)` | 모든 *_GOAL 타입 |
| `AggregateRequest.DualTimeBuilder` | `setLocalTimeFilter` / `setInstantTimeFilter` | NUTRITION.TOTAL_CALORIES, WATER_INTAKE.TOTAL |

`LocalDateGroupUnit`: DAILY, WEEKLY, MONTHLY, YEARLY
`LocalTimeGroupUnit`: MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY

## entry 클래스 (SdkJson 에서 명시적으로 매핑)

| 클래스 | 공개 getter |
| --- | --- |
| `SleepSession` | startTime, endTime, duration, stages |
| `SleepSession.SleepStage` | startTime, endTime, stage(UNDEFINED/AWAKE/LIGHT/DEEP/REM) |
| `HeartRate` | heartRate, min, max, startTime, endTime |
| `OxygenSaturation` | oxygenSaturation, min, max, startTime, endTime |
| `SkinTemperature` | skinTemperature, min, max, startTime, endTime |
| `BloodGlucose` | glucose, timestamp |
| `ExerciseSession` | exerciseType, customTitle, startTime, endTime, duration, distance, calories, altitudeGain/Loss, count, countType, max/meanSpeed, max/meanCalorieBurnRate, max/meanCadence, min/max/meanHeartRate, min/maxAltitude, incline/declineDistance, max/meanPower, max/meanRpm, vo2Max, autoDetected, comment, swimmingLog, route, log |
| `ExerciseLog` | timestamp, heartRate, cadence, count, power, speed |
| `ExerciseLocation` | timestamp, latitude, longitude, altitude, accuracy |
| `SwimmingLog` | poolLength, poolLengthUnit, totalDistance, totalDuration, swimmingIntervals |
| `SwimmingLog.SwimmingInterval` | interval, duration, strokeCount, strokeType |
