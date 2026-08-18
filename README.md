# Samsung Health Data SDK 데이터 추출 PoC

Samsung Health에 저장된 건강 데이터를 **Samsung Health Data SDK 1.1.0**을 통해 직접 조회하고, JSON 파일로 저장하기 위한 Android 테스트 프로젝트입니다.

걸음 수, 수면, 심박수뿐만 아니라 운동, 혈중 산소, 체성분 등 Samsung Health Data SDK에서 읽을 수 있는 여러 건강 데이터를 선택해서 조회할 수 있습니다.

조회한 데이터는 별도의 서버나 DB로 전송하지 않으며, 휴대폰의 `Download/SHealthPoC` 폴더에 JSON 파일로 저장됩니다. 필요하면 ADB를 이용해 PC의 `data/` 폴더로 가져올 수도 있습니다.

> 이 프로젝트는 **Samsung Health Data SDK 1.1.0**을 사용합니다.
> Health Connect 또는 구버전 Samsung Health SDK for Android를 사용하는 프로젝트가 아닙니다.

---

## 1. 동작 방식

전체 데이터 흐름은 다음과 같습니다.

```text
Samsung Health
      ↓
Samsung Health Data SDK 1.1.0
      ↓
Android 앱 (Kotlin)
      ↓
데이터 조회
      ↓
JSON 변환
      ↓
Download/SHealthPoC/
```

앱에서 조회할 데이터와 기간을 선택한 뒤 **조회 및 저장**을 누르면 Samsung Health에서 데이터를 가져와 데이터 종류별 JSON 파일로 저장합니다.

예를 들어 걸음 수, 수면, 심박수를 선택하면 다음과 같이 생성됩니다.

```text
Download/SHealthPoC/
├── steps.json
├── sleep.json
├── heart_rate.json
└── _export_summary.json
```

`_export_summary.json`에는 조회 기간, 요청한 데이터 종류, 데이터별 조회 건수 및 오류 여부 등 이번 조회의 전체 결과가 기록됩니다.

---

## 2. 주요 기능

* Samsung Health Data SDK 1.1.0 직접 연동
* 최대 24종의 건강 데이터 선택 조회
* 조회 기간 설정: 1~365일
* 선택한 데이터에 필요한 READ 권한만 요청
* `readData()` / `aggregateData()` 방식 모두 지원
* 페이징을 포함한 전체 데이터 조회
* SDK 데이터를 JSON으로 변환
* 휴대폰 `Download/SHealthPoC`에 바로 저장
* ADB를 이용한 PC 데이터 회수 지원
* 조회 결과 및 오류 Logcat 출력

기본 선택 데이터는 다음 3종입니다.

* 걸음 수
* 수면
* 심박수

---

## 3. 조회 가능한 데이터

현재 다음 24종의 데이터를 조회할 수 있습니다.

### 건강 및 생체 데이터

| 데이터          | 저장 파일                         | 조회 방식    |
| ------------ | ----------------------------- | -------- |
| 수면           | `sleep.json`                  | readData |
| 심박수          | `heart_rate.json`             | readData |
| 혈중 산소        | `blood_oxygen.json`           | readData |
| 피부 온도        | `skin_temperature.json`       | readData |
| 체온           | `body_temperature.json`       | readData |
| 혈당           | `blood_glucose.json`          | readData |
| 혈압           | `blood_pressure.json`         | readData |
| 체성분          | `body_composition.json`       | readData |
| 에너지 점수       | `energy_score.json`           | readData |
| 수면 무호흡       | `sleep_apnea.json`            | readData |
| 불규칙 심장 리듬 알림 | `irregular_heart_rhythm.json` | readData |
| 사용자 프로필      | `user_profile.json`           | readData\* |

\* 사용자 프로필은 SDK의 `UserProfileBuilder`에 시간 필터가 없어 **조회 기간이 적용되지 않습니다.** 기간을 며칠로 설정하든 현재 프로필 값을 그대로 가져옵니다.

### 활동 및 생활 데이터

| 데이터    | 저장 파일                   | 조회 방식         |
| ------ | ----------------------- | ------------- |
| 운동     | `exercise.json`         | readData      |
| 오른 층수  | `floors_climbed.json`   | readData      |
| 물 섭취   | `water_intake.json`     | readData      |
| 영양(음식) | `nutrition.json`        | readData      |
| 걸음 수   | `steps.json`            | aggregateData |
| 활동 요약  | `activity_summary.json` | aggregateData |

