package org.fossify.calendar.extensions

import org.fossify.commons.helpers.uiColor
import org.fossify.commons.helpers.UiRole
import android.widget.TextView
import android.view.View
import android.content.Context
import android.app.Activity
import android.net.Uri
import org.fossify.calendar.BuildConfig
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.dialogs.CustomEventRepeatIntervalDialog
import org.fossify.calendar.dialogs.ImportEventsDialog
import org.fossify.calendar.dialogs.KeyboardTimePickerDialog
import org.fossify.calendar.helpers.*
import org.fossify.calendar.models.Event
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.RadioItem
import java.io.File
import java.io.FileOutputStream
import java.util.TreeSet

// Show a keyboard-entry time picker: HH:MM as four single-digit black/yellow boxes you type into (not
// the circular clock). Used for every event / task / default-time picker in 白い熊 予定表. See
// KeyboardTimePickerDialog for the type-four-digits-and-overwrite behaviour.
fun BaseSimpleActivity.showKeyboardTimePicker(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    callback: (hour: Int, minute: Int) -> Unit,
) {
    KeyboardTimePickerDialog(this, hour, minute, is24Hour, callback)
}

fun BaseSimpleActivity.shareEvents(ids: List<Long>) {
    ensureBackgroundThread {
        val file = getTempFile()
        if (file == null) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return@ensureBackgroundThread
        }

        val events = eventsDB.getEventsOrTasksWithIds(ids) as ArrayList<Event>
        if (events.isEmpty()) {
            toast(org.fossify.commons.R.string.no_items_found)
        }

        getFileOutputStream(file.toFileDirItem(this), true) {
            IcsExporter(this).exportEvents(it, events, false) { result ->
                if (result == IcsExporter.ExportResult.EXPORT_OK) {
                    sharePathIntent(file.absolutePath, BuildConfig.APPLICATION_ID)
                }
            }
        }
    }
}

fun BaseSimpleActivity.getTempFile(): File? {
    val folder = File(cacheDir, "events")
    if (!folder.exists()) {
        if (!folder.mkdir()) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return null
        }
    }

    return File(folder, "events.ics")
}

fun Activity.showEventRepeatIntervalDialog(curSeconds: Int, callback: (minutes: Int) -> Unit) {
    hideKeyboard()
    val seconds = TreeSet<Int>()
    seconds.apply {
        add(0)
        add(DAY)
        add(WEEK)
        add(MONTH)
        add(YEAR)
        add(curSeconds)
    }

    val items = ArrayList<RadioItem>(seconds.size + 1)
    seconds.mapIndexedTo(items) { index, value ->
        RadioItem(index, getRepetitionText(value), value)
    }

    var selectedIndex = 0
    seconds.forEachIndexed { index, value ->
        if (value == curSeconds)
            selectedIndex = index
    }

    items.add(RadioItem(-1, getString(org.fossify.commons.R.string.custom)))

    RadioGroupDialog(this, items, selectedIndex) {
        if (it == -1) {
            CustomEventRepeatIntervalDialog(this) {
                callback(it)
            }
        } else {
            callback(it as Int)
        }
    }
}

fun SimpleActivity.tryImportEventsFromFile(uri: Uri, callback: (Boolean) -> Unit = {}) {
    when (uri.scheme) {
        "file" -> showImportEventsDialog(uri.path!!, callback)
        "content" -> {
            val tempFile = getTempFile()
            if (tempFile == null) {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                return
            }

            try {
                val inputStream = contentResolver.openInputStream(uri)
                val out = FileOutputStream(tempFile)
                inputStream!!.copyTo(out)
                showImportEventsDialog(tempFile.absolutePath, callback)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }

        else -> toast(org.fossify.commons.R.string.invalid_file_format)
    }
}

fun SimpleActivity.showImportEventsDialog(path: String, callback: (Boolean) -> Unit) {
    ImportEventsDialog(this, path, callback)
}

/**
 * The controls of a widget configuration screen sit on the wallpaper, so each needs a ground of its own:
 * the save button is the screen's main pill, the transparency slider lies in a thin one.
 */
fun Context.styleWidgetConfigControls(save: TextView, sliderHolder: View) {
    save.setTextColor(uiColor(UiRole.BUTTON_TEXT))
    save.background = pillDrawable(uiColor(UiRole.BUTTON_FRAME), uiColor(UiRole.BUTTON_GROUND), main = true)
    sliderHolder.background = pillDrawable(uiColor(UiRole.BUTTON_FRAME), uiColor(UiRole.BACKGROUND), main = false)
}
