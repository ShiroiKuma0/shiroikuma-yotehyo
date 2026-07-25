package org.fossify.calendar.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import org.fossify.calendar.R
import org.fossify.calendar.databinding.ActivityThemeBinding
import org.fossify.calendar.databinding.DialogDayBoxDateFormatBinding
import org.fossify.calendar.databinding.ItemThemeColorBinding
import org.fossify.calendar.databinding.ItemThemeSectionBinding
import org.fossify.calendar.databinding.ItemThemeSliderBinding
import org.fossify.calendar.databinding.ItemThemeSubsectionBinding
import org.fossify.calendar.databinding.ItemThemeTextBinding
import org.fossify.calendar.databinding.ItemThemeToggleBinding
import org.fossify.calendar.databinding.ItemThemeTokenBinding
import org.fossify.calendar.databinding.ItemThemeValueBinding
import org.fossify.calendar.dialogs.AlphaColorPickerDialog
import org.fossify.calendar.dialogs.ExportImportDialog
import org.fossify.calendar.dialogs.FontPickerDialog
import org.fossify.calendar.extensions.FontWeightOption
import org.fossify.calendar.extensions.ThemeGroup
import org.fossify.calendar.extensions.ThemeSlot
import org.fossify.calendar.extensions.WeekGridLine
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.effectiveFontFamily
import org.fossify.calendar.extensions.effectiveFontSize
import org.fossify.calendar.extensions.effectiveFontWeight
import org.fossify.calendar.extensions.fontDisplayName
import org.fossify.calendar.extensions.formatEventTimeRange
import org.fossify.calendar.extensions.gridLineColor
import org.fossify.calendar.extensions.importFont
import org.fossify.calendar.extensions.resetThemeColor
import org.fossify.calendar.extensions.setThemeColor
import org.fossify.calendar.extensions.showFontSample
import org.fossify.calendar.extensions.themeColor
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_CENTER
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_END
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_START
import org.fossify.calendar.helpers.DAY_BOX_HEADER_FORMAT_JAPANESE
import org.fossify.calendar.helpers.DAY_BOX_HEADER_FORMAT_JAPANESE_ERA
import org.fossify.calendar.helpers.EVENT_TIME_FORMAT_JAPANESE
import org.fossify.calendar.helpers.DAY_BOX_THICKNESS_INHERIT
import org.fossify.calendar.helpers.MAX_FONT_SIZE_SP
import org.fossify.calendar.helpers.PALETTE_YELLOW
import org.fossify.calendar.helpers.SettingsTransfer
import org.fossify.calendar.helpers.THEME_UNSET
import org.fossify.calendar.helpers.formatDayBoxHeader
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.onSeekBarChangeListener
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.RadioItem

// The consolidated "白い熊 予定表 UI" screen. Built from a section -> (optional subsection) -> rows
// hierarchy: section headers carry a full-width accent underline, subsections a short one, color
// rows show a "Default" badge while inherited, and text elements get a font / weight / size block
// with a live sample beneath. Every colour is picked with an alpha-enabled dialog.
@Suppress("TooManyFunctions")
class ThemeActivity : SimpleActivity() {
    companion object {
        // How many hex characters of the automation token stay visible at each end.
        private const val TOKEN_VISIBLE_CHARS = 8
    }

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

    // Export/Import (top section): the SAF pickers live here — launchers must be registered before
    // RESUMED — and feed their results into the currently open panel.
    private var eximDialog: ExportImportDialog? = null

