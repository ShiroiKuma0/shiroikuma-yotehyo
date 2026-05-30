package org.fossify.calendar.activities

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import org.fossify.calendar.R
import org.fossify.calendar.databinding.ActivityThemeBinding
import org.fossify.calendar.databinding.ItemThemeColorBinding
import org.fossify.calendar.databinding.ItemThemeSectionBinding
import org.fossify.calendar.databinding.ItemThemeSliderBinding
import org.fossify.calendar.databinding.ItemThemeSubsectionBinding
import org.fossify.calendar.databinding.ItemThemeTextBinding
import org.fossify.calendar.databinding.ItemThemeValueBinding
import org.fossify.calendar.dialogs.AlphaColorPickerDialog
import org.fossify.calendar.dialogs.FontPickerDialog
import org.fossify.calendar.extensions.FontWeightOption
import org.fossify.calendar.extensions.ThemeGroup
import org.fossify.calendar.extensions.ThemeSlot
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.fontDisplayName
import org.fossify.calendar.extensions.importFont
import org.fossify.calendar.extensions.resetThemeColor
import org.fossify.calendar.extensions.setThemeColor
import org.fossify.calendar.extensions.showFontSample
import org.fossify.calendar.extensions.themeColor
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_CENTER
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_END
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_START
import org.fossify.calendar.helpers.DAY_BOX_THICKNESS_INHERIT
import org.fossify.calendar.helpers.MAX_FONT_SIZE_SP
import org.fossify.calendar.helpers.THEME_UNSET
import org.fossify.calendar.helpers.WEEKLY_STYLE_DAY_BOXES
import org.fossify.calendar.helpers.WEEKLY_STYLE_TIME_GRID
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.onSeekBarChangeListener
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.RadioItem

