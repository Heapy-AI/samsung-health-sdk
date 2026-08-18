package com.example.shealthpoc

import java.time.LocalDateTime

/**
 * Envelope / summary DTOs.
 *
 * The per-record JSON is built directly as Gson JsonObject in [HealthDataExporter], from the
 * SDK `Field` constants and the explicit converters in [SdkJson] - so record shape always
 * follows the real SDK surface instead of a hand-maintained mirror of it.
 */

data class RequestedRange(
    val startLocalDateTime: LocalDateTime,
    val endLocalDateTime: LocalDateTime,
)

data class ExportSummaryEntry(
    val dataType: String,
    val count: Int,
    val file: String?,
    /** Shared-storage path, e.g. "Download/SHealthPoC/steps.json" */
    val publicPath: String?,
    val skippedReason: String?,
    val error: String?,
)

data class ExportSummary(
    val retrievedAt: String,
    val sdkVersion: String,
    /** Days back requested on screen, including today. */
    val requestedDays: Long,
    /** Data types ticked on screen for this run. */
    val requestedDataTypes: List<String>,
    val requestedRange: RequestedRange,
    val results: List<ExportSummaryEntry>,
)