    private val eximDirPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        eximDialog?.onDirPicked(uri)
    }

    private val eximImportPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        eximDialog?.onImportFilePicked(uri)
    }

    private val eximExportCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        eximDialog?.onExportTargetPicked(uri)
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
        applyTopBarForeground(binding.themeAppbar)
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

        // Export / Import — the first, separated section (same idea and flow as the Kōjiki page).
        addSection(R.string.eim_heading)
        addExportImportRow()
        // Automation sits directly below the export row it drives — the placement every sister app
        // shares, so 白い熊 finds it where backup lives.
        addSubsection(R.string.automation)
        addAutomationRows()

        // Foundation — the base colors that cascade through the whole app.
        addSection(R.string.theme_group_foundation)
        slotsOf(ThemeGroup.FOUNDATION).forEach { addSlot(it) }

        // Search bar.
        addSection(R.string.theme_group_search)
        slotsOf(ThemeGroup.SEARCH).forEach { addSlot(it) }

        // Top bar action/overflow icons, overflow-menu text, and the Settings toolbar foreground.
        addSection(R.string.theme_group_chrome)
        slotsOf(ThemeGroup.CHROME).forEach { addSlot(it) }

        // Calendar — grouped by surface.
        addSection(R.string.theme_group_calendar)
        addSubsection(R.string.theme_sub_weekly_view)
        addToggleRow(R.string.theme_show_time_grid_weekly, config.showTimeGridWeekly) {
            config.showTimeGridWeekly = it
        }
        addToggleRow(R.string.theme_show_box_grid_weekly, config.showBoxGridWeekly) {
            config.showBoxGridWeekly = it
        }
        addSubsection(R.string.theme_sub_events)
        addSlot(ThemeSlot.EVENT_TEXT)
        addEventTimeFormatRow()
        addSubsection(R.string.theme_sub_day_boxes)
        addSlot(ThemeSlot.DAY_BOX_HEADER)
        addSlot(ThemeSlot.DAY_BOX_HEADER_TEXT)
        addHeaderAlignmentRow()
        addDateHeaderFormatRow()
        addSlot(ThemeSlot.DAY_BOX_HEADER_BORDER)
        addThicknessRow(R.string.theme_day_box_header_border_thickness, config.dayBoxHeaderBorderThickness) {
            config.dayBoxHeaderBorderThickness = it
        }
        addSlot(ThemeSlot.DAY_BOX_BORDER)
        addThicknessRow(R.string.theme_day_box_border_thickness, config.dayBoxBorderThickness) {
            config.dayBoxBorderThickness = it
        }
        addSubsection(R.string.theme_sub_today_boxes)
        addSlot(ThemeSlot.TODAY_HIGHLIGHT)
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
        addSlot(ThemeSlot.WEEKEND)
        addSlot(ThemeSlot.WEEKEND_TEXT)
        addSlot(ThemeSlot.WEEKEND_HEADER_BORDER)
        addThicknessRow(R.string.theme_weekend_header_border_thickness, config.weekendHeaderBorderThickness, DAY_BOX_THICKNESS_INHERIT) {
            config.weekendHeaderBorderThickness = it
        }
        addSlot(ThemeSlot.WEEKEND_BOX_BORDER)
        addThicknessRow(R.string.theme_weekend_box_border_thickness, config.weekendBoxBorderThickness, DAY_BOX_THICKNESS_INHERIT) {
            config.weekendBoxBorderThickness = it
        }
        addSubsection(R.string.theme_sub_grid_lines)
        addSlot(ThemeSlot.GRID_LINES)
        WeekGridLine.entries.forEach { addGridLineRows(it) }

        // Dialogs — the accent border drawn around every dialog (so it stands out from a same-colored
        // app background) and the boxed button style. Read by Commons' patched setupDialogStuff.
        addSection(R.string.theme_group_dialogs)
        addDialogBorderColorRow()
        addThicknessRow(R.string.theme_dialog_border_width, config.dialogBorderWidth, min = 0, max = 8) {
            config.dialogBorderWidth = it
        }
        addToggleRow(R.string.theme_dialog_styled_buttons, config.styledDialogButtons) {
            config.styledDialogButtons = it
        }
    }

    // The tappable Export/Import entry row: title + a status line that is refreshed with the newest
    // export in the chosen directory every time the page opens (buildRows runs in onResume).
    private fun addExportImportRow() {
        val margin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val titleView = TextView(this).apply {
            text = getString(R.string.eim_heading)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(textColor)
        }
        val statusView = TextView(this).apply {
            text = getString(R.string.eim_desc)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(textColor.adjustAlpha(0.6f))
            setPadding(0, margin / 4, 0, 0)
        }
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(margin, margin * 3 / 4, margin, margin * 3 / 4)
            val outValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            setOnClickListener { openExportImport() }
            addView(titleView)
            addView(statusView)
        }
        row.layoutParams = android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        indentView(row, currentRowIndent)
        binding.themeHolder.addView(row)
        // Query the export directory for the latest export off the main thread — SAF listing is slow.
        // Warnings (no directory set / no export yet) show in red, like in the panel.
        ensureBackgroundThread {
            val (status, warn) = SettingsTransfer.lastExportStatus(this)
            runOnUiThread {
                statusView.text = status
                if (warn) {
                    statusView.setTextColor(SettingsTransfer.WARN_COLOR)
                }
            }
        }
    }

    // ---- Automation: a subgroup of Export / Import, since every automation intent drives that export
    // (see receivers/StateExportReceiver) ----

    private fun addAutomationRows() {
        // Two rows, in the order every sister app uses: the master switch (default OFF), then the token.
        addToggleRow(R.string.enable_automation, config.automationEnabled) {
            config.automationEnabled = it
        }
        addDescriptionRow(R.string.enable_automation_desc)
        addTokenRow()

        // All-files access: needed so an automation broadcast can write to an arbitrary absolute path
        // (白い熊's archive folder) outside Download/ and Documents/. API 30+ only.
        if (isRPlus()) {
            addAllFilesAccessRow()
        }
    }

    /**
     * The automation-token row: label plus the abbreviated token, tapping anywhere copies the full token,
     * and a Regenerate action on the right that warns pasted copies stop working.
     */
    private fun addTokenRow() {
        val row = ItemThemeTokenBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeTokenLabel.text = getString(R.string.automation_token)
        row.themeTokenLabel.setTextColor(textColor)
        row.themeTokenValue.text = abbreviateToken(config.automationToken)
        row.themeTokenValue.setTextColor(textColor.adjustAlpha(0.6f))
        row.themeTokenRegenerate.text = getString(R.string.automation_token_regenerate)
        row.themeTokenRegenerate.setTextColor(primaryColor)
        row.root.setOnClickListener {
            // Not commons' copyToClipboard: that one toasts the value itself, which would put the full
            // secret back on screen right after we deliberately abbreviated it.
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(getString(R.string.automation_token), config.automationToken))
            toast(R.string.automation_token_copied)
        }
        row.themeTokenRegenerate.setOnClickListener {
            ConfirmationDialog(
                activity = this,
                message = getString(R.string.automation_token_regenerate_warning),
                positive = R.string.automation_token_regenerate,
                negative = org.fossify.commons.R.string.cancel,
            ) {
                row.themeTokenValue.text = abbreviateToken(config.regenerateAutomationToken())
                toast(R.string.automation_token_regenerated)
            }
        }
        indentView(row.root, currentRowIndent)
        binding.themeHolder.addView(row.root)
    }

    /** "80922d8c…4c49a87c" — enough to tell two tokens apart without showing the whole secret. */
    private fun abbreviateToken(token: String): String =
        if (token.length <= TOKEN_VISIBLE_CHARS * 2) {
            token
        } else {
            token.take(TOKEN_VISIBLE_CHARS) + "…" + token.takeLast(TOKEN_VISIBLE_CHARS)
        }

    private fun addAllFilesAccessRow() {
        val granted = Environment.isExternalStorageManager()
        val row = ItemThemeValueBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeValueLabel.text = getString(R.string.all_files_access)
        row.themeValueLabel.setTextColor(textColor)
        row.themeValueValue.text =
            getString(if (granted) R.string.all_files_access_granted else R.string.all_files_access_needed)
        row.themeValueValue.setTextColor(if (granted) textColor.adjustAlpha(0.6f) else SettingsTransfer.WARN_COLOR)
        row.root.setOnClickListener {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e2: Exception) {
                    showErrorToast(e2)
                }
            }
        }
        indentView(row.root, currentRowIndent)
        binding.themeHolder.addView(row.root)
    }

    /** A dimmed explanatory line under the row it belongs to. */
    private fun addDescriptionRow(@StringRes textRes: Int) {
        val margin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val view = TextView(this).apply {
            text = getString(textRes)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(textColor.adjustAlpha(0.6f))
            setPadding(margin, 0, margin, margin / 2)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        indentView(view, currentRowIndent)
        binding.themeHolder.addView(view)
    }

    private fun openExportImport() {
        eximDialog = ExportImportDialog(
            activity = this,
            launchDirPicker = { eximDirPicker.launch(it) },
            launchImportPicker = { eximImportPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            launchExportCreate = { eximExportCreator.launch(it) },
            onChainClose = { finish() }
        )
    }

    // Standalone colour row for the dialog accent border (not a ThemeSlot). The "Default" badge shows
    // while the colour is still the fork default; the picker's default button resets to it.
    private fun addDialogBorderColorRow() {
        val row = ItemThemeColorBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeColorLabel.text = getString(R.string.theme_dialog_border_color)
        row.themeColorLabel.setTextColor(textColor)
        row.themeColorPreview.background.setTint(config.dialogBorderColor)
        row.themeColorDefault.setTextColor(textColor.adjustAlpha(0.6f))
        row.themeColorDefault.beVisibleIf(config.dialogBorderColor == PALETTE_YELLOW)
        row.root.setOnClickListener {
            AlphaColorPickerDialog(this, config.dialogBorderColor, addDefaultColorButton = true) { wasPositive, color ->
                config.dialogBorderColor = if (wasPositive) color else PALETTE_YELLOW
                row.themeColorPreview.background.setTint(config.dialogBorderColor)
                row.themeColorDefault.beVisibleIf(config.dialogBorderColor == PALETTE_YELLOW)
            }
        }
        indentView(row.root, currentRowIndent)
        binding.themeHolder.addView(row.root)
    }

    // Per box-grid grid line: its toggle, then (indented) its colour and thickness. Colour follows
    // the shared Grid lines colour until overridden; thickness is in dp.
    private fun addGridLineRows(line: WeekGridLine) {
        addToggleRow(line.labelRes, config.isGridLineEnabled(line.key)) {
            config.setGridLineEnabled(line.key, it)
        }
        currentRowIndent++
        addGridLineColorRow(line)
        addThicknessRow(R.string.theme_grid_line_thickness, config.getGridLineThickness(line.key), min = 1) {
            config.setGridLineThickness(line.key, it)
        }
        currentRowIndent--
    }

    private fun addGridLineColorRow(line: WeekGridLine) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeColorLabel.text = getString(R.string.theme_grid_line_color)
        row.themeColorLabel.setTextColor(textColor)
        row.themeColorPreview.background.setTint(gridLineColor(line))
        row.themeColorDefault.setTextColor(textColor.adjustAlpha(0.6f))
        row.themeColorDefault.beVisibleIf(config.getGridLineColor(line.key) == THEME_UNSET)
        row.root.setOnClickListener {
            AlphaColorPickerDialog(this, gridLineColor(line), addDefaultColorButton = true) { wasPositive, color ->
                config.setGridLineColor(line.key, if (wasPositive) color else THEME_UNSET)
                row.themeColorPreview.background.setTint(gridLineColor(line))
                row.themeColorDefault.beVisibleIf(config.getGridLineColor(line.key) == THEME_UNSET)
            }
        }
        indentView(row.root, currentRowIndent)
        binding.themeHolder.addView(row.root)
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

    // kxkb-style section header: bold accent title with a text-wide underline, preceded by a thin
    // full-width hairline spacer on every section but the first.
    private fun addSection(@StringRes titleRes: Int) {
        val section = ItemThemeSectionBinding.inflate(layoutInflater, binding.themeHolder, false)
        section.themeSectionLabel.text = getString(titleRes)
        section.themeSectionLabel.setTextColor(primaryColor)
        section.themeSectionUnderline.setBackgroundColor(primaryColor)
        section.themeSectionDivider.setBackgroundColor(primaryColor)
        section.themeSectionDivider.beVisibleIf(binding.themeHolder.childCount > 0)
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

    private fun addToggleRow(@StringRes labelRes: Int, isChecked: Boolean, onToggle: (Boolean) -> Unit) {
        val row = ItemThemeToggleBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeToggleSwitch.text = getString(labelRes)
        row.themeToggleSwitch.setColors(textColor, primaryColor, getProperBackgroundColor())
        row.themeToggleSwitch.isChecked = isChecked
        row.themeToggleHolder.setOnClickListener {
            row.themeToggleSwitch.toggle()
            onToggle(row.themeToggleSwitch.isChecked)
        }
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
            effectiveFontFamily(slot),
            effectiveFontWeight(slot),
            effectiveFontSize(slot),
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

    private fun addDateHeaderFormatRow() {
        val row = ItemThemeValueBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeValueLabel.text = getString(R.string.theme_date_header_format)
        row.themeValueLabel.setTextColor(textColor)
        row.themeValueValue.setTextColor(textColor.adjustAlpha(0.6f))
        row.themeValueValue.text = dateFormatSample()
        row.root.setOnClickListener { openDateHeaderFormatPicker(row.themeValueValue) }
        indentView(row.root, currentRowIndent)
        binding.themeHolder.addView(row.root)
    }

    private fun dateFormatSample() = formatDayBoxHeader(System.currentTimeMillis(), config.dayBoxHeaderDateFormat)

    private fun openDateHeaderFormatPicker(valueView: TextView) {
        val now = System.currentTimeMillis()
        val customId = 2
        val current = when (config.dayBoxHeaderDateFormat) {
            DAY_BOX_HEADER_FORMAT_JAPANESE -> 0
            DAY_BOX_HEADER_FORMAT_JAPANESE_ERA -> 1
            else -> customId
        }
        val items = arrayListOf(
            RadioItem(0, "${getString(R.string.theme_date_format_japanese)} — ${formatDayBoxHeader(now, DAY_BOX_HEADER_FORMAT_JAPANESE)}"),
            RadioItem(1, "${getString(R.string.theme_date_format_japanese_era)} — ${formatDayBoxHeader(now, DAY_BOX_HEADER_FORMAT_JAPANESE_ERA)}"),
            RadioItem(customId, getString(R.string.theme_date_format_custom))
        )
        RadioGroupDialog(this, items, current) {
            when (it as Int) {
                0 -> {
                    config.dayBoxHeaderDateFormat = DAY_BOX_HEADER_FORMAT_JAPANESE
                    valueView.text = dateFormatSample()
                }

                1 -> {
                    config.dayBoxHeaderDateFormat = DAY_BOX_HEADER_FORMAT_JAPANESE_ERA
                    valueView.text = dateFormatSample()
                }

                else -> openCustomDateFormatDialog(valueView)
            }
        }
    }

    private fun openCustomDateFormatDialog(valueView: TextView) {
        val dialogBinding = DialogDayBoxDateFormatBinding.inflate(layoutInflater)
        val stored = config.dayBoxHeaderDateFormat
        val initial = if (stored == DAY_BOX_HEADER_FORMAT_JAPANESE || stored == DAY_BOX_HEADER_FORMAT_JAPANESE_ERA) {
            "MMM d, EEE"
        } else {
            stored
        }
        dialogBinding.dateFormatPattern.setText(initial)
        dialogBinding.dateFormatSample.text = formatDayBoxHeader(System.currentTimeMillis(), initial)
        dialogBinding.dateFormatPattern.onTextChangeListener { text ->
            dialogBinding.dateFormatSample.text = formatDayBoxHeader(System.currentTimeMillis(), text)
        }

        getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                val pattern = dialogBinding.dateFormatPattern.text.toString().trim()
                if (pattern.isNotEmpty()) {
                    config.dayBoxHeaderDateFormat = pattern
                    valueView.text = dateFormatSample()
                }
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                setupDialogStuff(dialogBinding.root, this, R.string.theme_date_header_format)
            }
    }

    private fun addEventTimeFormatRow() {
        val row = ItemThemeValueBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeValueLabel.text = getString(R.string.theme_event_time_format)
        row.themeValueLabel.setTextColor(textColor)
        row.themeValueValue.setTextColor(textColor.adjustAlpha(0.6f))
        row.themeValueValue.text = eventTimeSample()
        row.root.setOnClickListener { openEventTimeFormatPicker(row.themeValueValue) }
        indentView(row.root, currentRowIndent)
        binding.themeHolder.addView(row.root)
    }

    // A representative 08:00→09:00 range so the user sees the chosen style at a glance.
    private fun eventTimeSample(format: String = config.eventTimeFormat) =
        formatEventTimeRange(sampleSeconds(8), sampleSeconds(9), format)

    private fun sampleSeconds(hour: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 1000L
    }

    private fun openEventTimeFormatPicker(valueView: TextView) {
        val customId = 1
        val current = if (config.eventTimeFormat == EVENT_TIME_FORMAT_JAPANESE) 0 else customId
        val items = arrayListOf(
            RadioItem(0, "${getString(R.string.theme_event_time_japanese)} — ${eventTimeSample(EVENT_TIME_FORMAT_JAPANESE)}"),
            RadioItem(customId, getString(R.string.theme_date_format_custom))
        )
        RadioGroupDialog(this, items, current) {
            if (it as Int == 0) {
                config.eventTimeFormat = EVENT_TIME_FORMAT_JAPANESE
                valueView.text = eventTimeSample()
            } else {
                openCustomEventTimeDialog(valueView)
            }
        }
    }

    private fun openCustomEventTimeDialog(valueView: TextView) {
        val dialogBinding = DialogDayBoxDateFormatBinding.inflate(layoutInflater)
        val stored = config.eventTimeFormat
        val initial = if (stored == EVENT_TIME_FORMAT_JAPANESE) "HH:mm" else stored
        dialogBinding.dateFormatPatternHint.hint = getString(R.string.theme_time_pattern_hint)
        dialogBinding.dateFormatLegend.text = getString(R.string.theme_time_format_legend)
        dialogBinding.dateFormatPattern.setText(initial)
        dialogBinding.dateFormatSample.text = eventTimeSample(initial)
        dialogBinding.dateFormatPattern.onTextChangeListener { text ->
            dialogBinding.dateFormatSample.text = eventTimeSample(text)
        }

        getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                val pattern = dialogBinding.dateFormatPattern.text.toString().trim()
                if (pattern.isNotEmpty()) {
                    config.eventTimeFormat = pattern
                    valueView.text = eventTimeSample()
                }
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                setupDialogStuff(dialogBinding.root, this, R.string.theme_event_time_format)
            }
    }

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