// The consolidated "白い熊 予定表 UI" screen. Built from a section -> (optional subsection) -> rows
// hierarchy: section headers carry a full-width accent underline, subsections a short one, color
// rows show a "Default" badge while inherited, and text elements get a font / weight / size block
// with a live sample beneath. Every colour is picked with an alpha-enabled dialog.
@Suppress("TooManyFunctions")
class ThemeActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThemeBinding::inflate)
    private val previews = HashMap<ThemeSlot, ImageView>()
    private val defaultBadges = HashMap<ThemeSlot, TextView>()
    private var textColor = 0
    private var primaryColor = 0
    private var indentStepPx = 0
    private var currentRowIndent = 0

    private var pendingFontSlot: ThemeSlot? = null
    private var pendingFontBinding: ItemThemeTextBinding? = null

    private val fontImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onFontImported(uri)
    }

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
        indentStepPx = 3 * resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        currentRowIndent = 0

        // Foundation — the base colors that cascade through the whole app.
        addSection(R.string.theme_group_foundation)
        slotsOf(ThemeGroup.FOUNDATION).forEach { addSlot(it) }

        // Search bar.
        addSection(R.string.theme_group_search)
        slotsOf(ThemeGroup.SEARCH).forEach { addSlot(it) }

        // Calendar — grouped by surface.
        addSection(R.string.theme_group_calendar)
        addSubsection(R.string.theme_sub_weekly_view)
        addWeeklyViewStyleRow()
        addSubsection(R.string.theme_sub_events)
        addSlot(ThemeSlot.EVENT_TEXT)
        addSubsection(R.string.theme_sub_day_boxes)
        addSlot(ThemeSlot.DAY_BOX_HEADER)
        addSlot(ThemeSlot.DAY_BOX_HEADER_TEXT)
        addHeaderAlignmentRow()
        addSlot(ThemeSlot.DAY_BOX_HEADER_BORDER)
        addThicknessRow(R.string.theme_day_box_header_border_thickness, config.dayBoxHeaderBorderThickness) {
            config.dayBoxHeaderBorderThickness = it
        }
        addSlot(ThemeSlot.DAY_BOX_BORDER)
        addThicknessRow(R.string.theme_day_box_border_thickness, config.dayBoxBorderThickness) {
            config.dayBoxBorderThickness = it
        }
        addSubsection(R.string.theme_sub_today_boxes)
        addSlot(ThemeSlot.TODAY_BOX_FILL)
        addSlot(ThemeSlot.TODAY_TEXT)
        addSlot(ThemeSlot.TODAY_HEADER_BORDER)
        addThicknessRow(R.string.theme_today_header_border_thickness, config.todayHeaderBorderThickness, DAY_BOX_THICKNESS_INHERIT) {
            config.todayHeaderBorderThickness = it
        }
        addSlot(ThemeSlot.TODAY_BOX_BORDER)
        addThicknessRow(R.string.theme_today_box_border_thickness, config.todayBoxBorderThickness, DAY_BOX_THICKNESS_INHERIT) {
            config.todayBoxBorderThickness = it
        }
        addSubsection(R.string.theme_sub_weekend_boxes)
        addSlot(ThemeSlot.WEEKEND_TEXT)
        addSlot(ThemeSlot.WEEKEND_HEADER_BORDER)
        addThicknessRow(R.string.theme_weekend_header_border_thickness, config.weekendHeaderBorderThickness, DAY_BOX_THICKNESS_INHERIT) {
            config.weekendHeaderBorderThickness = it
        }
        addSlot(ThemeSlot.WEEKEND_BOX_BORDER)
        addThicknessRow(R.string.theme_weekend_box_border_thickness, config.weekendBoxBorderThickness, DAY_BOX_THICKNESS_INHERIT) {
            config.weekendBoxBorderThickness = it
        }
        addSubsection(R.string.theme_sub_highlights_grid)
        addSlot(ThemeSlot.TODAY_HIGHLIGHT)
        addSlot(ThemeSlot.WEEKEND)
        addSlot(ThemeSlot.GRID_LINES)
    }

    private fun slotsOf(group: ThemeGroup) = ThemeSlot.entries.filter { it.group == group }

    private fun addSlot(slot: ThemeSlot) {
        if (slot.hasFont) addTextSlot(slot) else addColorRow(slot)
    }

    // Left-indent a row by `level` steps so the section -> subsection -> rows hierarchy is visible.
    private fun indentView(view: View, level: Int) {
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.marginStart = level * indentStepPx
            view.layoutParams = it
        }
    }

    private fun addSection(@StringRes titleRes: Int) {
        val section = ItemThemeSectionBinding.inflate(layoutInflater, binding.themeHolder, false)
        section.themeSectionLabel.text = getString(titleRes)
        section.themeSectionLabel.setTextColor(primaryColor)
        section.themeSectionUnderline.setBackgroundColor(primaryColor)
        indentView(section.root, 0)
        binding.themeHolder.addView(section.root)
        currentRowIndent = 1
    }

    private fun addSubsection(@StringRes titleRes: Int) {
        val sub = ItemThemeSubsectionBinding.inflate(layoutInflater, binding.themeHolder, false)
        sub.themeSubsectionLabel.text = getString(titleRes)
        sub.themeSubsectionLabel.setTextColor(primaryColor)
        sub.themeSubsectionUnderline.setBackgroundColor(primaryColor)
        indentView(sub.root, 1)
        binding.themeHolder.addView(sub.root)
        currentRowIndent = 2
    }

    private fun addColorRow(slot: ThemeSlot) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeColorLabel.text = getString(slot.labelRes)
        row.themeColorLabel.setTextColor(textColor)
        row.themeColorPreview.background.setTint(themeColor(slot))
        row.themeColorDefault.setTextColor(textColor.adjustAlpha(0.6f))
        row.themeColorDefault.beVisibleIf(followsDefault(slot))
        row.root.setOnClickListener { openColorPicker(slot) }
        previews[slot] = row.themeColorPreview
        defaultBadges[slot] = row.themeColorDefault
        indentView(row.root, currentRowIndent)
        binding.themeHolder.addView(row.root)
    }

    private fun addTextSlot(slot: ThemeSlot) {
        val b = ItemThemeTextBinding.inflate(layoutInflater, binding.themeHolder, false)
        b.themeTextLabel.text = getString(slot.labelRes)
        listOf(
            b.themeTextLabel, b.themeTextFontTitle, b.themeTextFontValue,
            b.themeTextWeightTitle, b.themeTextWeightValue, b.themeTextSizeTitle, b.themeTextSizeValue
        ).forEach { it.setTextColor(textColor) }

        b.themeTextColorPreview.background.setTint(themeColor(slot))
        b.themeTextFontValue.text = fontDisplayName(config.getFontFamily(slot.key))
        b.themeTextWeightValue.text = getString(FontWeightOption.fromValue(config.getFontWeight(slot.key)).labelRes)
        b.themeTextSizeSeekbar.max = MAX_FONT_SIZE_SP
        b.themeTextSizeSeekbar.progress = config.getFontSize(slot.key)
        b.themeTextSizeValue.text = sizeLabel(config.getFontSize(slot.key))
        refreshSample(b, slot)

        b.themeTextColorRow.setOnClickListener { openTextColorPicker(slot, b) }
        b.themeTextFontRow.setOnClickListener { openFontPicker(slot, b) }
        b.themeTextWeightRow.setOnClickListener { openWeightPicker(slot, b) }
        b.themeTextSizeSeekbar.onSeekBarChangeListener {
            config.setFontSize(slot.key, it)
            b.themeTextSizeValue.text = sizeLabel(it)
            refreshSample(b, slot)
        }

        // The font / weight / size / sample belong to this element, so sit one full step deeper
        // than its own colour row.
        listOf<View>(b.themeTextFontRow, b.themeTextWeightRow, b.themeTextSizeRow, b.themeTextSample).forEach { sub ->
            (sub.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.marginStart = indentStepPx
                sub.layoutParams = it
            }
        }

        indentView(b.root, currentRowIndent)
        binding.themeHolder.addView(b.root)
    }

    private fun addWeeklyViewStyleRow() {
        val row = ItemThemeValueBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeValueLabel.text = getString(R.string.weekly_view_style)
        row.themeValueLabel.setTextColor(textColor)
        row.themeValueValue.setTextColor(textColor.adjustAlpha(0.6f))
        row.themeValueValue.text = weeklyStyleText()
        row.root.setOnClickListener { openWeeklyStylePicker(row.themeValueValue) }
        indentView(row.root, currentRowIndent)
        binding.themeHolder.addView(row.root)
    }

    /** A slot follows its inherited default when it has no explicit override (write-through slots never do). */
    private fun followsDefault(slot: ThemeSlot): Boolean {
        val isWriteThrough = slot.isFoundation || slot == ThemeSlot.WEEKEND
        return !isWriteThrough && config.getThemeOverride(slot.key) == THEME_UNSET
    }

    private fun refreshSample(b: ItemThemeTextBinding, slot: ThemeSlot) {
        b.themeTextSample.showFontSample(
            config.getFontFamily(slot.key),
            config.getFontWeight(slot.key),
            config.getFontSize(slot.key),
            themeColor(slot)
        )
    }

    private fun sizeLabel(sp: Int) = if (sp > 0) "$sp sp" else getString(R.string.theme_size_default)

    private fun openColorPicker(slot: ThemeSlot) {
        AlphaColorPickerDialog(this, themeColor(slot), addDefaultColorButton = true) { wasPositive, color ->
            if (wasPositive) setThemeColor(slot, color) else resetThemeColor(slot)
            if (slot.isFoundation) {
                // foundation cascades into the chrome + every inheriting preview
                recreate()
            } else {
                previews[slot]?.background?.setTint(themeColor(slot))
                defaultBadges[slot]?.beVisibleIf(followsDefault(slot))
            }
        }
    }

    private fun openTextColorPicker(slot: ThemeSlot, b: ItemThemeTextBinding) {
        AlphaColorPickerDialog(this, themeColor(slot), addDefaultColorButton = true) { wasPositive, color ->
            if (wasPositive) setThemeColor(slot, color) else resetThemeColor(slot)
            if (slot.isFoundation) {
                recreate()
            } else {
                b.themeTextColorPreview.background.setTint(themeColor(slot))
                refreshSample(b, slot)
            }
        }
    }

    private fun openFontPicker(slot: ThemeSlot, b: ItemThemeTextBinding) {
        FontPickerDialog(
            activity = this,
            onAddFont = {
                pendingFontSlot = slot
                pendingFontBinding = b
                fontImportLauncher.launch(arrayOf("*/*"))
            },
            onPick = { fileName ->
                config.setFontFamily(slot.key, fileName)
                b.themeTextFontValue.text = fontDisplayName(fileName)
                refreshSample(b, slot)
            }
        )
    }

    private fun openWeightPicker(slot: ThemeSlot, b: ItemThemeTextBinding) {
        val items = ArrayList(FontWeightOption.entries.map { RadioItem(it.value, getString(it.labelRes)) })
        RadioGroupDialog(this, items, config.getFontWeight(slot.key)) {
            val weight = it as Int
            config.setFontWeight(slot.key, weight)
            b.themeTextWeightValue.text = getString(FontWeightOption.fromValue(weight).labelRes)
            refreshSample(b, slot)
        }
    }

    private fun onFontImported(uri: Uri?) {
        val slot = pendingFontSlot
        val b = pendingFontBinding
        pendingFontSlot = null
        pendingFontBinding = null
        if (uri == null || slot == null) {
            return
        }

        val fileName = importFont(uri)
        if (fileName == null) {
            toast(R.string.font_invalid)
            return
        }

        config.setFontFamily(slot.key, fileName)
        b?.themeTextFontValue?.text = fontDisplayName(fileName)
        if (b != null) {
            refreshSample(b, slot)
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
        indentView(row.root, currentRowIndent)
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

    private fun addThicknessRow(@StringRes labelRes: Int, current: Int, min: Int = 0, max: Int = 12, onChange: (Int) -> Unit) {
        val row = ItemThemeSliderBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeSliderLabel.text = getString(labelRes)
        row.themeSliderLabel.setTextColor(textColor)
        row.themeSliderValue.setTextColor(textColor.adjustAlpha(0.6f))
        row.themeSliderValue.text = thicknessText(current)
        row.themeSliderSeekbar.min = min
        row.themeSliderSeekbar.max = max
        row.themeSliderSeekbar.progress = current
        row.themeSliderSeekbar.onSeekBarChangeListener {
            row.themeSliderValue.text = thicknessText(it)
            onChange(it)
        }
        indentView(row.root, currentRowIndent)
        binding.themeHolder.addView(row.root)
    }

    // < 0 = inherit the general thickness, 0 = no border, otherwise the dp value.
    private fun thicknessText(dp: Int) = when {
        dp < 0 -> getString(R.string.theme_default_value)
        dp == 0 -> getString(R.string.theme_thickness_none)
        else -> getString(R.string.theme_thickness_value, dp)
    }
}
