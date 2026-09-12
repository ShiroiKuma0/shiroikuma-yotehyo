package org.fossify.calendar.helpers

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import org.fossify.calendar.BuildConfig
import org.fossify.calendar.R
import org.fossify.calendar.extensions.calendarsDB
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.models.CalendarEntity
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.ACCENT_COLOR
import org.fossify.commons.helpers.APP_ICON_COLOR
import org.fossify.commons.helpers.APP_ID
import org.fossify.commons.helpers.APP_RUN_COUNT
import org.fossify.commons.helpers.APP_SIDELOADING_STATUS
import org.fossify.commons.helpers.BACKGROUND_COLOR
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.INTERNAL_STORAGE_PATH
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.helpers.IS_GLOBAL_THEME_ENABLED
import org.fossify.commons.helpers.IS_SYSTEM_THEME_ENABLED
import org.fossify.commons.helpers.LAST_EXPORTED_SETTINGS_FILE
import org.fossify.commons.helpers.LAST_EXPORTED_SETTINGS_FOLDER
import org.fossify.commons.helpers.LAST_VERSION
import org.fossify.commons.helpers.OTG_ANDROID_DATA_TREE_URI
import org.fossify.commons.helpers.OTG_ANDROID_OBB_TREE_URI
import org.fossify.commons.helpers.OTG_PARTITION
import org.fossify.commons.helpers.OTG_REAL_PATH
import org.fossify.commons.helpers.OTG_TREE_URI
import org.fossify.commons.helpers.PRIMARY_ANDROID_DATA_TREE_URI
import org.fossify.commons.helpers.PRIMARY_ANDROID_OBB_TREE_URI
import org.fossify.commons.helpers.PRIMARY_COLOR
import org.fossify.commons.helpers.SD_ANDROID_DATA_TREE_URI
import org.fossify.commons.helpers.SD_ANDROID_OBB_TREE_URI
import org.fossify.commons.helpers.SD_CARD_PATH
import org.fossify.commons.helpers.SD_TREE_URI
import org.fossify.commons.helpers.STYLED_DIALOG_BUTTONS
import org.fossify.commons.helpers.TEXT_COLOR
import org.fossify.commons.helpers.WAS_OTG_HANDLED
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Real-count progress from the export core: how many of what have been written, plus the ready-made
 * display line. Never a percentage — 白い熊 reads the numbers ("Events 1234/8942").
 */
typealias ProgressReporter = (current: Long, total: Long, unit: String, text: String) -> Unit

/**
 * The category-based settings Export/Import behind the top section of 白い熊 予定表 UI (same idea and
 * flow as the Kōjiki page). The export is a ZIP of plain JSON files — one per category — plus any
 * imported font files as real files under `fonts/`; a `manifest.json` lists format, version and the
 * categories present. Every settable item lives in one category: prefs keys are routed by
 * [categoryOf] (unknown keys fall into GENERAL so nothing is ever silently dropped), local calendar
 * categories come from the Room DB. Import merges — selected categories only, absent files skipped,
 * unknown keys tolerated, rows upserted by title, never wiped.
 *
 * Calendar IDS never travel as meaning: they are this phone's auto-increment numbers, and the prefs
 * that hold them (which calendars are shown, quick-filtered, auto-backed-up, the default one) are
 * remapped on import through the calendar TITLES — see [calendarIdMap]. Without that, a restore onto
 * a new phone re-created every calendar under fresh ids and then applied the old phone's id sets over
 * them, so any calendar whose number differed was filtered out of every view and looked unrestored.
 */
object SettingsTransfer {
    const val FORMAT = "yotehyo-export"
    const val VERSION = 1
    const val WARN_COLOR = 0xFFFF5252.toInt() // warn-red for "no directory / no export yet" statuses

    // The app's English dash-separated name, and the prefix every export of ours starts with — the whole
    // family names its backups "<app-name>_<yyyy-MM-dd_HH-mm-ss>.zip", so 白い熊 can keep every app's
    // backups in one directory and have them sort and read uniformly. Deliberately version-free: a backup
    // is identified by when it was taken, not by the build that wrote it (that is recorded inside, as
    // manifest.json's appVersion). Older exports carried the version in the name and still match this
    // prefix, so the "last export" row keeps finding them.
    const val EXPORT_PREFIX = "shiroikuma-yotehyo"

