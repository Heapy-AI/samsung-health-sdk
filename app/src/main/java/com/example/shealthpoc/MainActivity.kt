package com.example.shealthpoc

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.error.ErrorCode
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.permission.Permission
import kotlinx.coroutines.launch

/**
 * Minimal PoC entry point.
 *
 * The only UI is the pick-what-to-export controls (data type checkboxes + day count) plus a
 * status text. There is no data-browsing screen: the result goes to JSON files and Logcat.
 */
class MainActivity : ComponentActivity() {

    private lateinit var statusView: TextView
    private lateinit var runButton: Button
    private lateinit var daysInput: EditText
    private lateinit var typeBoxes: Map<HealthDataChoice, CheckBox>

    private var store: HealthDataStore? = null
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.tv_status)
        runButton = findViewById(R.id.btn_run)
        daysInput = findViewById(R.id.et_days)

        // One CheckBox per HealthDataChoice - adding a data type needs no layout change.
        val container: LinearLayout = findViewById(R.id.types_container)
        typeBoxes = HealthDataChoice.entries.associateWith { choice ->
            CheckBox(this).apply {
                text = choice.label
                textSize = 14f
                container.addView(this)
            }
        }

        restoreSettings()
        runButton.setOnClickListener { start() }
        findViewById<Button>(R.id.btn_select_all).setOnClickListener {
            typeBoxes.values.forEach { it.isChecked = true }
        }
        findViewById<Button>(R.id.btn_select_none).setOnClickListener {
            typeBoxes.values.forEach { it.isChecked = false }
        }

        report(
            "== 환경 ==\n" +
                "앱: $packageName\n" +
                "${samsungHealthInfo()}\n" +
                "Android SDK: ${android.os.Build.VERSION.SDK_INT} / ${android.os.Build.MODEL}\n\n" +
                "조회할 데이터와 기간을 고른 뒤 '${getString(R.string.action_run)}' 을 누르세요."
        )
    }

    // ------------------------------------------------------------- 설정 읽기/저장

    private fun selectedChoices(): Set<HealthDataChoice> =
        typeBoxes.filterValues { it.isChecked }.keys

    /** @return day count, or null if the input is not a usable number */
    private fun selectedDays(): Long? =
        daysInput.text.toString().trim().toLongOrNull()?.takeIf { it in 1..365 }

    private fun restoreSettings() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        typeBoxes.forEach { (choice, box) ->
            box.isChecked = prefs.getBoolean(
                KEY_TYPE_PREFIX + choice.name,
                choice in HealthDataChoice.DEFAULT_SELECTION,
            )
        }
        daysInput.setText(prefs.getLong(KEY_DAYS, DEFAULT_DAYS).toString())
    }

    private fun saveSettings(days: Long, choices: Set<HealthDataChoice>) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            HealthDataChoice.entries.forEach { putBoolean(KEY_TYPE_PREFIX + it.name, it in choices) }
            putLong(KEY_DAYS, days)
        }.apply()
    }

    // ------------------------------------------------------------------- 실행

    private fun start() {
        if (running) return

        val choices = selectedChoices()
        if (choices.isEmpty()) {
            report(getString(R.string.error_no_type))
            return
        }
        val days = selectedDays()
        if (days == null) {
            report(getString(R.string.error_days_range))
            return
        }
        saveSettings(days, choices)

        running = true
        setControlsEnabled(false)
        lifecycleScope.launch {
            try {
                runFlow(days, choices)
            } finally {
                running = false
                setControlsEnabled(true)
            }
        }
    }

    private fun setControlsEnabled(enabled: Boolean) {
        runButton.isEnabled = enabled
        daysInput.isEnabled = enabled
        typeBoxes.values.forEach { it.isEnabled = enabled }
    }

    private suspend fun runFlow(days: Long, choices: Set<HealthDataChoice>) {
        val required: Set<Permission> = HealthDataChoice.permissionsOf(choices)

        val log = StringBuilder()
        log.appendLine("== 요청 ==")
        log.appendLine("데이터: ${choices.joinToString { it.fileBaseName }}")
        log.appendLine("기간: 최근 ${days}일 (오늘 포함)")
        log.appendLine()
        log.appendLine("Samsung Health 연결 중...")
        report(log.toString())

        try {
            val healthStore = store ?: HealthDataService.getStore(applicationContext).also { store = it }

            // 1) already-granted consents (only for the ticked types)
            log.appendLine("getGrantedPermissions() 호출...")
            report(log.toString())
            var granted = healthStore.getGrantedPermissions(required)
            log.appendLine("  승인됨: ${granted.size}/${required.size}")
            report(log.toString())
            Log.i(TAG, "granted (before request) = ${granted.size}/${required.size}")

            // 2) ask for the missing ones - opens the Samsung Health consent screen
            if (!granted.containsAll(required)) {
                log.appendLine("requestPermissions() 호출 - Samsung Health 동의 화면 대기...")
                report(log.toString())
                healthStore.requestPermissions(required, this@MainActivity)
                granted = healthStore.getGrantedPermissions(required)
                log.appendLine("  승인됨: ${granted.size}/${required.size}")
                report(log.toString())
            }
            Log.i(TAG, "granted (after request) = ${granted.size}/${required.size}")

            if (granted.isEmpty()) {
                log.appendLine()
                log.appendLine("권한이 하나도 승인되지 않았습니다.")
                log.appendLine("Samsung Health > 설정 > 권한 에서 이 앱을 허용한 뒤 다시 실행하세요.")
                report(log.toString())
                return
            }

            // 3) read + 4) serialize + 5) save
            log.appendLine()
            log.appendLine("데이터 조회 중...")
            report(log.toString())

            val exporter = HealthDataExporter(applicationContext, healthStore)
            val results = exporter.exportAll(days = days, selected = choices, grantedPermissions = granted)

            // 6) Logcat + on-screen summary
            log.appendLine()
            log.appendLine("== 저장 위치 ==")
            log.appendLine(exporter.outputDir().absolutePath)
            log.appendLine()
            log.appendLine("== 결과 ==")
            results.forEach { r ->
                val detail = r.error ?: r.skippedReason ?: "${r.count} records -> ${r.file?.name}"
                log.appendLine("- ${r.dataType}: $detail")
                Log.i(TAG, "RESULT ${r.dataType}: $detail")
            }

            val publicPaths = exporter.publicPaths()
            log.appendLine()
            log.appendLine("== 휴대폰에서 바로 확인 (USB 불필요) ==")
            if (publicPaths.isEmpty()) {
                log.appendLine("공유 저장소 저장 실패 - Logcat 의 'Downloads export failed' 확인")
            } else {
                publicPaths.forEach { path ->
                    log.appendLine(path)
                    Log.i(TAG, "PUBLIC $path")
                }
                log.appendLine()
                log.appendLine("내 파일(My Files) > 내장 저장공간 > Download > ${DownloadsExporter.FOLDER_NAME}")
            }
            report(log.toString())
        } catch (e: HealthDataException) {
            Log.e(TAG, "HealthDataException", e)
            log.appendLine()
            log.appendLine("== 실패 (HealthDataException) ==")
            log.appendLine("종류: ${e.javaClass.simpleName}")
            log.appendLine("errorCode: ${e.errorCode} (${errorCodeName(e.errorCode)})")
            log.appendLine("message: ${e.errorMessage}")
            log.appendLine()
            log.appendLine(hintFor(e))
            if (e is ResolvablePlatformException && e.hasResolution) {
                log.appendLine()
                log.appendLine("Samsung Health 안내 화면으로 이동합니다...")
                report(log.toString())
                e.resolve(this@MainActivity)
                return
            }
            report(log.toString())
        } catch (t: Throwable) {
            Log.e(TAG, "unexpected failure", t)
            log.appendLine()
            log.appendLine("== 실패 (${t.javaClass.simpleName}) ==")
            log.appendLine("message: ${t.message}")
            log.appendLine("cause: ${t.cause}")
            report(log.toString())
        }
    }

    // ------------------------------------------------------------------- 진단

    private fun samsungHealthInfo(): String = try {
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageInfo(SAMSUNG_HEALTH_PACKAGE, 0)
        "Samsung Health: ${info.versionName} (설치됨)"
    } catch (e: PackageManager.NameNotFoundException) {
        "Samsung Health: 조회 불가 (미설치이거나 비활성화)"
    }

    private fun errorCodeName(code: Int?): String = when (code) {
        null -> "없음"
        ErrorCode.ERR_PLATFORM_NOT_INSTALLED -> "ERR_PLATFORM_NOT_INSTALLED"
        ErrorCode.ERR_OLD_VERSION_PLATFORM -> "ERR_OLD_VERSION_PLATFORM"
        ErrorCode.ERR_PLATFORM_DISABLED -> "ERR_PLATFORM_DISABLED"
        ErrorCode.ERR_PLATFORM_NOT_INITIALIZED -> "ERR_PLATFORM_NOT_INITIALIZED"
        ErrorCode.ERR_INVALID_PLATFORM_SIGNATURE -> "ERR_INVALID_PLATFORM_SIGNATURE"
        ErrorCode.ERR_INVALID_CALLER -> "ERR_INVALID_CALLER"
        ErrorCode.ERR_NO_USER_PERMISSION -> "ERR_NO_USER_PERMISSION"
        ErrorCode.ERR_ACCESS_CONTROL -> "ERR_ACCESS_CONTROL"
        ErrorCode.ERR_CHILD_ACCOUNT_ACCESS -> "ERR_CHILD_ACCOUNT_ACCESS"
        ErrorCode.ERR_UNSUPPORTED_OPERATION -> "ERR_UNSUPPORTED_OPERATION"
        ErrorCode.ERR_INVALID_INPUT -> "ERR_INVALID_INPUT"
        ErrorCode.ERR_INVALID_UID -> "ERR_INVALID_UID"
        ErrorCode.ERR_CONNECTION_FAIL -> "ERR_CONNECTION_FAIL"
        ErrorCode.ERR_CONNECTION_TIMEOUT -> "ERR_CONNECTION_TIMEOUT"
        ErrorCode.ERR_PLATFORM_DISCONNECTED -> "ERR_PLATFORM_DISCONNECTED"
        ErrorCode.ERR_INTERNAL_ERROR -> "ERR_INTERNAL_ERROR"
        ErrorCode.ERR_DB_ERROR -> "ERR_DB_ERROR"
        ErrorCode.ERR_INTERRUPTED -> "ERR_INTERRUPTED"
        else -> "기타"
    }

    private fun hintFor(e: HealthDataException): String = when (e.errorCode) {
        ErrorCode.ERR_INVALID_PLATFORM_SIGNATURE, ErrorCode.ERR_INVALID_CALLER ->
            "→ Samsung Health 개발자 모드가 꺼져 있습니다.\n" +
                "   Samsung Health > ⋮ > 설정 > Samsung Health 정보 >\n" +
                "   버전 줄을 10회 이상 빠르게 탭 > 개발자 모드 켜기"
        ErrorCode.ERR_PLATFORM_NOT_INSTALLED ->
            "→ Samsung Health 앱을 설치하세요."
        ErrorCode.ERR_OLD_VERSION_PLATFORM ->
            "→ Samsung Health 를 6.30.2 이상으로 업데이트하세요."
        ErrorCode.ERR_PLATFORM_DISABLED ->
            "→ 설정 > 앱 에서 Samsung Health 를 활성화하세요."
        ErrorCode.ERR_PLATFORM_NOT_INITIALIZED ->
            "→ Samsung Health 를 한 번 실행해 초기 설정을 마치세요."
        ErrorCode.ERR_NO_USER_PERMISSION ->
            "→ Samsung Health > 설정 > 권한 에서 이 앱의 데이터 접근을 허용하세요."
        else ->
            "→ 개발자 모드 활성화 여부와 Samsung Health 버전(6.30.2+)을 먼저 확인하세요."
    }

    private fun report(message: String) {
        Log.i(TAG, message.substringAfterLast("\n\n").replace("\n", " | "))
        statusView.text = message
    }

    companion object {
        private const val TAG = HealthDataExporter.TAG
        private const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"

        private const val PREFS = "poc_settings"
        private const val KEY_DAYS = "days"
        private const val KEY_TYPE_PREFIX = "type_"
        private const val DEFAULT_DAYS = 30L
    }
}
