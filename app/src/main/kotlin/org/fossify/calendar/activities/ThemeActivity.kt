package org.fossify.calendar.activities

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import org.fossify.calendar.R
import org.fossify.calendar.databinding.ActivityThemeBinding
import org.fossify.calendar.databinding.ItemThemeColorBinding
import org.fossify.calendar.databinding.ItemThemeSectionBinding
import org.fossify.calendar.databinding.ItemThemeSliderBinding
import org.fossify.calendar.databinding.ItemThemeSubsectionBinding
import org.fossify.calendar.databinding.ItemThemeValueBinding
import org.fossify.calendar.extensions.ThemeGroup
import org.fossify.calendar.extensions.ThemeSlot
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.resetThemeColor
import org.fossify.calendar.extensions.setThemeColor
import org.fossify.calendar.extensions.themeColor
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_CENTER
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_END
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_START
import org.fossify.calendar.helpers.THEME_UNSET
import org.fossify.calendar.helpers.WEEKLY_STYLE_DAY_BOXES
import org.fossify.calendar.helpers.WEEKLY_STYLE_TIME_GRID
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.onSeekBarChangeListener
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.RadioItem

// The consolidated "白い熊 予定表 UI" screen. Built from a section -> (optional subsection) -> rows
// hierarchy, mirroring the layout used in shiroikuma-handyrss and shiroikuma-futokxkb: section
// headers carry a full-width accent underline, subsections a short one, and color rows show a
// "Default" badge while they follow their inherited default.
class ThemeActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThemeBinding::inflate)
    private val previews = HashMap<ThemeSlot, ImageView>()
    private val defaultBadges = HashMap<ThemeSlot, TextView>()
    private var textColor = 0
    private var primaryColor = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.themeNestedScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.themeNestedScrollview,
            topAppBar = binding.themeAppbar
        )
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(topAppBar = binding.themeAppbar, navigationIcon = NavigationIcon.Arrow)
        buildRows()
    }

    private fun buildRows() {
        binding.themeHolder.removeAllViews()
        previews.clear()
        defaultBadges.clear()
        textColor = getProperTextColor()
        primaryColor = getProperPrimaryColor()

        // Foundation — the base colors that cascade through the whole app.
        addSection(R.string.theme_group_foundation)
        slotsOf(ThemeGroup.FOUNDATION).forEach { addColorRow(it) }

        // Search bar.
        addSection(R.string.theme_group_search)
        slotsOf(ThemeGroup.SEARCH).forEach { addColorRow(it) }

        // Calendar — grouped into the weekly day-box view and the shared highlights/grid.
        addSection(R.string.theme_group_calendar)
        addSubsection(R.string.theme_sub_weekly_view)
        addWeeklyViewStyleRow()
        addSubsection(R.string.theme_sub_day_boxes)
        addColorRow(ThemeSlot.DAY_BOX_HEADER)
        addColorRow(ThemeSlot.DAY_BOX_HEADER_TEXT)
        addHeaderAlignmentRow()
        addColorRow(ThemeSlot.DAY_BOX_HEADER_BORDER)
        addThicknessRow(R.string.theme_day_box_header_border_thickness, config.dayBoxHeaderBorderThickness) {
            config.dayBoxHeaderBorderThickness = it
        }
        addColorRow(ThemeSlot.DAY_BOX_BORDER)
        addThicknessRow(R.string.theme_day_box_border_thickness, config.dayBoxBorderThickness) {
            config.dayBoxBorderThickness = it
        }
        addSubsection(R.string.theme_sub_highlights_grid)
        addColorRow(ThemeSlot.TODAY_HIGHLIGHT)
        addColorRow(ThemeSlot.WEEKEND)
        addColorRow(ThemeSlot.GRID_LINES)
    }

    private fun slotsOf(group: ThemeGroup) = ThemeSlot.entries.filter { it.group == group }

    private fun addSection(@StringRes titleRes: Int) {
        val section = ItemThemeSectionBinding.inflate(layoutInflater, binding.themeHolder, false)
        section.themeSectionLabel.text = getString(titleRes)
        section.themeSectionLabel.setTextColor(primaryColor)
        section.themeSectionUnderline.setBackgroundColor(primaryColor)
        binding.themeHolder.addView(section.root)
    }

    private fun addSubsection(@StringRes titleRes: Int) {
        val sub = ItemThemeSubsectionBinding.inflate(layoutInflater, binding.themeHolder, false)
        sub.themeSubsectionLabel.text = getString(titleRes)
        sub.themeSubsectionLabel.setTextColor(primaryColor)
        sub.themeSubsectionUnderline.setBackgroundColor(primaryColor)
        binding.themeHolder.addView(sub.root)
    }

    private fun addColorRow(slot: ThemeSlot) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeColorLabel.text = getString(slot.labelRes)
        row.themeColorLabel.setTextColor(textColor)
        row.themeColorPreview.background.setTint(themeColor(slot))
        row.themeColorDefault.setTextColor(textColor.adjustAlpha(0.6f))
        row.themeColorDefault.beVisibleIf(followsDefault(slot))
        row.root.setOnClickListener { openPicker(slot) }
        previews[slot] = row.themeColorPreview
        defaultBadges[slot] = row.themeColorDefault
        binding.themeHolder.addView(row.root)
    }

    private fun addWeeklyViewStyleRow() {
        val row = ItemThemeValueBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeValueLabel.text = getString(R.string.weekly_view_style)
        row.themeValueLabel.setTextColor(textColor)
        row.themeValueValue.setTextColor(textColor.adjustAlpha(0.6f))
        row.themeValueValue.text = weeklyStyleText()
        row.root.setOnClickListener { openWeeklyStylePicker(row.themeValueValue) }
        binding.themeHolder.addView(row.root)
    }

    /** A slot follows its inherited default when it has no explicit override (write-through slots never do). */
    private fun followsDefault(slot: ThemeSlot): Boolean {
        val isWriteThrough = slot.isFoundation || slot == ThemeSlot.WEEKEND
        return !isWriteThrough && config.getThemeOverride(slot.key) == THEME_UNSET
    }

    private fun openPicker(slot: ThemeSlot) {
        ColorPickerDialog(this, themeColor(slot), addDefaultColorButton = true) { wasPositive, color ->
            if (wasPositive) {
                setThemeColor(slot, color)
            } else {
                resetThemeColor(slot)
            }

            if (slot.isFoundation) {
                // foundation cascades into the chrome + every inheriting preview
                recreate()
            } else {
                previews[slot]?.background?.setTint(themeColor(slot))
                defaultBadges[slot]?.beVisibleIf(followsDefault(slot))
            }
        }
    }

    private fun openWeeklyStylePicker(valueView: TextView) {
        val items = arrayListOf(
            RadioItem(WEEKLY_STYLE_TIME_GRID, getString(R.string.weekly_style_time_grid)),
            RadioItem(WEEKLY_STYLE_DAY_BOXES, getString(R.string.weekly_style_day_boxes))
        )
        RadioGroupDialog(this, items, config.weeklyViewStyle) {
            config.weeklyViewStyle = it as Int
            valueView.text = weeklyStyleText()
        }
    }

    private fun weeklyStyleText() = getString(
        if (config.weeklyViewStyle == WEEKLY_STYLE_DAY_BOXES) {
            R.string.weekly_style_day_boxes
        } else {
            R.string.weekly_style_time_grid
        }
    )

    private fun addHeaderAlignmentRow() {
        val row = ItemThemeValueBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeValueLabel.text = getString(R.string.theme_day_box_header_align)
        row.themeValueLabel.setTextColor(textColor)
        row.themeValueValue.setTextColor(textColor.adjustAlpha(0.6f))
        row.themeValueValue.text = alignmentText()
        row.root.setOnClickListener { openHeaderAlignmentPicker(row.themeValueValue) }
        binding.themeHolder.addView(row.root)
    }

    private fun openHeaderAlignmentPicker(valueView: TextView) {
        val items = arrayListOf(
            RadioItem(DAY_BOX_ALIGN_START, getString(R.string.theme_align_left)),
            RadioItem(DAY_BOX_ALIGN_CENTER, getString(R.string.theme_align_center)),
            RadioItem(DAY_BOX_ALIGN_END, getString(R.string.theme_align_right))
        )
        RadioGroupDialog(this, items, config.dayBoxHeaderAlignment) {
            config.dayBoxHeaderAlignment = it as Int
            valueView.text = alignmentText()
        }
    }

    private fun alignmentText() = getString(
        when (config.dayBoxHeaderAlignment) {
            DAY_BOX_ALIGN_START -> R.string.theme_align_left
            DAY_BOX_ALIGN_CENTER -> R.string.theme_align_center
            else -> R.string.theme_align_right
        }
    )

    private fun addThicknessRow(@StringRes labelRes: Int, current: Int, max: Int = 12, onChange: (Int) -> Unit) {
        val row = ItemThemeSliderBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeSliderLabel.text = getString(labelRes)
        row.themeSliderLabel.setTextColor(textColor)
        row.themeSliderValue.setTextColor(textColor.adjustAlpha(0.6f))
        row.themeSliderValue.text = thicknessText(current)
        row.themeSliderSeekbar.max = max
        row.themeSliderSeekbar.progress = current
        row.themeSliderSeekbar.onSeekBarChangeListener {
            row.themeSliderValue.text = thicknessText(it)
            onChange(it)
        }
        binding.themeHolder.addView(row.root)
    }

    private fun thicknessText(dp: Int) = if (dp <= 0) {
        getString(R.string.theme_thickness_none)
    } else {
        getString(R.string.theme_thickness_value, dp)
    }
}