    private const val EXIMPORT_PREFS = "yotehyo_eximport" // device-local; deliberately never exported
    private const val KEY_DIR_URI = "dir_uri"

    /**
     * Thrown out of [export] when the caller's cancel check trips — a stop that was asked for, never a
     * failure, so the callers answer it with "cancelled" rather than an error reason. See the
     * CANCEL_EXPORT action in StateExportReceiver.
     */
    class Cancelled : Exception("cancelled")

    /**
     * Everything independently selectable in an export or import: the top-level categories plus their
     * parts (sub-options). `id` is what the automation contract accepts in its "items" extra, and for a
     * top-level category it is also the stable name its data carries inside the ZIP. A part names its
     * parent through [parentId] and is dotted after it ("ui_theme.fonts") — selecting a parent WITHOUT
     * its parts means that category's own data only. [labelRes] is the descriptive label shown in the
     * picker (in-app and in 自由作業盤), [shortLabelRes] the bare noun used in progress lines.
     *
     * [defaultOn] is whether the item STARTS TICKED in a freshly drawn picker — this app's answer to
     * state rather than the picker's to guess (the contract's optional fourth LIST_CATEGORIES field).
     * It defaults to true and every category here keeps it: the "off" case is for something large,
     * derived and re-creatable (a tile cache, a regenerable thumbnail), and nothing this app exports
     * is any of those. Anything added later inherits a field that is already on the wire.
     */
    enum class Category(
        val id: String,
        val parentId: String?,
        @StringRes val labelRes: Int,
        @StringRes val shortLabelRes: Int,
        val defaultOn: Boolean = true,
    ) {
        EVENTS("events", null, R.string.eim_cat_events, R.string.eim_cat_events_short),
        GENERAL("general", null, R.string.eim_cat_general, R.string.eim_cat_general_short),
        UI_THEME("ui_theme", null, R.string.eim_cat_ui, R.string.eim_cat_ui_short),
        UI_THEME_FONTS("ui_theme.fonts", "ui_theme", R.string.eim_cat_fonts, R.string.eim_cat_fonts_short),
        WIDGETS("widgets", null, R.string.eim_cat_widgets, R.string.eim_cat_widgets_short),
        CALENDARS("calendars", null, R.string.eim_cat_calendars, R.string.eim_cat_calendars_short);

        val isTopLevel: Boolean get() = parentId == null

        /** The parts of this category, in declaration order — empty for a leaf. */
        val children: List<Category> get() = entries.filter { it.parentId == id }

        companion object {
            fun byId(id: String): Category? = entries.firstOrNull { it.id == id }

            /** Parents first, each followed by its own parts — the order both pickers render. */
            val listed: List<Category>
                get() = entries.filter { it.isTopLevel }.flatMap { listOf(it) + it.children }
        }
    }

    // Events/tasks travel as standard ICS (the upstream exporter/importer handles recurrences,
    // reminders and categories); the imported fonts are real files under fonts/; everything else is a
    // plain JSON file. Null = the category has no single entry of its own.
    private fun entryName(cat: Category): String? = when (cat) {
        Category.EVENTS -> "events.ics"
        Category.UI_THEME_FONTS -> null // a directory of real font files, not one entry
        else -> "${cat.id}.json"
    }

    // Import order — not [Category.listed]: calendars first, so events land in categories that already
    // carry their type, colour and font and the id map exists; the prefs that refer to calendar ids last.
    private val IMPORT_ORDER = listOf(
        Category.CALENDARS, Category.EVENTS, Category.GENERAL, Category.UI_THEME, Category.UI_THEME_FONTS,
        Category.WIDGETS
    )

    // Prefs whose values are calendar ids — a set of them, or the one default calendar. Remapped on
    // import (see calendarIdMap); everything else in GENERAL is copied verbatim.
    private val CALENDAR_ID_SET_KEYS = setOf(DISPLAY_CALENDARS, QUICK_FILTER_CALENDARS, AUTO_BACKUP_CALENDARS)
    private val CALENDAR_ID_KEYS = setOf(DEFAULT_CALENDAR_ID)

