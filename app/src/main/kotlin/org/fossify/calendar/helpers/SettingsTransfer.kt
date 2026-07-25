package org.fossify.calendar.helpers

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import org.fossify.calendar.BuildConfig
import org.fossify.calendar.R
import org.fossify.calendar.activities.SimpleActivity
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
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The category-based settings Export/Import behind the top section of 白い熊 予定表 UI (same idea and
 * flow as the Kōjiki page). The export is a ZIP of plain JSON files — one per category — plus any
 * imported font files as real files under `fonts/`; a `manifest.json` lists format, version and the
 * categories present. Every settable item lives in one category: prefs keys are routed by
 * [categoryOf] (unknown keys fall into GENERAL so nothing is ever silently dropped), local calendar
 * categories come from the Room DB. Import merges — selected categories only, absent files skipped,
 * unknown keys tolerated, rows upserted by title, never wiped.
 */
object SettingsTransfer {
    const val FORMAT = "yotehyo-export"
    const val VERSION = 1
    const val EXPORT_PREFIX = "shiroikuma-yotehyo-"
    const val WARN_COLOR = 0xFFFF5252.toInt() // warn-red for "no directory / no export yet" statuses

    private const val EXIMPORT_PREFS = "yotehyo_eximport" // device-local; deliberately never exported
    private const val KEY_DIR_URI = "dir_uri"

    /** A selectable export/import category. `id` names the category's file inside the ZIP. */
    enum class Category(val id: String, @StringRes val labelRes: Int) {
        EVENTS("events", R.string.eim_cat_events),
        GENERAL("general", R.string.eim_cat_general),
        UI_THEME("ui_theme", R.string.eim_cat_ui),
        WIDGETS("widgets", R.string.eim_cat_widgets),
        CALENDARS("calendars", R.string.eim_cat_calendars);

        companion object {
            fun byId(id: String): Category? = entries.firstOrNull { it.id == id }
        }
    }

    // Events/tasks travel as standard ICS (the upstream exporter/importer handles recurrences,
    // reminders and categories); everything else is a plain JSON file.
    private fun entryName(cat: Category) = if (cat == Category.EVENTS) "events.ics" else "${cat.id}.json"

