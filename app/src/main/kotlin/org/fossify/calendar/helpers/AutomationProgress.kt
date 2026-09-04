package org.fossify.calendar.helpers

import android.content.Context
import android.content.Intent
import android.util.Log
import org.fossify.calendar.R

/** The one log tag the whole automation surface writes under, so `logcat -s` catches both doors. */
const val AUTOMATION_LOG_TAG = "YotehyoStateExport"

/**
 * The one progress channel both automation doors report through.
 *
 * Contract §3: **real counts, never a percentage** — "Events 1234/8942", not "47%". Throttled to one
 * broadcast per [PROGRESS_THROTTLE_MS], with an unthrottled final one at completion, and every
 * broadcast doubling as the heartbeat that tells 自由作業盤 this app is still alive.
 *
 * It lives here rather than inside StateExportReceiver because contract v2 added a second caller —
 * the data door's [org.fossify.calendar.automation.AutomationDataService] — and §2a is explicit that
 * an app which already has a §1 progress sender must **parameterise that one on the correlation id**
 * rather than write a second. Two implementations of the same watchdog drift, and the one that drifts
 * is always the one nobody is looking at.
 *
 * @param correlationId the id to echo back verbatim: "reply_id" for the §1 broadcast door, the job id
 *   for the §2a data door.
 * @param asJob when true the id is also sent as "job_id", which is how a data-door caller correlates
 *   progress with the job it was handed. Harmless to a §1 reader, so the two doors stay one shape.
 */
class AutomationProgress(
    private val context: Context,
    private val progressAction: String,
    private val replyPackage: String,
    private val correlationId: String,
    private val asJob: Boolean = false,
) {
    private val appLabel = context.getString(R.string.app_launcher_name)
    private val unitCategory = context.getString(R.string.state_progress_unit_category)
    private var lastSent = 0L

    /**
     * Whether progress can be delivered at all.
     *
     * **Both** halves are needed, and that is not obvious: since API 26 an IMPLICIT broadcast is not
     * delivered to a manifest-declared receiver at all, so a progress action without a package to
     * `setPackage` it to is not weak progress — it is none. The export would run, finish and report its
     * terminal reply correctly while every progress line vanished silently, which is the hardest kind
     * of failure to notice because everything visible still works (a sister fork shipped exactly that;
     * 応用管理 caught it, 2026-09-04). So we send only when we can actually address the caller.
     */
    private val canSend = progressAction.isNotEmpty() && replyPackage.isNotEmpty()

    /** Throttled: pass this straight to [SettingsTransfer.export]. */
    val reporter: ProgressReporter = { current, total, unit, text ->
        val now = System.currentTimeMillis()
        if (canSend && now - lastSent >= PROGRESS_THROTTLE_MS) {
            lastSent = now
            send(current, total, unit, text)
        }
    }

    /** The mandatory final message, unthrottled — [categories] is the count actually exported. */
    fun final(categories: Long) {
        if (canSend) {
            send(categories, categories, unitCategory, "$unitCategory $categories/$categories")
        }
    }

    private fun send(current: Long, total: Long, unit: String, text: String) {
        try {
            context.sendBroadcast(
                Intent(progressAction)
                    .setPackage(replyPackage)
                    .putExtra(EXTRA_REPLY_ID, correlationId)
                    .apply { if (asJob) putExtra(EXTRA_JOB_ID, correlationId) }
                    .putExtra(EXTRA_PROGRESS_APP, appLabel)
                    .putExtra(EXTRA_PROGRESS_TEXT, text)
                    .putExtra(EXTRA_PROGRESS_CURRENT, current)
                    .putExtra(EXTRA_PROGRESS_TOTAL, total)
                    .putExtra(EXTRA_PROGRESS_UNIT, unit)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            )
        } catch (e: Exception) {
            Log.w(AUTOMATION_LOG_TAG, "progress broadcast failed: $e")
        }
    }
}