    // Device-local keys never worth exporting: storage paths/SAF grants, version bookkeeping,
    // sideloading state, the last-used export bookkeeping of the stock mechanism, and the automation
    // gate. The automation pair is excluded on purpose and must stay excluded: each device owns its own
    // security state, so a restore must never silently flip automation on or overwrite the token — and
    // the shared secret must never travel inside a backup ZIP.
    private val DEVICE_LOCAL_KEYS = setOf(
        APP_ID, APP_RUN_COUNT, LAST_VERSION, APP_SIDELOADING_STATUS, INTERNAL_STORAGE_PATH,
        SD_CARD_PATH, OTG_REAL_PATH, OTG_PARTITION, WAS_OTG_HANDLED, SD_TREE_URI, OTG_TREE_URI,
        PRIMARY_ANDROID_DATA_TREE_URI, OTG_ANDROID_DATA_TREE_URI, SD_ANDROID_DATA_TREE_URI,
        PRIMARY_ANDROID_OBB_TREE_URI, OTG_ANDROID_OBB_TREE_URI, SD_ANDROID_OBB_TREE_URI,
        LAST_EXPORTED_SETTINGS_FOLDER, LAST_EXPORTED_SETTINGS_FILE, AUTO_BACKUP_FOLDER,
        AUTOMATION_ENABLED, AUTOMATION_REQUIRE_TOKEN, AUTOMATION_TOKEN
    )

    // Everything the 白い熊 予定表 UI page controls, plus the stock look keys it writes through to.
    private val UI_KEYS = setOf(
        TEXT_COLOR, BACKGROUND_COLOR, PRIMARY_COLOR, ACCENT_COLOR, APP_ICON_COLOR,
        IS_SYSTEM_THEME_ENABLED, IS_GLOBAL_THEME_ENABLED,
        SHOW_TIME_GRID_WEEKLY, SHOW_BOX_GRID_WEEKLY, EVENT_TIME_FORMAT,
        HIGHLIGHT_WEEKENDS, HIGHLIGHT_WEEKENDS_COLOR,
        TODAY_BOX_BORDER_THICKNESS, TODAY_HEADER_BORDER_THICKNESS,
        WEEKEND_BOX_BORDER_THICKNESS, WEEKEND_HEADER_BORDER_THICKNESS,
        STYLED_DIALOG_BUTTONS, DIALOG_STYLE_SEEDED, PURE_YELLOW_MIGRATED
    )

    // theme_* covers the slots/revision/seed, font_* the global size + per-slot triples,
    // day_box_* the header format/alignment/thicknesses, dialog_border_* the accent border.
    private val UI_PREFIXES = listOf("theme_", "font_", "week_grid_line_", "day_box_", "dialog_border_")

    /** Which category a prefs key belongs to; null = device-local, never exported or imported. */
    private fun categoryOf(key: String): Category? = when {
        key in DEVICE_LOCAL_KEYS -> null
        key.startsWith("caldav_") || key.startsWith("last_used_") -> null
        key.startsWith("widget_") -> Category.WIDGETS
        key in UI_KEYS || UI_PREFIXES.any { key.startsWith(it) } -> Category.UI_THEME
        else -> Category.GENERAL
    }

    // ---------- Export ----------

