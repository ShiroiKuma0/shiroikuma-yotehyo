package org.fossify.calendar.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.fossify.calendar.R
import org.fossify.calendar.helpers.AUTOMATION_LOG_TAG
import org.fossify.calendar.helpers.AutomationProgress
import org.fossify.calendar.helpers.EXTRA_JOB_ID
import org.fossify.calendar.helpers.EXTRA_REPLY_ID
import org.fossify.calendar.helpers.EXTRA_REPLY_RESULT
import org.fossify.calendar.helpers.SettingsTransfer
import org.fossify.commons.helpers.ensureBackgroundThread

/**
 * Where a data export or import driven through [AutomationProvider] actually runs.
 *
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this can run for minutes on a calendar with thousands of entries.
 *
 * - **A binder call holds the caller.** 応用管理 is drawing a list; a multi-minute synchronous call
 *   would freeze its UI, report no progress and refuse cancellation.
 * - **A backgrounded app writing for minutes is frozen mid-stream on this phone**, which yields a
 *   truncated archive underneath a success reply — the worst possible failure, because it is
 *   indistinguishable from a good backup until the day it is restored.
 *
 * A partial wakelock is held for the same measured reason: on EMUI a foreground service alone is not
 * enough, and an export with the screen off otherwise stops part-way at no consistent point, with no
 * crash, no ANR and no log.
 *
 * ## The descriptor
 *
 * Already duplicated by [AutomationProvider] before it got here, because the original belongs to the
 * binder transaction and is closed the moment `call()` returns. This service owns the copy and closes
 * it in a `finally` — leaking one would hold the caller's file open indefinitely, and a caller cannot
 * checksum or encrypt a file that is still open.
 */