### 목표 데이터

| 데이터       | 저장 파일                              | 조회 방식         |
| --------- | ---------------------------------- | ------------- |
| 걸음 수 목표   | `steps_goal.json`                  | aggregateData |
| 수면 목표     | `sleep_goal.json`                  | aggregateData |
| 활동 시간 목표  | `active_time_goal.json`            | aggregateData |
| 활동 칼로리 목표 | `active_calories_burned_goal.json` | aggregateData |
| 물 섭취 목표   | `water_intake_goal.json`           | aggregateData |
| 영양 목표     | `nutrition_goal.json`              | aggregateData |

### Exercise Location

`EXERCISE_LOCATION`은 독립적으로 조회하지 않습니다.

SDK에서 단독 `readData()` 또는 `aggregateData()` 조회 경로를 제공하지 않으며, 위치 정보는 `ExerciseSession.route`를 통해 운동 데이터에 포함됩니다.

따라서 **운동** 데이터를 선택하면 사용 가능한 위치 정보도 함께 추출됩니다.

---

## 4. 실행 전 준비

### 필요한 환경

| 항목                      | 요구사항                   |
| ----------------------- | ---------------------- |
| Android 기기              | Android 10 (API 29) 이상 |
| Samsung Health          | 6.30.2 이상              |
| Samsung Health Data SDK | 1.1.0                  |
| Java                    | 17                     |
| 테스트 환경                  | Android 실기기            |

Samsung Health Data SDK는 에뮬레이터를 지원하지 않으므로 **실제 Android 기기에서 테스트해야 합니다.**

### SDK AAR 배치

SDK AAR은 재배포하지 않으므로 **저장소에 포함되어 있지 않습니다.** (`.gitignore`에서 제외)