    /**
     * The export core, callable headlessly — no Activity, no user interaction. Writes a ZIP of the
     * selected categories into [out] and reports real counts through [onProgress] (unthrottled; the
     * caller decides how often to surface them). Blocking, so call it on a background thread; it throws
     * on failure so the Export/Import panel and the automation receiver share one error path.
     * Returns a short human summary.
     *
     * [isCancelled] is polled at every entry boundary — between categories, and between events inside
     * the one category big enough to matter — and unwinds the run with [Cancelled] the moment it
     * answers true. Never mid-`write()`: the boundary is always between whole entries. The caller owns
     * what happens to the half-written ZIP.
     */
    fun export(
        context: Context,
        cats: Set<Category>,
        out: OutputStream,
        onProgress: ProgressReporter = { _, _, _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): String {
        // Declaration order, not the caller's, so a ZIP's contents never depend on how the set was built.
        val ordered = Category.listed.filter { it in cats }
        require(ordered.isNotEmpty()) { "nothing selected" }
        val total = ordered.size.toLong()
        val unit = context.getString(R.string.state_progress_unit_category)

        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("app", context.packageName)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("createdTs", System.currentTimeMillis())
                .put("categories", JSONArray(ordered.map { it.id }))
            writeEntry(zip, "manifest.json", manifest.toString(2))

            ordered.forEachIndexed { index, cat ->
                if (isCancelled()) throw Cancelled()
                val done = index + 1L
                onProgress(done, total, unit, "$unit $done/$total — ${context.getString(cat.shortLabelRes)}")
                when (cat) {
                    Category.EVENTS -> writeEntry(zip, "events.ics", exportEventsIcs(context, onProgress, isCancelled))
                    Category.UI_THEME_FONTS -> exportFonts(context, zip)
                    Category.CALENDARS -> writeEntry(zip, entryName(cat)!!, exportCalendars(context))
                    else -> writeEntry(zip, entryName(cat)!!, exportPrefs(context, cat))
                }
            }
        }
        return "${ordered.size} categor${if (ordered.size == 1) "y" else "ies"}"
    }

    // One JSON object per prefs category, every value typed so import can restore it exactly.
    private fun exportPrefs(context: Context, cat: Category): String {
        val obj = JSONObject()
        for ((key, value) in context.getSharedPrefs().all) {
            if (categoryOf(key) != cat) continue
            val e = JSONObject()
            when (value) {
                is Boolean -> { e.put("t", "b"); e.put("v", value) }
                is Int -> { e.put("t", "i"); e.put("v", value) }
                is Long -> { e.put("t", "l"); e.put("v", value) }
                is Float -> { e.put("t", "f"); e.put("v", value.toDouble()) }
                is String -> { e.put("t", "s"); e.put("v", value) }
                is Set<*> -> { e.put("t", "ss"); e.put("v", JSONArray(value.map { it.toString() })) }
                else -> continue
            }
            obj.put(key, e)
        }
        return obj.toString(2)
    }

    // Local calendar categories only — synced (CalDAV) ones belong to their account, not the export.
    // The id is written so an import can translate the id-bearing prefs, never to be reused as an id.
    private fun exportCalendars(context: Context): String {
        val arr = JSONArray()
        context.calendarsDB.getCalendars().filter { !it.isSyncedCalendar() }.forEach { cal ->
            arr.put(
                JSONObject()
                    .put("id", cal.id)
                    .put("title", cal.title)
                    .put("color", cal.color)
                    .put("type", cal.type)
                    .put("backgroundColor", cal.backgroundColor)
                    .put("fontFamily", cal.fontFamily)
                    .put("fontWeight", cal.fontWeight)
                    .put("fontSize", cal.fontSize)
            )
        }
        return arr.toString(2)
    }

    private fun exportFonts(context: Context, zip: ZipOutputStream) {
        FontHelper.getFontsDir(context).listFiles()?.forEach { file ->
            if (file.isFile) {
                zip.putNextEntry(ZipEntry("fonts/${file.name}"))
                zip.write(file.readBytes())
                zip.closeEntry()
            }
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        writeEntry(zip, name, content.toByteArray())
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content)
        zip.closeEntry()
    }