class AutomationDataService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("ReturnCount")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val jobId = intent?.getStringExtra(EXTRA_JOB)
        val importing = intent?.getBooleanExtra(EXTRA_IMPORTING, false) == true

        // FIRST, before any decision to bail out. Once startForegroundService() has been called the
        // platform requires a matching startForeground() WHATEVER this service then decides, and kills
        // the process with ForegroundServiceDidNotStartInTimeException otherwise. So the "nothing to do"
        // paths below — a null intent after a restart, or a caller retrying with a stale job id — must
        // come AFTER this call, or an ignorable request would instead kill the app it was aimed at.
        try {
            startForegroundCompat(importing)
        } catch (e: Exception) {
            // We never got to be foreground, so there is nothing to tear down — but the descriptor the
            // provider handed over is the caller's open file and is ours to close.
            Log.w(AUTOMATION_LOG_TAG, "startForeground refused for ${jobId ?: "-"}: $e")
            jobId?.let { id ->
                HANDOVER.remove(id)?.let { held -> runCatching { held.close() } }
                AutomationJobs.finish(id)
            }
            return stop(startId)
        }

        // Checked together so both smart-cast to non-null for the rest of the method.
        if (intent == null || jobId == null) return stopForegroundAnd(startId)
        // A stale or already-finished job id: nothing is held for it, so there is nothing to close and
        // nothing to answer. Silent, per the contract — a cancel or retry arriving after the work
        // completed is the normal race, not an error.
        val fd = HANDOVER.remove(jobId) ?: return stopForegroundAnd(startId)
        val replyAction = intent.getStringExtra(AutomationProvider.KEY_REPLY_ACTION).orEmpty()
        val replyPackage = intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE).orEmpty()
        val progressAction = intent.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION).orEmpty()
        val items = intent.getStringExtra(AutomationProvider.KEY_ITEMS)

        val replied = AtomicBoolean(false)
        fun reply(result: String) {
            // Exactly one terminal answer per job, whatever path got here — a synchronous failure and
            // an asynchronous success must never both fire. The same guard the broadcast contract has
            // carried since the first sister app.
            if (!replied.compareAndSet(false, true)) return
            Log.i(AUTOMATION_LOG_TAG, "data door ${if (importing) "import" else "export"} $jobId → $result")
            AutomationJobs.finish(jobId)
            if (replyAction.isEmpty() || replyPackage.isEmpty()) return
            try {
                sendBroadcast(
                    Intent(replyAction)
                        .setPackage(replyPackage)
                        // Without this a caller that has been backgrounded never hears the answer, and
                        // on a clean phone the caller may not have been launched at all.
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        .putExtra(EXTRA_JOB_ID, jobId)
                        // The same id under the §1 name too, so one reply reader serves both doors.
                        .putExtra(EXTRA_REPLY_ID, jobId)
                        .putExtra(EXTRA_REPLY_RESULT, result)
                )
            } catch (e: Exception) {
                Log.w(AUTOMATION_LOG_TAG, "data door reply failed: $e")
            }
        }

        ensureBackgroundThread {
            val wakeLock = acquireWakeLock()
            try {
                fd.use { open ->
                    if (importing) {
                        runImport(open, ::reply)
                    } else {
                        runExport(jobId, open, items, progressAction, replyPackage, ::reply)
                    }
                }
            } catch (t: Throwable) {
                reply("ERROR:${t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName}")
            } finally {
                // A path that somehow reached here without answering must still answer: the caller is
                // holding a slot open on a job id it was handed.
                reply("ERROR:ended without a result")
                runCatching { wakeLock?.release() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Write the one ZIP straight into the caller's descriptor.
     *
     * Counted as it goes rather than stat'ed afterwards: the caller owns the file and we may not be
     * able to see it at all — it can be an anonymous pipe, or a descriptor into a directory this app
     * cannot list. That is also why the reply carries no path, unlike §1's.
     */
    private fun runExport(
        jobId: String,
        fd: ParcelFileDescriptor,
        items: String?,
        progressAction: String,
        replyPackage: String,
        reply: (String) -> Unit,
    ) {
        val cats = resolve(items)
        if (cats == null) {
            reply("ERROR:unknown category in items: ${items.orEmpty()}")
            return
        }
        // §2a: progress applies to the data door too, correlated by the job id. The §1 sender is
        // parameterised rather than duplicated — two implementations of one watchdog drift, and the
        // one that drifts is always the one nobody is looking at.
        val progress = AutomationProgress(this, progressAction, replyPackage, jobId, asJob = true)
        var written = 0L
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(fd).use { out ->
                // A NAMED class, not a capturing anonymous `object : OutputStream()`. AGP's lint crashes
                // on a file where such an object and a local `fun` share a method, and it does so only
                // after Kotlin, Java and dex have all succeeded — i.e. it costs a whole release build to
                // discover. Neither construct alone is the trigger, so the cheap defence is to keep them
                // apart by construction (found across the family, 2026-09-04).
                val counting = CountingOutputStream(out)
                SettingsTransfer.export(this, cats, counting, progress.reporter) {
                    AutomationJobs.isCancelled(jobId)
                }
                written = counting.count
            }
        } catch (e: SettingsTransfer.Cancelled) {
            Log.i(AUTOMATION_LOG_TAG, "data door export unwound: ${e.message}")
            reply("ERROR:cancelled")
            return
        }
        progress.final(cats.size.toLong())
        reply("OK:$written|${humanSize(written)}|${cats.size} categories")
    }

    /**
     * Spool the archive to disk, check it is whole, and only then apply it.
     *
     * Nothing is written to this app until the entire archive has arrived and been checked — a partial
     * read applied halfway leaves a half-restored app, which is worse than one that refused. The spool
     * file is what keeps that guarantee bounded by disk rather than by RAM: a calendar's archive
     * carries an ICS of every event and task plus any imported font files, which is not a size worth
     * growing a byte array to.
     *
     * A complete ZIP ends with the end-of-central-directory signature `50 4b 05 06`; a truncated one
     * does not, which is exactly how a half-written backup is told from a real one.
     */
    private fun runImport(fd: ParcelFileDescriptor, reply: (String) -> Unit) {
        val spool = File(cacheDir, "automation_import.zip")
        try {
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                spool.outputStream().use { input.copyTo(it) }
            }
            if (spool.length() == 0L) {
                reply("ERROR:empty archive")
                return
            }
            if (!endsWithCentralDirectory(spool)) {
                reply("ERROR:archive is truncated")
                return
            }
            val bytes = spool.readBytes()
            // Every category the archive actually carries, not every category we know about: asking for
            // one the archive lacks is how a restore ends up reporting success over nothing.
            val present = SettingsTransfer.categoriesIn(bytes)
            if (present.isEmpty()) {
                reply("ERROR:archive carries no categories")
                return
            }
            val summary = SettingsTransfer.import(this, bytes, present)
            // 応用管理 force-stops us straight after this, deliberately and on its side: a running
            // process writes its cached SharedPreferences back out at orderly shutdown and would
            // silently undo the import that just happened.
            reply("OK:${present.size} categories restored — ${summary.replace('\n', ';')}")
        } finally {
            runCatching { spool.delete() }
        }
    }

    /** Absent or empty items means this app's default set — every category, all of which are default-on. */
    private fun resolve(items: String?): Set<SettingsTransfer.Category>? {
        if (items.isNullOrBlank()) {
            return SettingsTransfer.Category.entries.filter { it.defaultOn }.toSet()
        }
        val wanted = items.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val found = wanted.mapNotNull { SettingsTransfer.Category.byId(it) }.toSet()
        return found.takeIf { it.size == wanted.distinct().size }
    }

    private fun endsWithCentralDirectory(file: File): Boolean = runCatching {
        // The EOCD record is 22 bytes plus a comment of up to 65535; scanning the tail covers both.
        val window = minOf(file.length(), EOCD_SCAN_BYTES)
        if (window < EOCD_SIGNATURE.size) return false
        val tail = ByteArray(window.toInt())
        RandomAccessFile(file, "r").use {
            it.seek(file.length() - window)
            it.readFully(tail)
        }
        (tail.size - EOCD_SIGNATURE.size downTo 0).any { i ->
            EOCD_SIGNATURE.indices.all { j -> tail[i + j] == EOCD_SIGNATURE[j] }
        }
    }.getOrDefault(false)

    private fun humanSize(bytes: Long): String = when {
        bytes < KILO -> "$bytes B"
        bytes < KILO * KILO -> "%.1f KB".format(Locale.ROOT, bytes / KILO)
        bytes < KILO * KILO * KILO -> "%.1f MB".format(Locale.ROOT, bytes / (KILO * KILO))
        else -> "%.2f GB".format(Locale.ROOT, bytes / (KILO * KILO * KILO))
    }

    private fun acquireWakeLock(): PowerManager.WakeLock? = runCatching {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$AUTOMATION_LOG_TAG:data")
            .apply { acquire(WAKELOCK_TIMEOUT_MS) }
    }.getOrNull()

    private fun startForegroundCompat(importing: Boolean) {
        val notification = notification(importing)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(importing: Boolean): Notification {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.automation_data_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val title = if (importing) R.string.automation_data_importing else R.string.automation_data_exporting
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(title))
            .setSmallIcon(R.drawable.ic_calendar_vector)
            .setOngoing(true)
            .build()
    }

    private fun stop(startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }

    /** Bail out of a request we cannot serve, having already had to become foreground for it. */
    private fun stopForegroundAnd(startId: Int): Int {
        stopForeground(STOP_FOREGROUND_REMOVE)
        return stop(startId)
    }

    /**
     * Counts what it forwards. The caller owns the destination and we may not be able to stat it at
     * all — it can be an anonymous pipe, or a descriptor into a directory this app cannot list — so the
     * byte count in the reply has to be accumulated as we write.
     */
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
    }

    companion object {
        private const val CHANNEL = "automation_data"
        private const val NOTIFICATION_ID = 9714
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"
        private const val KILO = 1024.0
        private const val EOCD_SCAN_BYTES = 66_000L
        private const val WAKELOCK_TIMEOUT_MS = 15 * 60 * 1000L
        private val EOCD_SIGNATURE = byteArrayOf(0x50, 0x4b, 0x05, 0x06)

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A [ParcelFileDescriptor] in an Intent extra is duplicated by the system on delivery and the
         * copy's lifetime stops being ours to reason about. Handing it through a map keyed by the job
         * id keeps exactly one open descriptor with exactly one owner — the service, which closes it
         * in a `finally`.
         */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        /**
         * Start the job. Returns null when it started, or the exact `ERROR:` line to refuse with.
         *
         * The failure path is the point of the return value: `startForegroundService` from a binder
         * call is a background start, and on API 31+ it can be refused outright with
         * `ForegroundServiceStartNotAllowedException` unless this app is exempt from battery
         * optimisation. The dup is closed and the handover entry removed here, so the caller's open
         * file is never stranded in a map nothing will ever read.
         */
        fun start(
            context: Context,
            jobId: String,
            fd: ParcelFileDescriptor,
            importing: Boolean,
            extras: Bundle?,
        ): String? {
            HANDOVER[jobId] = fd
            return try {
                context.startForegroundService(
                    Intent(context, AutomationDataService::class.java)
                        .putExtra(EXTRA_JOB, jobId)
                        .putExtra(EXTRA_IMPORTING, importing)
                        .putExtra(AutomationProvider.KEY_ITEMS, extras?.getString(AutomationProvider.KEY_ITEMS))
                        .putExtra(
                            AutomationProvider.KEY_REPLY_ACTION,
                            extras?.getString(AutomationProvider.KEY_REPLY_ACTION)
                        )
                        .putExtra(
                            AutomationProvider.KEY_REPLY_PACKAGE,
                            extras?.getString(AutomationProvider.KEY_REPLY_PACKAGE)
                        )
                        .putExtra(
                            AutomationProvider.KEY_PROGRESS_ACTION,
                            extras?.getString(AutomationProvider.KEY_PROGRESS_ACTION)
                        )
                )
                null
            } catch (e: Exception) {
                HANDOVER.remove(jobId)
                runCatching { fd.close() }
                Log.w(AUTOMATION_LOG_TAG, "data door service refused to start: $e")
                "ERROR:${e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName}"
            }
        }
    }
}
