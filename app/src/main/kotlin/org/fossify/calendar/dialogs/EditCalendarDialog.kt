package org.fossify.calendar.dialogs

import android.app.Activity
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import org.fossify.calendar.R
import org.fossify.calendar.databinding.DialogCalendarBinding
import org.fossify.calendar.extensions.FontWeightOption
import org.fossify.calendar.extensions.calDAVHelper
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.extensions.fontDisplayName
import org.fossify.calendar.extensions.showFontSample
import org.fossify.calendar.helpers.OTHER_EVENT
import org.fossify.calendar.helpers.THEME_UNSET
import org.fossify.calendar.models.CalendarEntity
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.setFillWithStroke
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.RadioItem

class EditCalendarDialog(
    val activity: Activity,
    var calendar: CalendarEntity? = null,
    val callback: (calendar: CalendarEntity) -> Unit
) {
    private var isNewEvent = calendar == null
    private val binding by activity.viewBinding(DialogCalendarBinding::inflate)

    init {
        if (calendar == null) {
            calendar = CalendarEntity(null, "", activity.getProperPrimaryColor())
        }

        binding.apply {
            setupColor(typeColor)
            setupBackgroundColor(typeBackgroundColor)
            typeTitle.setText(calendar!!.title)
            typeColor.setOnClickListener {
                if (calendar?.caldavCalendarId == 0) {
                    ColorPickerDialog(
                        activity = activity,
                        color = calendar!!.color
                    ) { wasPositivePressed, color ->
                        if (wasPositivePressed) {
                            calendar!!.color = color
                            setupColor(typeColor)
                            refreshSample()
                        }
                    }
                } else {
                    val currentColor = calendar!!.color
                    val colors =
                        activity.calDAVHelper.getAvailableCalDAVCalendarColors(calendar!!).keys.toIntArray()
                    SelectCalendarColorDialog(
                        activity = activity,
                        colors = colors,
                        currentColor = currentColor
                    ) {
                        calendar!!.color = it
                        setupColor(typeColor)
                        refreshSample()
                    }
                }
            }

            typeBackgroundColor.setOnClickListener {
                val current = if (calendar!!.backgroundColor == THEME_UNSET) {
                    activity.getProperBackgroundColor()
                } else {
                    calendar!!.backgroundColor
                }
                AlphaColorPickerDialog(activity, current, addDefaultColorButton = true) { wasPositive, color ->
                    calendar!!.backgroundColor = if (wasPositive) color else THEME_UNSET
                    setupBackgroundColor(typeBackgroundColor)
                    refreshSample()
                }
            }

            typeFontRow.setOnClickListener {
                FontPickerDialog(
                    activity = activity,
                    onAddFont = { activity.toast(R.string.category_add_font_hint) },
                    onPick = { fileName ->
                        calendar!!.fontFamily = fileName
                        typeFontValue.text = activity.fontDisplayName(fileName)
                        refreshSample()
                    }
                )
            }

            typeWeightRow.setOnClickListener {
                val items = ArrayList(FontWeightOption.entries.map { RadioItem(it.value, activity.getString(it.labelRes)) })
                RadioGroupDialog(activity, items, calendar!!.fontWeight) {
                    calendar!!.fontWeight = it as Int
                    typeWeightValue.text = activity.getString(FontWeightOption.fromValue(calendar!!.fontWeight).labelRes)
                    refreshSample()
                }
            }

            typeSizeRow.setOnClickListener {
                val sizes = listOf(0, 10, 12, 14, 16, 18, 20, 24, 28, 32)
                val items = ArrayList(sizes.map { RadioItem(it, sizeLabel(it)) })
                RadioGroupDialog(activity, items, calendar!!.fontSize) {
                    calendar!!.fontSize = it as Int
                    typeSizeValue.text = sizeLabel(calendar!!.fontSize)
                    refreshSample()
                }
            }

            typeFontValue.text = activity.fontDisplayName(calendar!!.fontFamily)
            typeWeightValue.text = activity.getString(FontWeightOption.fromValue(calendar!!.fontWeight).labelRes)
            typeSizeValue.text = sizeLabel(calendar!!.fontSize)
            refreshSample()
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(
                    view = binding.root,
                    dialog = this,
                    titleId = if (isNewEvent) R.string.add_new_type else R.string.edit_type
                ) { alertDialog ->
                    alertDialog.showKeyboard(binding.typeTitle)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        ensureBackgroundThread {
                            calendarConfirmed(binding.typeTitle.value, alertDialog)
                        }
                    }
                }
            }
    }

    private fun setupColor(view: ImageView) {
        view.setFillWithStroke(calendar!!.color, activity.getProperBackgroundColor())
    }

    private fun setupBackgroundColor(view: ImageView) {
        val shown = if (calendar!!.backgroundColor == THEME_UNSET) {
            activity.getProperBackgroundColor()
        } else {
            calendar!!.backgroundColor
        }
        view.setFillWithStroke(shown, activity.getProperBackgroundColor())
    }

    private fun sizeLabel(sp: Int) = if (sp > 0) "$sp sp" else activity.getString(R.string.theme_size_default)

    // Preview the category's text in its own colour + font on its background.
    private fun refreshSample() {
        binding.typeSample.showFontSample(
            calendar!!.fontFamily,
            calendar!!.fontWeight,
            calendar!!.fontSize,
            calendar!!.color
        )
        if (calendar!!.backgroundColor != THEME_UNSET) {
            binding.typeSample.setBackgroundColor(calendar!!.backgroundColor)
        } else {
            binding.typeSample.background = null
        }
    }

    private fun calendarConfirmed(title: String, dialog: AlertDialog) {
        val calendarClass = calendar?.type ?: OTHER_EVENT
        val calendarId = if (calendarClass == OTHER_EVENT) {
            activity.eventsHelper.getCalendarIdWithTitle(title)
        } else {
            activity.eventsHelper.getCalendarIdWithClass(calendarClass)
        }

        var isCalendarTitleTaken = isNewEvent && calendarId != -1L
        if (!isCalendarTitleTaken) {
            isCalendarTitleTaken = !isNewEvent && calendar!!.id != calendarId && calendarId != -1L
        }

        if (title.isEmpty()) {
            activity.toast(R.string.title_empty)
            return
        } else if (isCalendarTitleTaken) {
            activity.toast(R.string.type_already_exists)
            return
        }

        calendar!!.title = title
        if (calendar!!.caldavCalendarId != 0) {
            calendar!!.caldavDisplayName = title
        }

        calendar!!.id = activity.eventsHelper.insertOrUpdateCalendarSync(calendar!!)

        if (calendar!!.id != -1L) {
            activity.runOnUiThread {
                dialog.dismiss()
                callback(calendar!!)
            }
        } else {
            activity.toast(R.string.editing_calendar_failed)
        }
    }
}