    // Every local + synced calendar, all events and tasks, past included. The exporter's callback runs
    // synchronously because export() is always called from a background thread. This is the one category
    // that can hold thousands of rows, so it reports its own real counts ("Events 1234/8942") rather than
    // leaving 白い熊 with a single category tick for the whole run — and, for the same reason, it is the
    // one category that must honour a cancel from inside: it checks per entry, on that same synchronous
    // callback, so [Cancelled] unwinds through the exporter between two whole events.
    private fun exportEventsIcs(
        context: Context,
        onProgress: ProgressReporter,
        isCancelled: () -> Boolean,
    ): ByteArray {
        val calendarIds = context.calendarsDB.getCalendars().mapNotNull { it.id }
        val events = context.eventsHelper.getEventsToExport(
            calendars = calendarIds,
            exportEvents = true,
            exportTasks = true,
            exportPastEntries = true
        )
        val unit = context.getString(R.string.state_progress_unit_events)
        val total = events.size.toLong()
        val out = ByteArrayOutputStream()
        var exportResult: IcsExporter.ExportResult? = null
        IcsExporter(context).exportEvents(
            outputStream = out,
            events = events,
            showExportingToast = false,
            onProgress = { written ->
                if (isCancelled()) throw Cancelled()
                onProgress(written.toLong(), total, unit, "$unit $written/$total")
            },
        ) { exportResult = it }
        // An empty calendar is a valid backup — the exporter only reports FAIL by writing nothing, which
        // is exactly what "no events" looks like, so treat it as a failure only when there was work to do.
        if (exportResult == IcsExporter.ExportResult.EXPORT_FAIL && events.isNotEmpty()) {
            error("events export failed")
        }
        return out.toByteArray()
    }

    // ---------- Import ----------

    /** Categories present in a ZIP (from its manifest, falling back to the entries actually found). */
    fun categoriesIn(zip: ByteArray): Set<Category> {
        val files = readZip(zip)
        files["manifest.json"]?.let { manifest ->
            val cats = runCatching { JSONObject(manifest.decodeToString()).optJSONArray("categories") }.getOrNull()
            if (cats != null) {
                val set = (0 until cats.length()).mapNotNull { Category.byId(cats.optString(it)) }.toSet()
                if (set.isNotEmpty()) {
                    return set
                }
            }
        }
        return Category.entries.filter { cat ->
            if (cat == Category.UI_THEME_FONTS) {
                files.keys.any { it.startsWith("fonts/") }
            } else {
                files.containsKey(entryName(cat))
            }
        }.toSet()
    }

    /**
     * Apply the selected categories from a ZIP. Missing files are skipped. Returns a human summary.
     *
     * Takes a plain [Context], not an Activity: contract v2's data door (§2a) imports from a foreground
     * service started by a binder call, where there is no Activity and never will be — a clean-phone
     * restore happens into an app that has deliberately not been launched. Everything below was already
     * a Context extension; only [IcsImporter] had to widen with it.
     */
    fun import(context: Context, zip: ByteArray, cats: Set<Category>): String {
        val files = readZip(zip)
        val parts = mutableListOf<String>()
        for (cat in IMPORT_ORDER.filter { it in cats }) {
            val count = if (cat == Category.UI_THEME_FONTS) {
                importFonts(context, files)
            } else {
                val data = files[entryName(cat)] ?: continue
                when (cat) {
                    Category.EVENTS -> importEventsIcs(context, data)
                    Category.CALENDARS -> importCalendars(context, data.decodeToString())
                    // Built here, after calendars and events have been applied, so every title the
                    // export names has been given this phone's id by one of them.
                    Category.GENERAL -> importPrefs(
                        context, cat, data.decodeToString(), calendarIdMap(context, files)
                    )
                    else -> importPrefs(context, cat, data.decodeToString())
                }
            }
            parts.add("${context.getString(cat.labelRes)}: $count")
        }
        if (Category.UI_THEME in cats || Category.UI_THEME_FONTS in cats || Category.WIDGETS in cats) {
            // Repaint everything with the imported look on the next resume.
            context.config.themeRevision = context.config.themeRevision + 1
        }
        flushPrefs(context)
        return if (parts.isEmpty()) "nothing imported" else parts.joinToString("\n")
    }

    /**
     * Force every pending SharedPreferences write to disk before we tell anyone the import worked.
     *
     * A synchronous commit is a barrier for the asynchronous writes queued before it: SharedPreferences
     * serialises its disk writes through one worker, so awaiting the latest implies the earlier ones
     * have run. This catches the writes we do NOT own — commons' BaseConfig setters (themeRevision
     * above) all use apply(), and a SIGKILL arriving between that apply() and its disk write would lose
     * it. Everything else on the restore path is already durable when its call returns: Room commits
     * its transaction, and the font files are written through.
     */
    private fun flushPrefs(context: Context) {
        runCatching { context.getSharedPrefs().edit().commit() }
    }

