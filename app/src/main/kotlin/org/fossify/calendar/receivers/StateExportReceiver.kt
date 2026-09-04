package org.fossify.calendar.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.fossify.calendar.extensions.config
import org.fossify.calendar.helpers.ACTION_CANCEL_EXPORT
import org.fossify.calendar.helpers.ACTION_EXPORT_STATE
import org.fossify.calendar.helpers.ACTION_LIST_CATEGORIES
import org.fossify.calendar.helpers.AUTOMATION_LOG_TAG
import org.fossify.calendar.helpers.AutomationProgress
import org.fossify.calendar.helpers.EXTRA_AUTOMATION_TOKEN
import org.fossify.calendar.helpers.EXTRA_EXPORT_ITEMS
import org.fossify.calendar.helpers.EXTRA_EXPORT_PATH
import org.fossify.calendar.helpers.EXTRA_PROGRESS_ACTION
import org.fossify.calendar.helpers.EXTRA_REPLY_ACTION
import org.fossify.calendar.helpers.EXTRA_REPLY_ID
import org.fossify.calendar.helpers.EXTRA_REPLY_PACKAGE
import org.fossify.calendar.helpers.EXTRA_REPLY_RESULT
import org.fossify.calendar.helpers.PROGRESS_THROTTLE_MS
import org.fossify.calendar.helpers.SettingsTransfer
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus

/**
 * The 保存復元 state-export contract, for 白い熊 自由作業盤's one-run backup of every sister app.
 *
 * Three exported actions, gated by [org.fossify.calendar.helpers.Config.automationRefusal] — the master
 * switch, which now ships ON, plus a token that is only demanded when 白い熊 has asked for one
 * (contract v2, 2026-09-04). A token sent to this app while it is not asking for one is IGNORED, never
 * refused. Everything that moves data through a CALLER-SUPPLIED descriptor lives behind
 * [org.fossify.calendar.automation.AutomationProvider] instead, which can identify who is calling;
 * this receiver only ever writes where it was told to and reports what it did, which is why it is safe
 * for it to be the unauthenticated half of the surface. There is deliberately no import action here:
 * an import overwrites the app's data, and this receiver is exported with no permission.
 *
 *  - [ACTION_LIST_CATEGORIES] — instant; replies "OK:" plus one `id<TAB>label<TAB>parent<TAB>on|off`
 *    line per selectable item. A sub-option names its parent in the third field and follows its
 *    parent's line ("ui_theme.fonts" under "ui_theme"), so the caller can render it indented and make
 *    it follow the parent's toggle; a top-level item leaves that field empty. The fourth field is this
 *    app STATING whether the item starts ticked rather than leaving the picker to guess.
 *  - [ACTION_EXPORT_STATE] — runs the same category ZIP export as the Export/Import page, headlessly
 *    (no Activity, no interaction), and replies with the written path and its real size. Extras:
 *    "token", optional "path" (an absolute directory that OVERRIDES the configured export directory),
 *    optional "items" (comma-separated category ids; absent = everything), optional
 *    "progress_action", plus "reply_action"/"reply_package"/"reply_id".
 *  - [ACTION_CANCEL_EXPORT] — stops the export in flight; extras "token" and an optional "reply_id"
 *    (absent = whatever is running, unambiguous because two at once are forbidden). Fire-and-forget:
 *    it is never answered, and sending it with nothing running is a silent no-op. The terminal
 *    "ERROR:cancelled" belongs to the ORIGINAL request and is sent once the run has actually unwound
 *    and its half-written ZIP is gone.
 *
 * Directory precedence: the "path" extra → the app's configured export directory → ERROR:no-directory.
 * One request writes exactly ONE ZIP — every component (events, settings, theme, fonts, widgets,
 * calendar categories) is an entry inside it, so 白い熊 restores this app by picking that one file.
 *
 * The reply is a plain broadcast carrying "reply_id" + "result" — the only channel that works on this
 * EMUI (verified on 白い熊's Mate XT, 2026-07-23): the ordered-broadcast result is severed between
 * third-party apps, and any Binder-bearing extra (ResultReceiver, PendingIntent, Messenger) gets the
 * broadcast dropped outright. setResultData is set too, because it is correct AOSP behaviour and costs
 * nothing, but it is never the only reply. [Intent.FLAG_INCLUDE_STOPPED_PACKAGES] matters: without it a
 * backgrounded caller never hears us. Exactly one terminal reply per request, guarded by an
 * [AtomicBoolean] so an async success and a synchronous error can never both fire.
 *
 * Progress is reported as real counts, never a percentage — "Events 1234/8942" — throttled to one
 * broadcast per [PROGRESS_THROTTLE_MS], with an unthrottled final one at completion.
 */
class StateExportReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = AUTOMATION_LOG_TAG
        private const val KILO = 1024.0

        /**
         * The export in flight, if any. A cancel arrives on a *different* receiver instance — Android
         * builds a fresh one per broadcast — so the only way to reach the running export is a
         * process-wide handle. The contract forbids two exports at once, which is exactly what makes
         * one slot enough, and what makes an absent "reply_id" on a cancel unambiguous.
         */
        private val running = AtomicReference<RunningExport?>(null)
    }

    /** What a parsed request turned out to be: already answerable, or an export to run. */
    private sealed class Request {
        class Done(val result: String) : Request()
        class Export(val cats: Set<SettingsTransfer.Category>, val path: String) : Request()
    }

    /**
     * A cancellable export: the request it answers, and whether a stop has been asked for. [cancelled]
     * is written by whichever thread the cancel broadcast lands on and read by the export thread
     * between entries, hence @Volatile — that flag is the whole stop mechanism. Nothing here kills a
     * thread or a process; the export unwinds itself at the next boundary.
     */
    private class RunningExport(val replyId: String) {
        @Volatile
        var cancelled = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_EXPORT_STATE && action != ACTION_LIST_CATEGORIES && action != ACTION_CANCEL_EXPORT) {
            return
        }

        if (action == ACTION_CANCEL_EXPORT) {
            cancel(context.applicationContext, intent)
            return
        }

        // goAsync() holds the broadcast open until finish(); the guard makes finishWith idempotent so
        // the async success path and any synchronous error path can't double-finish (and a dropped path
        // can't leave the caller waiting forever).
        val pending = goAsync()
        val finished = AtomicBoolean(false)
        val appContext = context.applicationContext
        val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)?.trim().orEmpty()
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)?.trim().orEmpty()
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty()
        val progressAction = intent.getStringExtra(EXTRA_PROGRESS_ACTION)?.trim().orEmpty()

        fun finishWith(result: String) {
            if (!finished.compareAndSet(false, true)) return
            Log.i(TAG, "result → $result")
            if (replyAction.isNotEmpty() && replyId.isNotEmpty()) {
                try {
                    appContext.sendBroadcast(
                        Intent(replyAction)
                            .setPackage(replyPackage.ifEmpty { null })
                            .putExtra(EXTRA_REPLY_ID, replyId)
                            .putExtra(EXTRA_REPLY_RESULT, result)
                            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    )
                    Log.i(TAG, "reply broadcast sent → $replyAction ($replyPackage, id=$replyId)")
                } catch (e: Exception) {
                    Log.w(TAG, "reply broadcast failed: $e")
                }
            }
            pending.setResultData(result)
            pending.finish()
        }

        val request = try {
            parse(appContext, intent, action)
        } catch (e: Exception) {
            Request.Done("ERROR:${reason(e)}")
        }

        when (request) {
            is Request.Done -> finishWith(request.result)
            is Request.Export -> {
                val progress = AutomationProgress(appContext, progressAction, replyPackage, replyId)
                val run = RunningExport(replyId)
                running.set(run)
                ensureBackgroundThread {
                    try {
                        finishWith(export(appContext, request.cats, request.path, progress, run))
                    } finally {
                        // Only ever clear our own run, so a cancel that lands after this one ended is
                        // the no-op it should be rather than a stop aimed at somebody else.
                        running.compareAndSet(run, null)
                    }
                }
            }
        }
    }

    /**
     * [ACTION_CANCEL_EXPORT]: raise the stop flag on the export in flight, and answer nothing. Safe to
     * send at any time — with nothing running, or with the export already finished, or naming an id
     * that is not the running one, this is a silent no-op: not an error, not a reply, not a crash. The
     * "ERROR:cancelled" terminal reply is the export thread's to send, for the ORIGINAL request, after
     * it has unwound and deleted its partial file.
     */
    private fun cancel(context: Context, intent: Intent) {
        val config = context.config
        val token = intent.getStringExtra(EXTRA_AUTOMATION_TOKEN)
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty()
        // The same one gate the export uses — silent here, because a cancel is fire-and-forget and
        // there is nothing to report a refusal to.
        config.automationRefusal(token)?.let {
            Log.i(TAG, "cancel refused: $it")
            return
        }

        val run = running.get()
        if (run == null || (replyId.isNotEmpty() && replyId != run.replyId)) {
            Log.i(TAG, "cancel: nothing to stop (id=${replyId.ifEmpty { "-" }})")
            return
        }

        Log.i(TAG, "cancel: stopping the export for id=${run.replyId}")
        run.cancelled = true
    }

    /**
     * Decide the request without doing any work: the gate first (the switch and the token report
     * distinctly, since they debug differently), then the instant category list, then the export's own
     * validation — so a malformed request is answered before anything is written.
     */
    private fun parse(context: Context, intent: Intent, action: String?): Request {
        val config = context.config
        val token = intent.getStringExtra(EXTRA_AUTOMATION_TOKEN)
        val itemsRaw = intent.getStringExtra(EXTRA_EXPORT_ITEMS)?.trim().orEmpty()
        val path = intent.getStringExtra(EXTRA_EXPORT_PATH)?.trim().orEmpty()
        val cats = parseItems(itemsRaw)
        Log.i(
            TAG,
            "received $action: enabled=${config.automationEnabled}, " +
                "requireToken=${config.automationRequireToken}, tokenLen=${token?.length ?: 0}, " +
                "items=$itemsRaw, path=$path"
        )

        config.automationRefusal(token)?.let { return Request.Done(it) }

        return when {
            action == ACTION_LIST_CATEGORIES -> Request.Done(categoryList(context))
            cats == null -> Request.Done("ERROR:unknown category in items: $itemsRaw")
            path.isNotEmpty() && !path.startsWith("/") ->
                Request.Done("ERROR:$EXTRA_EXPORT_PATH must be an absolute directory")

            else -> Request.Export(cats, path)
        }
    }

    /**
     * "OK:" plus one `id<TAB>label<TAB>parent<TAB>on|off` line per selectable item — the ids are exactly
     * the ones "items" accepts. A sub-option names its parent in the third field and follows its
     * parent's line, so the caller can render it indented under the parent; a top-level item leaves
     * that field empty rather than dropping it, because the fourth field is positional. That fourth
     * field is whether the item starts ticked — our answer, not the picker's guess.
     */
    private fun categoryList(context: Context): String =
        SettingsTransfer.Category.listed.joinToString(separator = "\n", prefix = "OK:") {
            val parent = it.parentId.orEmpty()
            "${it.id}\t${context.getString(it.labelRes)}\t$parent\t${if (it.defaultOn) "on" else "off"}"
        }

    /**
     * The requested items, or null when [itemsRaw] names an id we do not export. Absent or empty means
     * everything. A parent id on its own selects that category's own data only — its parts are separate
     * ids, so they are included only when asked for.
     */
    private fun parseItems(itemsRaw: String): Set<SettingsTransfer.Category>? {
        val ids = itemsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (ids.isEmpty()) return SettingsTransfer.Category.entries.toSet()
        val cats = ids.mapNotNull { SettingsTransfer.Category.byId(it) }.toSet()
        return cats.takeIf { it.size == ids.distinct().size }
    }

    /** Runs on a background thread; returns the single result line and never throws. */
    private fun export(
        context: Context,
        cats: Set<SettingsTransfer.Category>,
        path: String,
        progress: AutomationProgress,
        run: RunningExport,
    ): String {
        val target = try {
            SettingsTransfer.headlessTarget(context, path) ?: return "ERROR:no-directory"
        } catch (e: Exception) {
            return storageError(path, e)
        }

        var completed = false
        return try {
            // The count is a fallback for a destination we cannot stat; it is final once export() returns,
            // which is after the ZIP's central directory has been flushed.
            val counting = CountingOutputStream(target.open())
            counting.use { SettingsTransfer.export(context, cats, it, progress.reporter) { run.cancelled } }
            val bytes = target.size().takeIf { it > 0 } ?: counting.count
            progress.final(cats.size.toLong())
            completed = true
            "OK:${target.displayPath}|$bytes|${humanSize(bytes)}|${cats.size} categories"
        } catch (e: SettingsTransfer.Cancelled) {
            Log.i(TAG, "export unwound: ${e.message}")
            "ERROR:cancelled"
        } catch (e: Exception) {
            storageError(path, e)
        } finally {
            // A run that never reached its OK line leaves nothing behind. This is the point of the
            // cancel action: the backup directory is exactly as it was found — no short archive for
            // 白い熊 to mistake for a backup, and none for "last export" to pick up. Failures unwind the
            // same way, since a truncated ZIP is no more of a backup than a cancelled one.
            if (!completed) {
                runCatching { target.discard() }
            }
        }
    }

    // An absolute path we were told to write but cannot needs All-files access; name that specifically,
    // since it is the one failure 白い熊 fixes with a toggle rather than a code change.
    private fun storageError(path: String, e: Exception): String {
        val noAllFiles = isRPlus() && !Environment.isExternalStorageManager()
        return if (path.isNotEmpty() && noAllFiles) "ERROR:no-storage-access" else "ERROR:${reason(e)}"
    }

    private fun reason(e: Throwable): String =
        (e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName).replace('\n', ' ')

    /** Display size for the reply line — the caller cannot stat the file, so we compute both forms. */
    private fun humanSize(bytes: Long): String = when {
        bytes < KILO -> "$bytes B"
        bytes < KILO * KILO -> "%.1f KB".format(Locale.ROOT, bytes / KILO)
        bytes < KILO * KILO * KILO -> "%.1f MB".format(Locale.ROOT, bytes / (KILO * KILO))
        else -> "%.2f GB".format(Locale.ROOT, bytes / (KILO * KILO * KILO))
    }

    private class CountingOutputStream(private val out: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        override fun flush() = out.flush()

        override fun close() = out.close()
    }
}