[Samsung Developer](https://developer.samsung.com/health/data/overview.html)에서 SDK를 내려받아 압축을 푼 뒤 AAR 파일을 다음 위치에 넣습니다.

```text
app/libs/samsung-health-data-api-1.1.0.aar
```

프로젝트는 `libs/*.aar` 전체를 참조하므로 버전이 달라도 파일명을 바꿀 필요는 없습니다.

AAR이 없는 상태로 빌드하면 다음 메시지와 함께 중단됩니다.

```text
Samsung Health Data SDK AAR not found.
  1. Download the SDK from https://developer.samsung.com/health/data/overview.html
  2. Unzip it and copy the AAR (e.g. samsung-health-data-api-1.1.0.aar)
     into:  app/libs/
  3. Re-run the build.
```

---

## 5. Samsung Health 개발자 모드 설정

개발 중인 앱에서 Samsung Health 데이터를 읽으려면 Samsung Health 앱의 **개발자 모드**를 활성화해야 합니다.

Android 시스템의 `개발자 옵션`과는 별개의 설정입니다.

### 활성화 방법

1. Samsung Health 앱 실행
2. 우측 상단 `⋮` 선택
3. **설정** 선택
4. **Samsung Health 정보** 진입
5. 버전이 표시된 영역을 빠르게 10회 이상 터치
6. **개발자 모드 (Samsung Health Data SDK)** 진입
7. Developer mode 활성화

이 프로젝트는 Samsung Health 데이터를 **읽기(Read)**만 하므로 개발 단계에서는 별도의 데이터 쓰기용 Access Code를 사용하지 않습니다.

개발자 모드가 활성화되지 않은 상태에서 앱을 실행하면 `AuthorizationException`과 같은 접근 오류가 발생할 수 있습니다.

---

## 6. 빌드 및 실행

### APK 빌드

Windows PowerShell 기준:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

.\gradlew.bat :app:assembleDebug
```

`java`가 이미 PATH에 있다면 첫 줄은 생략해도 됩니다.
(cmd에서는 `set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr` 형태를 씁니다.)

빌드된 APK는 다음 위치에서 확인할 수 있습니다.

```text
app/build/outputs/apk/debug/app-debug.apk
```

### ADB로 설치

휴대폰이 ADB로 연결되어 있다면:

```powershell
.\gradlew.bat :app:installDebug
```

앱 실행:

```powershell
adb shell am start -n com.example.shealthpoc/.MainActivity
```

로그 확인:

```powershell
adb logcat -s SHealthPoC
```

### USB/ADB를 사용하지 않는 경우

`app-debug.apk`를 휴대폰으로 직접 옮긴 뒤 설치해도 됩니다.

Android Studio에서 프로젝트를 열어 실기기로 실행하는 것도 가능합니다.

---

## 7. 앱 사용 방법

앱을 실행하면 조회할 데이터와 조회 기간을 선택할 수 있습니다.

### 1. 데이터 선택

조회하려는 건강 데이터를 체크합니다.

`전체` / `해제` 버튼으로 한 번에 선택하거나 해제할 수도 있습니다.

기본값은 다음과 같습니다.

* 걸음 수
* 수면
* 심박수

선택한 항목은 저장되므로 앱을 다시 실행해도 이전 선택 상태가 유지됩니다.

### 2. 조회 기간 입력

1~365일 사이에서 조회 기간을 지정합니다.

예를 들어 `30`을 입력하면 오늘을 포함한 최근 30일의 데이터를 조회합니다.

단, **사용자 프로필**은 SDK가 기간 필터를 제공하지 않아 이 설정의 영향을 받지 않습니다.

### 3. 조회 및 저장

**조회 및 저장** 버튼을 누릅니다.

필요한 Samsung Health 데이터 접근 권한이 없는 경우 Samsung Health의 권한 동의 화면이 나타납니다.

### 4. 권한 허용

선택한 데이터 중 필요한 항목을 허용합니다.

모든 권한을 허용할 필요는 없습니다.

예를 들어 걸음 수와 수면만 허용하고 심박수를 허용하지 않았다면:

```text
steps        → 조회
sleep        → 조회
heart_rate   → permission not granted
```

처럼 처리됩니다.

권한이 없는 데이터는 `_export_summary.json`에 기록됩니다.

---

## 8. 데이터 확인

조회가 완료되면 JSON 파일을 휴대폰에서 바로 확인할 수 있습니다.

```text
내 파일
→ 내장 저장공간
→ Download
→ SHealthPoC
```

예:

```text
Download/SHealthPoC/
├── steps.json
├── sleep.json
├── heart_rate.json
└── _export_summary.json
```

파일은 다시 조회할 때 같은 이름으로 갱신됩니다.

따라서 반복해서 실행해도 다음처럼 중복 파일이 계속 생성되지 않습니다.

```text
steps.json
steps (1).json
steps (2).json
```

### 데이터가 0건인 경우

예를 들어:

```text
heart_rate: 0 records
```

라고 표시되더라도 SDK 연결 실패를 의미하지는 않습니다.

선택한 기간에 Samsung Health에 해당 데이터가 저장되어 있지 않은 경우에도 `0 records`가 반환될 수 있습니다.

필요한 경우 SDK와 함께 제공되는 다음 앱을 이용해 Samsung Health에 실제 데이터가 존재하는지 확인할 수 있습니다.

```text
app/tool/DataViewer_1.1.0.apk
```

---

## 9. 데이터 저장 위치

조회 결과는 동일한 내용을 여러 위치에 저장합니다.

### ① 휴대폰 Download 폴더

USB 없이 휴대폰에서 바로 확인하기 위한 위치입니다.

```text
Download/SHealthPoC/
```

예:

```text
Download/SHealthPoC/steps.json
Download/SHealthPoC/sleep.json
Download/SHealthPoC/heart_rate.json
Download/SHealthPoC/_export_summary.json
```

Android의 `MediaStore.Downloads`를 사용합니다.

별도의 `WRITE_EXTERNAL_STORAGE` 또는 `MANAGE_EXTERNAL_STORAGE` 권한은 사용하지 않습니다.

### ② 앱 전용 외부 저장소

ADB를 이용해 PC로 가져오기 위한 저장 위치입니다.

```text
/sdcard/Android/data/com.example.shealthpoc/files/data/
```

### ③ 앱 내부 저장소

외부 앱 전용 저장소 접근이 제한되는 기기를 위한 미러입니다.

```text
/data/data/com.example.shealthpoc/files/data/
```

---

## 10. PC의 `data/` 폴더로 가져오기

USB 디버깅 또는 무선 ADB가 연결된 경우 조회 결과를 PC 프로젝트의 `data/` 폴더로 가져올 수 있습니다.

### Windows

```powershell
powershell -ExecutionPolicy Bypass -File scripts\pull-data.ps1
```

### Git Bash / WSL / macOS

```bash
./scripts/pull-data.sh
```

스크립트는 기기에 존재하는 JSON 파일 목록을 확인한 뒤 모두 가져옵니다.

기본적으로:

1. 앱 전용 외부 저장소 확인
2. `adb pull` 시도
3. 접근할 수 없는 경우 `run-as` 방식으로 재시도
4. 프로젝트의 `data/` 폴더에 저장

순서로 동작합니다.

### 옵션

기기가 여러 대 연결되어 있다면:

```powershell
-Serial R3CN30XXXXX
```

기존 JSON을 삭제하고 새로 가져오려면:

```powershell
-Clean
```

또는:

```bash
./scripts/pull-data.sh --clean
```

### 직접 가져오기

```powershell
adb pull /sdcard/Android/data/com.example.shealthpoc/files/data/. data
```

외부 저장소 접근이 제한되는 경우:

```powershell
adb exec-out run-as com.example.shealthpoc cat files/data/steps.json > data\steps.json
```

---

## 11. JSON 형식

모든 데이터 파일에는 조회 정보와 실제 레코드가 함께 저장됩니다.

### 기본 구조

```json
{
  "dataType": "sleep",
  "retrievedAt": "2026-08-19T00:42:36+09:00",
  "sdkCall": "readData(DataTypes.SLEEP.readDataRequestBuilder.setLocalTimeFilter(LocalTimeFilter.of(start, end)).setOrdering(Ordering.ASC).build())",
  "requestedRange": {
    "startLocalDateTime": "2026-07-21T00:00:00",
    "endLocalDateTime": "2026-08-20T00:00:00"
  },
  "count": 19,
  "records": []
}
```

각 파일의 `records`에 실제 Samsung Health 데이터가 들어갑니다.

> JSON의 키 순서는 선언 순서가 아니라 알파벳순으로 출력됩니다. Android 런타임(ART)이
> `getDeclaredFields()`의 순서를 보장하지 않아 생기는 현상이며, 값에는 영향이 없습니다.

### 걸음 수

걸음 수는 SDK에서 원본 레코드 조회를 제공하지 않아 `aggregateData()`로 집계하여 가져옵니다.

예:

```json
{
  "operation": "TOTAL",
  "startTime": "2026-08-12T15:00:00Z",
  "endTime": "2026-08-13T15:00:00Z",
  "startLocalDateTime": "2026-08-13T00:00:00",
  "endLocalDateTime": "2026-08-14T00:00:00",
  "value": 4884
}
```

현재는 일 단위(`DAILY`)로 집계합니다.

### 연산이 여러 개인 집계 데이터

집계 데이터 중에는 SDK가 여러 연산을 제공하는 것이 있습니다. 이 경우 **한 파일에 모든 연산의 레코드가 함께** 들어가며, `operation` 값으로 구분합니다.

| 데이터 | 파일 | 포함되는 `operation` |
| --- | --- | --- |
| 활동 요약 | `activity_summary.json` | `TOTAL_ACTIVE_TIME`, `TOTAL_ACTIVE_CALORIES_BURNED`, `TOTAL_CALORIES_BURNED`, `TOTAL_DISTANCE` |
| 수면 목표 | `sleep_goal.json` | `LAST_BED_TIME`, `LAST_WAKE_UP_TIME` |

나머지 집계 데이터는 연산이 하나씩입니다 (걸음 수 `TOTAL`, 목표 데이터 `LAST` 또는 `LAST_CALORIES`).

### 수면

수면 데이터에는 다음과 같은 정보가 포함될 수 있습니다.

* 수면 시작/종료 시각
* 수면 시간
* 수면 점수
* 수면 세션
* 수면 단계

수면 단계는 다음 값으로 구분됩니다.

```text
UNDEFINED
AWAKE
LIGHT
DEEP
REM
```

### 심박수

심박 데이터에는 다음과 같은 값이 포함될 수 있습니다.

* 심박수
* 최소 심박수
* 최대 심박수
* 연속 심박 데이터(seriesData)
* 측정 시작/종료 시각

### 운동

운동 데이터에는 운동 종류와 세션 정보가 포함되며, 데이터가 존재하는 경우 GPS 경로와 운동 로그도 함께 저장합니다.

### `_export_summary.json`

각 실행의 전체 결과를 확인하기 위한 파일입니다.

예:

```json
{
  "retrievedAt": "...",
  "sdkVersion": "samsung-health-data-api-1.1.0",
  "requestedDays": 30,
  "requestedDataTypes": [
    "steps",
    "sleep",
    "heart_rate",
    "exercise",
    "blood_pressure"
  ],
  "requestedRange": {
    "...": "..."
  },
  "results": [
    {
      "dataType": "steps",
      "count": 29,
      "file": "steps.json",
      "publicPath": "Download/SHealthPoC/steps.json",
      "skippedReason": null,
      "error": null
    }
  ]
}
```

이를 통해 어떤 데이터를 요청했고, 각각 몇 건이 조회됐으며, 권한이나 오류 문제가 있었는지 한 번에 확인할 수 있습니다.

---

## 12. 프로젝트 구조

```text
samsung-health-data/
├── app/
│   ├── build.gradle.kts          libs/*.aar 연결, minSdk 29, Java 17
│   ├── proguard-rules.pro
│   ├── libs/
│   │   └── samsung-health-data-api-1.1.0.aar
│   ├── tool/
│   │   └── DataViewer_1.1.0.apk
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/
│       │   ├── layout/activity_main.xml
│       │   └── values/strings.xml
│       └── java/com/example/shealthpoc/
│           ├── MainActivity.kt
│           ├── HealthDataChoice.kt
│           ├── HealthDataExporter.kt
│           ├── SdkJson.kt
│           ├── JsonSupport.kt
│           ├── HealthRecords.kt
│           └── DownloadsExporter.kt
│
├── data/
├── docs/
│   └── sdk-api-notes.md
│
├── scripts/
│   ├── pull-data.ps1
│   └── pull-data.sh
│
├── gradle/
│   └── libs.versions.toml        AGP / Kotlin / Gson 버전
├── gradlew
├── gradlew.bat
├── build.gradle.kts
├── settings.gradle.kts
├── local.properties              sdk.dir
└── README.md
```

### 주요 파일

**`MainActivity.kt`**
앱 화면을 담당합니다. 데이터 선택 → 권한 확인 → 조회 → 저장 흐름을 시작합니다.

**`HealthDataChoice.kt`**
앱에서 선택할 수 있는 데이터 종류와 각 데이터의 조회 방식을 정의합니다.

**`HealthDataExporter.kt`**
Samsung Health Data SDK를 호출하여 실제 데이터를 조회합니다. `readData()`와 `aggregateData()`를 처리하고, 여러 페이지의 데이터가 존재하는 경우 끝까지 조회합니다.

**`SdkJson.kt`**
Samsung Health SDK에서 받은 값을 JSON으로 변환합니다.

**`DownloadsExporter.kt`**
생성한 JSON을 휴대폰의 `Download/SHealthPoC` 폴더에 저장합니다.

**`HealthRecords.kt`**
JSON 파일의 공통 구조와 조회 결과 정보를 정의합니다.

**`app/build.gradle.kts`**
`libs/*.aar`을 의존성으로 연결하고 `minSdk 29` / Java 17을 지정합니다. AAR이 없으면 빌드를 중단하고 안내 메시지를 출력합니다.

**`docs/sdk-api-notes.md`**
SDK API 검증 내용과 DataTypes별 세부 정보를 정리한 개발 문서입니다.

---

## 13. 데이터 처리 방식

### readData

수면, 심박수, 운동, 혈압 등 개별 Health Data Point가 존재하는 데이터는 `readData()`로 조회합니다.

조회 기간과 정렬 조건을 지정하고, `DataResponse.pageToken`이 존재하는 동안 다음 페이지를 계속 요청합니다.

따라서 데이터가 많은 기간을 조회해도 첫 페이지만 저장되는 것을 방지합니다.

무한 루프를 막기 위한 상한으로 한 데이터당 최대 1000페이지까지 조회하며, 상한에 걸리면 Logcat에 경고를 남깁니다.

### aggregateData

SDK에서 원본 레코드를 제공하지 않는 데이터는 `aggregateData()`를 사용합니다. 해당 데이터는 다음 **8종 전부**입니다.

* 걸음 수
* 활동 요약
* 걸음 수 목표
* 수면 목표
* 활동 시간 목표
* 활동 칼로리 목표
* 물 섭취 목표
* 영양 목표

예를 들어 걸음 수는 `TOTAL` 연산을 사용하여 현재 일 단위로 집계합니다.

### JSON 변환

SDK 객체 전체를 리플렉션으로 그대로 저장하지 않습니다.

SDK가 공개한 `Field`와 entry 객체의 getter를 이용해 필요한 값을 명시적으로 JSON으로 변환합니다.

현재 다음과 같은 SDK entry 객체를 명시적으로 처리합니다.

* SleepSession
* SleepStage
* HeartRate
* OxygenSaturation
* SkinTemperature
* BloodGlucose
* ExerciseSession
* ExerciseLog
* ExerciseLocation
* SwimmingLog
* SwimmingInterval

SDK에서 새로운 형태의 객체가 반환되어 매핑되지 않은 경우 Logcat에 경고를 남깁니다.

---

## 14. 데이터 타입 추가 및 변경

### 새로운 데이터 타입 추가

`HealthDataChoice`에 항목을 추가하고 `HealthDataExporter`에서 해당 타입의 Field를 정의합니다.

```text
HealthDataChoice
        ↓
HealthDataExporter.fieldsOf()
        ↓
JSON export
```

위 흐름은 `readData()`로 조회하는 데이터 기준입니다.

집계(`aggregateData()`) 전용 데이터를 추가할 때는 `fieldsOf()` 대신 `HealthDataExporter.aggregateRecords()`에 해당 타입의 연산 분기를 추가해야 합니다.

체크박스는 `HealthDataChoice`를 기준으로 생성되므로 데이터 타입을 추가할 때 레이아웃을 직접 수정할 필요는 없습니다.

### 걸음 수를 시간 단위로 조회

현재 걸음 수는 일 단위로 집계합니다.

시간 단위가 필요한 경우 `HealthDataExporter.aggregateRecords()`의:

```text
LocalTimeGroupUnit.DAILY
```

를:

```text
LocalTimeGroupUnit.HOURLY
```

로 변경합니다.

### SDK entry 객체 필드 변경

`SdkJson.kt`에서 해당 객체의 JSON 변환 부분을 수정합니다.

---

## 15. 자주 발생하는 문제

| 증상                                      | 확인할 내용                              |
| --------------------------------------- | ----------------------------------- |
| `AuthorizationException`                | Samsung Health 개발자 모드가 활성화되어 있는지 확인 |
| `ERR_INVALID_PLATFORM_SIGNATURE`        | Samsung Health 개발자 모드 확인            |
| `ResolvablePlatformException`           | Samsung Health 설치 여부 및 버전 확인        |
| `permission not granted`                | Samsung Health에서 해당 데이터 접근 권한 허용    |
| `count: 0`                              | 조회 기간에 실제 데이터가 있는지 확인               |
| 에뮬레이터에서 실행되지 않음                         | 실기기 사용                              |
| `Samsung Health Data SDK AAR not found` | `app/libs/`에 SDK AAR이 있는지 확인        |

`count: 0`이 발생하면 SDK 오류라고 단정하지 말고 Samsung Health 또는 `DataViewer_1.1.0.apk`에서 해당 데이터가 실제로 존재하는지 먼저 확인합니다.

---

## 16. SDK 구현 및 검증 기준

이 프로젝트의 Samsung Health 연동 코드는 **Samsung Health Data SDK 1.1.0**을 기준으로 작성했습니다.

SDK API의 클래스, 메서드, 필드 및 데이터 타입은 다음을 기준으로 확인했습니다.

* Samsung Health Data SDK 1.1.0 AAR
* AAR 바이트코드
* Samsung 공식 Health Diary 1.1.0 샘플 앱
* Samsung Health Data SDK 공식 문서

SDK API별 상세 검증 내용과 DataTypes capability matrix는 다음 문서에 정리되어 있습니다.

```text
docs/sdk-api-notes.md
```

README는 프로젝트의 사용 및 전체 구조를 설명하고, SDK 내부 API에 대한 세부 검증 내용은 `sdk-api-notes.md`에서 관리합니다.
