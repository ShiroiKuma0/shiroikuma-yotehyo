package org.fossify.calendar.dialogs

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import org.fossify.calendar.databinding.DialogKeyboardTimePickerBinding
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.viewBinding
import java.text.DateFormatSymbols

// Keyboard-entry time picker: HH:MM as four single-digit cells (a 2×2 grid of black/yellow boxes)
// instead of the circular clock. Each cell holds one digit; the focused cell inverts (yellow fill,
// black digit) and its content is auto-selected, so you just type four digits straight through —
// every keystroke overwrites the current cell and advances to the next, no backspacing. Backspace on
// an empty cell steps back. In 12-hour mode an AM/PM toggle is shown. Captions under the boxes
// localize (時 / 分 in Japanese) via string resources.
class KeyboardTimePickerDialog(
    val activity: Activity,
    hour: Int,
    minute: Int,
    private val is24Hour: Boolean,
    val callback: (hour: Int, minute: Int) -> Unit,
) {
    private val binding by activity.viewBinding(DialogKeyboardTimePickerBinding::inflate)
    private var dialog: AlertDialog? = null
    private var isPm = hour >= 12

    private val cells: List<AppCompatEditText> by lazy {
        listOf(
            binding.timePickerHourTens,
            binding.timePickerHourUnits,
            binding.timePickerMinuteTens,
            binding.timePickerMinuteUnits,
        )
    }

    init {
        val displayHour = if (is24Hour) hour else to12Hour(hour)
        prefill(displayHour, minute)
        setupAmPm()
        setupCellBehavior()

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> confirmTime() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    dialog = alertDialog
                    // Focus the first cell and select its digit so typing immediately overwrites it.
                    alertDialog.showKeyboard(binding.timePickerHourTens)
                    binding.timePickerHourTens.selectAll()
                }
            }
    }

    // 24-hour value -> 1..12 for display in 12-hour mode (0 and 12 both show as 12).
    private fun to12Hour(hour24: Int): Int {
        val h = hour24 % 12
        return if (h == 0) 12 else h
    }

    private fun prefill(hour: Int, minute: Int) {
        // Set before the watchers are attached (setupCellBehavior runs after), so no auto-advance fires.
        binding.timePickerHourTens.setText((hour / 10).toString())
        binding.timePickerHourUnits.setText((hour % 10).toString())
        binding.timePickerMinuteTens.setText((minute / 10).toString())
        binding.timePickerMinuteUnits.setText((minute % 10).toString())
    }

    private fun setupAmPm() {
        if (is24Hour) {
            binding.timePickerAmpmHolder.visibility = View.GONE
            return
        }

        binding.timePickerAmpmHolder.visibility = View.VISIBLE
        updateAmPmLabel()
        binding.timePickerAmpm.setOnClickListener {
            isPm = !isPm
            updateAmPmLabel()
        }
    }

    private fun updateAmPmLabel() {
        val amPm = DateFormatSymbols.getInstance().amPmStrings
        binding.timePickerAmpm.text = if (isPm) amPm[1] else amPm[0]
    }

    private fun setupCellBehavior() {
        cells.forEachIndexed { index, cell ->
            // Re-select the digit whenever a cell gains focus, so the next keystroke overwrites it.
            cell.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    cell.post { cell.selectAll() }
                }
            }

            // A digit was entered -> jump to the next cell (which re-selects via the focus listener).
            cell.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!s.isNullOrEmpty() && index < cells.lastIndex) {
                        cells[index + 1].requestFocus()
                    }
                }
            })

            // Backspace on an already-empty cell steps back to (and re-selects) the previous one.
            cell.setOnKeyListener { _, keyCode, event ->
                if (
                    keyCode == KeyEvent.KEYCODE_DEL &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    cell.text.isNullOrEmpty() &&
                    index > 0
                ) {
                    cells[index - 1].requestFocus()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun confirmTime() {
        val typedHour = digit(binding.timePickerHourTens) * 10 + digit(binding.timePickerHourUnits)
        val minute = (digit(binding.timePickerMinuteTens) * 10 + digit(binding.timePickerMinuteUnits))
            .coerceIn(0, 59)

        val hour = if (is24Hour) {
            typedHour.coerceIn(0, 23)
        } else {
            val h12 = typedHour.coerceIn(1, 12) % 12
            if (isPm) h12 + 12 else h12
        }

        callback(hour, minute)
        activity.hideKeyboard()
        dialog?.dismiss()
    }

    private fun digit(cell: AppCompatEditText): Int = cell.text?.toString()?.trim()?.toIntOrNull() ?: 0
}
