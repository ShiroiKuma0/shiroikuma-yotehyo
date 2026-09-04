package org.fossify.calendar.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import org.fossify.calendar.R
import org.fossify.calendar.extensions.config
import org.fossify.calendar.helpers.SettingsTransfer

/**
 * The data door: export this app's own state, and put it back, for a caller we can identify.
 *
 * ## Why a provider and not the broadcast receiver next to it
 *
 * Two reasons, and the first is the whole point of contract v2.
 *
 * **A broadcast cannot tell you who sent it.** v1's answer to that was a shared secret, which cannot
 * survive the wipe this feature exists to recover from. A provider gets the caller's identity from the
 * framework — see [AutomationCallers] for what is actually checked, and why a `shiroikuma.*` prefix
 * test would have been strictly weaker than the token it replaced.
 *
 * **A list needs a synchronous answer.** 白い熊 応用管理 draws a row per installed app before any
 * export exists; a broadcast round trip per app to fill a list is the wrong shape entirely.
 *
 * ## What does NOT happen here
 *
 * The payload. [call] validates, starts a foreground service and returns — tens of megabytes over
 * minutes inside a binder call would block the caller, report no progress, refuse cancellation and die
 * silently if this process were killed. The bytes go through a file descriptor the CALLER opened, and
 * the terminal answer comes back on the broadcast the family already proved on this EMUI.
 *
 * ## Why a descriptor and not a path
 *
 * Because a backup is not a stable directory while it is being assembled: 応用管理 writes into a
 * temporary path and renames on commit, and it encrypts and checksums per file it knows about. A ZIP
 * this app dropped into that directory itself would be renamed out from under it, would sit in
 * plaintext inside an otherwise encrypted backup, and would be unverified rather than
 * verified-and-failing. A descriptor is also a capability that expires when it is closed.
 *
 * ## And why import lives ONLY here
 *
 * An import overwrites this app's events, settings and appearance. The §1 receiver is exported with no
 * permission, so an import action there would let any app on the phone wipe any sister app. The door
 * that can identify its caller is the only door that may accept one.
 */
class AutomationProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    /**
     * Every method answers a [Bundle] with [KEY_RESULT] — `OK…` or `ERROR:…`, the same vocabulary the
     * broadcast contract uses, so a caller has one grammar to parse rather than two.
     *
     * A refusal is returned, never thrown: an exception across a binder reaches the caller as a
     * `RuntimeException` with our stack trace in it, which tells 白い熊 nothing and tells a misbehaving
     * caller rather more than it should.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return fail("ERROR:not ready")

        // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked for.
        when (val verdict = AutomationCallers.verify(ctx, callingPackage)) {
            is AutomationCallers.Verdict.Refused -> return fail(verdict.why)
            AutomationCallers.Verdict.Allowed -> Unit
        }
        // Then this app's own switches — one function, shared with the §1 receiver, and a token is
        // ignored rather than refused unless 白い熊 has asked for one.
        ctx.config.automationRefusal(extras?.getString(KEY_TOKEN))?.let { return fail(it) }

        return when (method) {
            METHOD_DESCRIBE -> ok(describe(ctx))
            METHOD_EXPORT -> start(ctx, extras, importing = false)
            METHOD_IMPORT -> start(ctx, extras, importing = true)
            METHOD_CANCEL -> {
                AutomationJobs.cancel(extras?.getString(KEY_JOB_ID))
                ok("OK:cancelled")
            }

            else -> fail("ERROR:unknown method: $method")
        }
    }

    /**
     * What this app would export, answered without exporting anything.
     *
     * Returned from the call rather than written into the archive, deliberately: 応用管理 must draw a
     * row before an export exists, and at restore must judge compatibility BEFORE streaming megabytes
     * into an app that would reject them — which it cannot do if the header is buried inside an
     * encrypted archive.
     *
     * `contains` is rendered verbatim by 応用管理, so for a calendar it has to answer the question
     * 白い熊 will actually be asking of that row: **which half of a calendar is this backup keeping?**
     * The app's own data — local events and tasks, their reminders and their repeat rules — is the part
     * that exists nowhere else and is genuinely being preserved. A synced account's calendars are not:
     * they belong to the account, and they come back by themselves the moment it is signed in again. So
     * the strings name the local part as local and say plainly that the synced part is not what this is
     * protecting (白い熊, via 応用管理, 2026-09-04).
     */
    private fun describe(ctx: Context): String {
        val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION") val versionCode = pkg.versionCode
        val contains = CONTAINS.map { ctx.getString(it) }
        return "OK:" + """
            {"app_id":"${ctx.packageName}",
             "version_code":$versionCode,
             "version_name":"${pkg.versionName}",
             "format":$FORMAT,
             "min_format_readable":$MIN_FORMAT_READABLE,
             "requires_launch_first":false,
             "requires_permissions":[${REQUIRES_PERMISSIONS.joinToString(",") { "\"$it\"" }}],
             "contains":[${contains.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}]}
        """.trimIndent().replace("\n", "")
    }

    /**
     * Hand the descriptor to a foreground service and get out of the way.
     *
     * The descriptor is **duplicated** before it leaves this method: the one in [extras] belongs to the
     * binder transaction and is closed the moment [call] returns, so a service reading it afterwards
     * would find it shut — a bug you only see under load, which is why it is not left to the service to
     * remember.
     *
     * And if the service does not start, the dup is closed and the job dropped **before** we answer.
     * That window is real: `startForegroundService` from a binder call is a background start, and on
     * API 31+ it can be refused outright unless this app is exempt from battery optimisation. Leaking
     * there would strand the caller's open file in a map nothing will ever read, and let the exception
     * cross the binder as a stack trace instead of a refusal.
     */
    private fun start(ctx: Context, extras: Bundle?, importing: Boolean): Bundle {
        @Suppress("DEPRECATION") val fd = extras?.getParcelable<ParcelFileDescriptor>(KEY_FD)
            ?: return fail("ERROR:no descriptor")
        val dup = runCatching { fd.dup() }.getOrNull() ?: return fail("ERROR:descriptor unusable")
        val jobId = AutomationJobs.begin()
        AutomationDataService.start(ctx, jobId, dup, importing, extras)?.let { failure ->
            AutomationJobs.finish(jobId)
            return fail(failure)
        }
        return ok("OK:$jobId")
    }

    private fun ok(result: String) = Bundle().apply { putString(KEY_RESULT, result) }
    private fun fail(why: String) = Bundle().apply { putString(KEY_RESULT, why) }

    // A provider that is only ever call()ed still has to answer these. Refusing loudly beats returning
    // an empty cursor, which reads downstream as "there is no data" rather than "wrong door".
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? =
        throw UnsupportedOperationException("automation is call() only")

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("automation is call() only")

    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    companion object {
        const val METHOD_DESCRIBE = "describe"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val METHOD_CANCEL = "cancel"

        const val KEY_RESULT = "result"
        const val KEY_FD = "fd"
        const val KEY_TOKEN = "token"
        const val KEY_JOB_ID = "job_id"
        const val KEY_ITEMS = "items"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_REPLY_PACKAGE = "reply_package"
        const val KEY_PROGRESS_ACTION = "progress_action"

        /**
         * The human lines 応用管理 prints under this app's row, in order — what the backup keeps, then
         * the one thing it deliberately does not. See [describe] for why a calendar has to say both.
         */
        private val CONTAINS = listOf(
            R.string.automation_contains_events,
            R.string.automation_contains_calendars,
            R.string.automation_contains_settings,
            R.string.automation_contains_appearance,
            R.string.automation_contains_widgets,
            R.string.automation_contains_synced_excluded,
        )

        /**
         * Runtime permissions this app's IMPORT needs — **none**, and that is a real answer rather than
         * an oversight worth checking twice, because the contract lists a calendar among the apps it
         * expects to be affected.
         *
         * The reasoning it expects — install → do not launch → import, into an app holding no runtime
         * grants — applies to data living behind a permission-guarded system provider. This fork's
         * backup is not that: events and tasks are its OWN Room rows, restored through the ICS importer
         * with `calDAVCalendarId = 0`, which stamps every entry `SOURCE_IMPORTED_ICS` — and
         * `EventsHelper.insertEvents` skips the CalendarContract write for exactly that source, and
         * again unless `caldavSync` is on, which is false on a fresh install and is never exported (the
         * `caldav_` keys are device-local). The rest of the import is SharedPreferences, Room and the
         * app's own files directory. So nothing here can raise a SecurityException for a missing grant,
         * and 応用管理 should not prompt 白い熊 for one it does not need.
         *
         * The manifest's READ_CALENDAR / WRITE_CALENDAR serve CalDAV sync, which the restore path does
         * not enter. If a future category ever wrote to the system provider, this is the list to add to.
         */
        private val REQUIRES_PERMISSIONS = emptyList<String>()

        /**
         * This app's archive format — [SettingsTransfer.VERSION], kept in step deliberately: there is
         * one archive, so there is one number, and a second one would only ever drift out of true.
         */
        const val FORMAT = SettingsTransfer.VERSION

        /**
         * The oldest archive this build can still read.
         *
         * Version skew has a direction: old data into a newer app is normally fine, because an app
         * migrates its own storage; newer data into an older app is not. This is what lets a restore be
         * refused at discovery time rather than halfway through. The importer skips categories it does
         * not recognise and merges prefs per key, so every format written so far is still readable.
         */
        const val MIN_FORMAT_READABLE = 1
    }
}