    // The upstream ICS importer needs a file path; it upserts by import id (UID), so re-importing the
    // same export does not duplicate events. Returns the number of entries the file carries.
    private fun importEventsIcs(context: Context, data: ByteArray): Int {
        if (data.isEmpty()) return 0
        val file = File(context.cacheDir, "eim_import_events.ics")
        file.writeBytes(data)
        try {
            val result = IcsImporter(context).importEvents(
                path = file.absolutePath,
                defaultCalendarId = defaultCalendarId(context),
                calDAVCalendarId = 0,
                overrideFileCalendars = false
            )
            if (result == IcsImporter.ImportResult.IMPORT_FAIL) {
                error("events import failed")
            }
        } finally {
            file.delete()
        }
        return Regex("BEGIN:(VEVENT|VTODO)").findAll(data.decodeToString()).count()
    }

    // Fallback calendar for ICS entries without a CATEGORIES line (our exports always carry one).
    private fun defaultCalendarId(context: Context): Long =
        context.config.defaultCalendarId.takeIf { it > 0L }
            ?: context.calendarsDB.getCalendars().firstOrNull { !it.isSyncedCalendar() }?.id
            ?: 1L

    // Merge — never clear, so unrelated/device-local keys survive; keys are re-routed through
    // categoryOf so a tampered file cannot smuggle values into an unselected category. Calendar ids in
    // the values are translated through [idMap]; a default calendar that translates to nothing is left
    // as this phone has it.
    private fun importPrefs(
        context: Context,
        cat: Category,
        json: String,
        idMap: Map<Long, Long> = emptyMap(),
    ): Int {
        val obj = JSONObject(json)
        val editor = context.getSharedPrefs().edit()
        var count = 0
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (categoryOf(key) != cat) continue
            val e = obj.optJSONObject(key) ?: continue
            when (e.optString("t")) {
                "b" -> editor.putBoolean(key, e.optBoolean("v"))
                "i" -> editor.putInt(key, e.optInt("v"))
                "l" -> {
                    val value = e.optLong("v")
                    if (key in CALENDAR_ID_KEYS) {
                        editor.putLong(key, remapCalendarId(value, idMap) ?: continue)
                    } else {
                        editor.putLong(key, value)
                    }
                }

                "f" -> editor.putFloat(key, e.optDouble("v").toFloat())
                "s" -> editor.putString(key, e.optString("v"))
                "ss" -> {
                    val arr = e.optJSONArray("v") ?: JSONArray()
                    var values = (0 until arr.length()).map { arr.optString(it) }.toSet()
                    if (key in CALENDAR_ID_SET_KEYS) {
                        values = values.mapNotNull { id ->
                            id.toLongOrNull()?.let { remapCalendarId(it, idMap) }?.toString()
                        }.toSet()
                    }
                    editor.putStringSet(key, values)
                }

                else -> continue
            }
            count++
        }
        // commit(), NOT apply(). 応用管理 force-stops this app the instant an import reports success —
        // deliberately, because a live process writes its cached SharedPreferences back out at orderly
        // shutdown and would silently undo the import. That force-stop is a SIGKILL, so an apply() still
        // in flight is simply lost and the restore reports success over data that never reached disk.
        // Synchronous is also fine for the UI path: import always runs off the main thread.
        editor.commit()
        return count
    }

    /**
     * The exporting phone's id → this phone's id, for every local calendar the export lists, matched by
     * title (the same lookup the ICS importer files events under). Empty for an export written before
     * ids travelled — the id-bearing prefs are then applied verbatim, as they always were.
     */
    private fun calendarIdMap(context: Context, files: Map<String, ByteArray>): Map<Long, Long> {
        val json = files[entryName(Category.CALENDARS)]?.decodeToString() ?: return emptyMap()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyMap()
        val map = HashMap<Long, Long>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val oldId = o.optLong("id", -1L)
            val title = o.optString("title")
            if (oldId <= 0L || title.isEmpty()) continue
            val newId = context.calendarsDB.getCalendarIdWithTitle(title) ?: continue
            map[oldId] = newId
        }
        return map
    }

    /**
     * Where an exported calendar id points on this phone, or null for "nowhere". An id the export does
     * not list (a synced calendar's — those belong to their account, not the export) is kept as it is,
     * so a same-phone restore leaves the synced calendars exactly as visible as they were; unless this
     * phone has since handed that very number to one of the listed local calendars, where keeping it
     * would show or hide the wrong one.
     */
    private fun remapCalendarId(id: Long, idMap: Map<Long, Long>): Long? = when {
        idMap.containsKey(id) -> idMap.getValue(id)
        idMap.containsValue(id) -> null
        else -> id
    }

    // Upsert local calendar categories by title; existing ones keep their id (and their events). A new
    // one goes through the helper the app's own "add calendar" uses, so it starts shown and quick-
    // filterable like any other — plain DB insert left it invisible until an event landed in it.
    private fun importCalendars(context: Context, json: String): Int {
        val arr = JSONArray(json)
        var count = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title")
            if (title.isEmpty()) continue
            val existing = context.calendarsDB.getLocalCalendarIdWithTitle(title)
                ?.let { context.calendarsDB.getCalendarWithId(it) }
            val entity = existing ?: CalendarEntity(id = null, title = title, color = 0, type = o.optInt("type", OTHER_EVENT))
            entity.color = o.optInt("color", entity.color)
            entity.backgroundColor = o.optInt("backgroundColor", THEME_UNSET)
            entity.fontFamily = o.optString("fontFamily", "")
            entity.fontWeight = o.optInt("fontWeight", 0)
            entity.fontSize = o.optInt("fontSize", 0)
            context.eventsHelper.insertOrUpdateCalendarSync(entity)
            count++
        }
        return count
    }

    /** Restores the imported font files; returns how many were written. */
    private fun importFonts(context: Context, files: Map<String, ByteArray>): Int {
        var count = 0
        files.forEach { (name, bytes) ->
            if (name.startsWith("fonts/")) {
                val fileName = File(name).name // strips any ../ traversal
                if (fileName.isNotEmpty()) {
                    FontHelper.saveFontData(context, bytes, fileName)
                    count++
                }
            }
        }
        return count
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val map = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    map[entry.name] = zin.readBytes()
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        return map
    }

    // ---------- The persisted export directory + "last export" status ----------

    private fun eximportPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(EXIMPORT_PREFS, Context.MODE_PRIVATE)

    fun dirUri(context: Context): Uri? =
        eximportPrefs(context).getString(KEY_DIR_URI, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun setDirUri(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        eximportPrefs(context).edit().putString(KEY_DIR_URI, uri.toString()).apply()
    }

    fun exportDir(context: Context): DocumentFile? =
        dirUri(context)?.let { runCatching { DocumentFile.fromTreeUri(context, it) }.getOrNull() }
            ?.takeIf { it.isDirectory }

    /** "shiroikuma-yotehyo_2026-07-25_18-58-23.zip" — the app's English name, then when it was taken. */
    fun exportFileName(): String =
        EXPORT_PREFIX + "_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    // ---------- The headless destination (automation) ----------

    /**
     * A resolved headless export destination: where to write, how big it ended up, and how to take it
     * back again. [discard] removes the file this target created — the caller runs it whenever the run
     * does not reach its OK line (cancelled or failed), so the backup directory is left exactly as it
     * was found: no short archive to mistake for a backup, and none for "last export" to pick up.
     */
    class Target(
        val displayPath: String,
        val open: () -> OutputStream,
        val size: () -> Long,
        val discard: () -> Unit,
    )

    /**
     * Resolve where a headless export writes. Directory precedence, per the automation contract:
     * [pathOverride] (an absolute directory, created if missing) → the app's configured export
     * directory → null, which the caller reports as "no-directory".
     */
    fun headlessTarget(context: Context, pathOverride: String): Target? {
        val name = exportFileName()
        if (pathOverride.isNotEmpty()) {
            // /sdcard is a symlink; normalize it so the MediaStore path checks below match.
            val primary = Environment.getExternalStorageDirectory().absolutePath
            val dir = pathOverride.replaceFirst(Regex("^/sdcard"), primary)
            val file = File(dir, name)
            file.parentFile?.mkdirs()
            // Whichever route the write took has to be the route the discard takes: a MediaStore
            // insert owns a row, and under scoped storage File.delete() alone would not reach it.
            var mediaUri: Uri? = null
            return Target(
                displayPath = file.absolutePath,
                open = { openAbsolute(context, file) { mediaUri = it } },
                size = { file.length() },
                discard = {
                    mediaUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
                    runCatching { file.delete() }
                },
            )
        }

        val dir = exportDir(context) ?: return null
        val file = dir.createFile("application/zip", name) ?: error("cannot create a file in ${dir.name}")
        return Target(
            displayPath = displayPathOf(file.uri),
            open = { context.contentResolver.openOutputStream(file.uri) ?: error("cannot open ${file.uri}") },
            size = { file.length() },
            discard = { runCatching { file.delete() } },
        )
    }

    /**
     * Write to an arbitrary absolute path. Download/ and Documents/ take non-media files from any app
     * through MediaStore with no permission at all; anywhere else on shared storage needs All-files
     * access on API 30+, so name that remedy instead of letting the write fail silently under FUSE.
     * [onMediaUri] is called with the row we ended up writing, so a discarded run can delete it.
     */
    private fun openAbsolute(context: Context, file: File, onMediaUri: (Uri) -> Unit): OutputStream {
        mediaStoreStream(context, file, onMediaUri)?.let { return it }

        val primary = Environment.getExternalStorageDirectory().absolutePath
        if (isRPlus() && file.absolutePath.startsWith("$primary/") && !Environment.isExternalStorageManager()) {
            error("no-storage-access")
        }
        file.parentFile?.mkdirs()
        return FileOutputStream(file)
    }

    @Suppress("ReturnCount")
    private fun mediaStoreStream(context: Context, file: File, onMediaUri: (Uri) -> Unit): OutputStream? {
        val primary = Environment.getExternalStorageDirectory().absolutePath
        val parent = file.parentFile?.absolutePath ?: return null
        if (!parent.startsWith("$primary/")) return null

        val relative = parent.removePrefix("$primary/").trimEnd('/')
        val topDir = relative.substringBefore('/')
        if (topDir != Environment.DIRECTORY_DOWNLOADS && topDir != Environment.DIRECTORY_DOCUMENTS) {
            return null
        }

        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        // Rewrite our own earlier file of the same name instead of piling up "name (1).zip" copies.
        runCatching {
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            context.contentResolver.query(
                collection, arrayOf(MediaStore.MediaColumns._ID), selection, arrayOf("$relative/", file.name), null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                    return context.contentResolver.openOutputStream(uri, "wt")?.also { onMediaUri(uri) }
                }
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
        }
        val uri = runCatching { context.contentResolver.insert(collection, values) }.getOrNull() ?: return null
        return runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()?.also { onMediaUri(uri) }
    }

    /**
     * Best-effort filesystem path for a SAF document ("primary:〇/x.zip" → "/storage/emulated/0/〇/x.zip"),
     * so the automation reply names a path 白い熊 can actually open. Falls back to the URI.
     */
    private fun displayPathOf(uri: Uri): String {
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return uri.toString()
        val volume = docId.substringBefore(':', "")
        val relative = docId.substringAfter(':', "")
        if (volume.isEmpty() || relative.isEmpty()) return uri.toString()
        val root = if (volume == "primary") {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volume"
        }
        return "$root/$relative"
    }

    /** (message, isWarning) describing the newest export in the chosen directory. Does IO — call off the main thread. */
    fun lastExportStatus(context: Context): Pair<String, Boolean> {
        val dir = exportDir(context) ?: return context.getString(R.string.eim_warn_nodir) to true
        val newest = runCatching {
            dir.listFiles().filter {
                it.isFile && it.name?.startsWith(EXPORT_PREFIX) == true && it.name?.endsWith(".zip") == true
            }.maxByOrNull { it.lastModified() }
        }.getOrNull() ?: return context.getString(R.string.eim_warn_none) to true
        return context.getString(R.string.eim_last, formatTimestamp(newest.lastModified())) to false
    }

    private fun formatTimestamp(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(ts))
}