    // Device-local keys never worth exporting: storage paths/SAF grants, version bookkeeping,
    // sideloading state, and the last-used export bookkeeping of the stock mechanism.
    private val DEVICE_LOCAL_KEYS = setOf(
        APP_ID, APP_RUN_COUNT, LAST_VERSION, APP_SIDELOADING_STATUS, INTERNAL_STORAGE_PATH,
        SD_CARD_PATH, OTG_REAL_PATH, OTG_PARTITION, WAS_OTG_HANDLED, SD_TREE_URI, OTG_TREE_URI,
        PRIMARY_ANDROID_DATA_TREE_URI, OTG_ANDROID_DATA_TREE_URI, SD_ANDROID_DATA_TREE_URI,
        PRIMARY_ANDROID_OBB_TREE_URI, OTG_ANDROID_OBB_TREE_URI, SD_ANDROID_OBB_TREE_URI,
        LAST_EXPORTED_SETTINGS_FOLDER, LAST_EXPORTED_SETTINGS_FILE, AUTO_BACKUP_FOLDER
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

    /** Write a ZIP of the selected categories to [out]. Returns a short human summary. */
    fun export(context: Context, cats: Set<Category>, out: OutputStream): String {
        var count = 0
        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("app", context.packageName)
                .put("createdTs", System.currentTimeMillis())
                .put("categories", JSONArray(cats.map { it.id }))
            writeEntry(zip, "manifest.json", manifest.toString(2))

            for (cat in cats) {
                val content = when (cat) {
                    Category.EVENTS -> exportEventsIcs(context)
                    Category.CALENDARS -> exportCalendars(context).toByteArray()
                    else -> exportPrefs(context, cat).toByteArray()
                }
                writeEntry(zip, entryName(cat), content)
                if (cat == Category.UI_THEME) {
                    exportFonts(context, zip)
                }
                count++
            }
        }
        return "$count categor${if (count == 1) "y" else "ies"}"
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
    private fun exportCalendars(context: Context): String {
        val arr = JSONArray()
        context.calendarsDB.getCalendars().filter { !it.isSyncedCalendar() }.forEach { cal ->
            arr.put(
                JSONObject()
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

    // Every local + synced calendar, all events and tasks, past included. The exporter's callback
    // runs synchronously because export() is always called from a background thread.
    private fun exportEventsIcs(context: Context): ByteArray {
        val calendarIds = context.calendarsDB.getCalendars().mapNotNull { it.id }
        val events = context.eventsHelper.getEventsToExport(
            calendars = calendarIds,
            exportEvents = true,
            exportTasks = true,
            exportPastEntries = true
        )
        val out = ByteArrayOutputStream()
        var exportResult: IcsExporter.ExportResult? = null
        IcsExporter(context).exportEvents(out, events, showExportingToast = false) { exportResult = it }
        if (exportResult == IcsExporter.ExportResult.EXPORT_FAIL) {
            error("events export failed")
        }
        return out.toByteArray()
    }

    // ---------- Import ----------

    /** Categories present in a ZIP (from its manifest, falling back to the `<id>.json` files found). */
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
        return Category.entries.filter { files.containsKey(entryName(it)) }.toSet()
    }

    /** Apply the selected categories from a ZIP. Missing files are skipped. Returns a human summary. */
    fun import(activity: SimpleActivity, zip: ByteArray, cats: Set<Category>): String {
        val files = readZip(zip)
        val parts = mutableListOf<String>()
        for (cat in cats) {
            val data = files[entryName(cat)] ?: continue
            val count = when (cat) {
                Category.EVENTS -> importEventsIcs(activity, data)
                Category.CALENDARS -> importCalendars(activity, data.decodeToString())
                else -> importPrefs(activity, cat, data.decodeToString())
            }
            if (cat == Category.UI_THEME) {
                importFonts(activity, files)
            }
            parts.add("${activity.getString(cat.labelRes)}: $count")
        }
        if (Category.UI_THEME in cats || Category.WIDGETS in cats) {
            // Repaint everything with the imported look on the next resume.
            activity.config.themeRevision = activity.config.themeRevision + 1
        }
        return if (parts.isEmpty()) "nothing imported" else parts.joinToString("\n")
    }

    // The upstream ICS importer needs a file path and a SimpleActivity; it upserts by import id
    // (UID), so re-importing the same export does not duplicate events. Returns the number of
    // entries the file carries.
    private fun importEventsIcs(activity: SimpleActivity, data: ByteArray): Int {
        if (data.isEmpty()) return 0
        val file = File(activity.cacheDir, "eim_import_events.ics")
        file.writeBytes(data)
        try {
            val result = IcsImporter(activity).importEvents(
                path = file.absolutePath,
                defaultCalendarId = defaultCalendarId(activity),
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
    // categoryOf so a tampered file cannot smuggle values into an unselected category.
    private fun importPrefs(context: Context, cat: Category, json: String): Int {
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
                "l" -> editor.putLong(key, e.optLong("v"))
                "f" -> editor.putFloat(key, e.optDouble("v").toFloat())
                "s" -> editor.putString(key, e.optString("v"))
                "ss" -> {
                    val arr = e.optJSONArray("v") ?: JSONArray()
                    editor.putStringSet(key, (0 until arr.length()).map { arr.optString(it) }.toSet())
                }

                else -> continue
            }
            count++
        }
        editor.apply()
        return count
    }

    // Upsert local calendar categories by title; existing ones keep their id (and their events).
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
            context.calendarsDB.insertOrUpdate(entity)
            count++
        }
        return count
    }

    private fun importFonts(context: Context, files: Map<String, ByteArray>) {
        files.forEach { (name, bytes) ->
            if (name.startsWith("fonts/")) {
                val fileName = File(name).name // strips any ../ traversal
                if (fileName.isNotEmpty()) {
                    FontHelper.saveFontData(context, bytes, fileName)
                }
            }
        }
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

    fun exportFileName(): String =
        EXPORT_PREFIX + BuildConfig.VERSION_NAME + "-export_" +
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

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
