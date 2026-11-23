package org.fossify.calendar.activities

import android.graphics.Typeface
import android.util.TypedValue
import android.widget.LinearLayout
import android.view.DragEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.graphics.drawable.GradientDrawable
import com.google.android.material.chip.ChipGroup
import org.fossify.calendar.extensions.resetAllThemeColors
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.pillDrawable
import org.fossify.commons.extensions.rippleDrawable
import org.fossify.calendar.views.applyOutlinedSwitch
import kotlin.math.roundToInt
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
import org.fossify.calendar.databinding.DialogDayOffTitleBinding
import org.fossify.calendar.databinding.ItemThemeColorBinding
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
import org.fossify.calendar.helpers.DAY_OFF_TITLE_DEFAULT
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_CENTER
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_END
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_START
import org.fossify.calendar.helpers.DAY_BOX_HEADER_FORMAT_JAPANESE
import org.fossify.calendar.helpers.DAY_BOX_HEADER_FORMAT_JAPANESE_ERA
import org.fossify.calendar.helpers.EVENT_TIME_FORMAT_JAPANESE
import org.fossify.calendar.helpers.DAY_BOX_THICKNESS_INHERIT
import org.fossify.calendar.helpers.DAY_OFF_TITLE_DEFAULT
import org.fossify.calendar.helpers.MAX_FONT_SIZE_SP
import org.fossify.calendar.helpers.SettingsTransfer
import org.fossify.calendar.helpers.THEME_UNSET
import org.fossify.calendar.helpers.formatDayBoxHeader
import org.fossify.commons.helpers.uiColor
import org.fossify.commons.helpers.setUiColor
import org.fossify.commons.helpers.resetUiColor
import org.fossify.commons.helpers.isUiColorSet
import org.fossify.commons.helpers.UiRoleSection
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
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
import androidx.annotation.DrawableRes
import org.fossify.calendar.extensions.appLanguageLabel
import org.fossify.calendar.extensions.showAppLanguagePicker
import org.fossify.calendar.extensions.labelIconGutter
import org.fossify.calendar.extensions.setLabelIcon
import org.fossify.commons.R as CR

/**
 * A top-level section of the page: its stored [key] (never rename one), its heading, the [icon] its
 * heading and its index chip both carry — in the colour of [role] — and what builds its rows.
 */
private class UiSection(
    val key: String,
    val title: String,
    @DrawableRes val icon: Int,
    val role: ThemeSlot,
    val fill: () -> Unit,
)

/**
 * The icon a row of the page carries, coloured by what the row does. The colours are the menu icon
 * roles, so a row and a menu line that do the same wear the same colour.
 */
private enum class RowIcon(val res: Int, val role: ThemeSlot) {
    SETTINGS(org.fossify.commons.R.drawable.ic_settings_cog_vector, ThemeSlot.MENU_ROLE_VIEW),
    TOGGLE(org.fossify.commons.R.drawable.ic_check_circle_vector, ThemeSlot.MENU_ROLE_VIEW),
    COLOR(R.drawable.ic_color_outline_vector, ThemeSlot.MENU_ROLE_VIEW),
    FONT(org.fossify.commons.R.drawable.ic_label_vector, ThemeSlot.MENU_ROLE_VIEW),
    WEIGHT(org.fossify.commons.R.drawable.ic_edit_vector, ThemeSlot.MENU_ROLE_VIEW),
    SIZE(org.fossify.commons.R.drawable.ic_sort_vector, ThemeSlot.MENU_ROLE_VIEW),
    VALUE(org.fossify.commons.R.drawable.ic_edit_vector, ThemeSlot.MENU_ROLE_VIEW),
    TIME(org.fossify.commons.R.drawable.ic_clock_vector, ThemeSlot.MENU_ROLE_VIEW),
    DATE(R.drawable.ic_calendar_vector, ThemeSlot.MENU_ROLE_VIEW),
    RESET(org.fossify.commons.R.drawable.ic_reset_vector, ThemeSlot.MENU_ROLE_CONNECT),
    SECRET(org.fossify.commons.R.drawable.ic_lock_vector, ThemeSlot.MENU_ROLE_BLOCK),
    SHARE(org.fossify.commons.R.drawable.ic_share_vector, ThemeSlot.MENU_ROLE_SHARE),
    DIRECTORY(org.fossify.commons.R.drawable.ic_folder_vector, ThemeSlot.MENU_ROLE_SHARE),
    AUTOMATION(org.fossify.commons.R.drawable.ic_code_vector, ThemeSlot.MENU_ROLE_SHARE),
    DELETE(org.fossify.commons.R.drawable.ic_delete_vector, ThemeSlot.MENU_ROLE_DELETE),
    LANGUAGE(R.drawable.ic_globe_vector, ThemeSlot.MENU_ROLE_VIEW),
}

// The chip index under the app-settings pill, the folding headings, and a row's icon.
private const val INDEX_GAP_DP = 6f
private const val INDEX_TEXT_SP = 13f
private const val INDEX_PAD_H_DP = 10f
private const val INDEX_PAD_V_DP = 5f
private const val INDEX_DRAGGED_ALPHA = 0.3f
private const val SECTION_GAP_DP = 10f
private const val SECTION_HEADER_PAD_DP = 8f
private const val SECTION_TITLE_SP = 20f
private const val SECTION_CHEVRON_SP = 22f
private const val SECTION_ICON_GAP_DP = 12f
private const val INDEX_ICON_GAP_DP = 5f

// An icon is 1.25 × the text size of the label it stands before, so it follows the font scale.
private const val ICON_PER_TEXT = 1.25f

// The consolidated "白い熊 予定表 UI" screen: the way to the app's own settings, a chip index, then the
// sections — each a folding block, folded unless 白い熊 opened it, in the order the chips were dragged into. Built from a section -> (optional subsection) -> rows
// hierarchy: section headers carry a full-width accent underline, subsections a short one, color
// rows show a "Default" badge while inherited, and text elements get a font / weight / size block
// with a live sample beneath. Every colour is picked with an alpha-enabled dialog.
@Suppress("TooManyFunctions")
class ThemeActivity : SimpleActivity() {
    companion object {
        // How many hex characters of the automation token stay visible at each end.
        private const val TOKEN_VISIBLE_CHARS = 8

        // Set by Settings when it opens this page, so "App settings" goes back to it instead of stacking a copy
        const val EXTRA_FROM_SETTINGS = "from_settings"
    }

    private val binding by viewBinding(ActivityThemeBinding::inflate)
    private val previews = HashMap<ThemeSlot, ImageView>()
    private val defaultBadges = HashMap<ThemeSlot, TextView>()
    private var textColor = 0
    private var primaryColor = 0
    private var indentStepPx = 0
    private var currentRowIndent = 0

    // Where the row builders add their rows: the page itself, or the body of the section being filled.
    private lateinit var body: ViewGroup

    // The page's sections in their shown order, and each one's views by its key.
    private var sections: List<UiSection> = emptyList()
    private val sectionWrappers = HashMap<String, View>()
    private val sectionBodies = HashMap<String, LinearLayout>()
    private val sectionChevrons = HashMap<String, TextView>()
    private val sectionsFilled = HashSet<String>()

    // The automation token row, kept so the "Use authorization token?" switch can show and hide it.
    private var tokenRow: ItemThemeTokenBinding? = null

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
        sectionWrappers.clear()
        sectionBodies.clear()
        sectionChevrons.clear()
        sectionsFilled.clear()
        tokenRow = null
        body = binding.themeHolder
        textColor = getProperTextColor()
        primaryColor = getProperPrimaryColor()
        indentStepPx = 3 * resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        currentRowIndent = 0

        // The page's link to the app's own settings: the first, thick pill, right under the title. Not a section.
        addActionRow(
            getString(R.string.theme_app_settings), getString(R.string.theme_app_settings_desc),
            main = true, opens = true, icon = RowIcon.SETTINGS
        ) { openAppSettings() }

        val built = ArrayList<UiSection>()
        // Every section has an icon — on its heading and on its chip — coloured by what kind of thing the
        // section is (ThemeSlot.SECTION_*). The default order below keeps two of one colour apart.
        fun section(
            key: String,
            @StringRes titleRes: Int,
            @DrawableRes icon: Int,
            role: ThemeSlot,
            fill: () -> Unit,
        ) = built.add(UiSection(key, getString(titleRes), icon, role, fill))
        fun slots(group: ThemeGroup) = slotsOf(group).forEach { addSlot(it) }

        // Your data first (backup, housekeeping), then the look, surface by surface.
        section("exim", R.string.eim_heading, CR.drawable.ic_share_vector, ThemeSlot.SECTION_REACH) {
            addExportImportRow()
            // Automation sits directly below the export row it drives — the placement every sister app shares.
            addSubsection(R.string.automation)
            addAutomationRows()
        }
        section("language", R.string.app_language, R.drawable.ic_globe_vector, ThemeSlot.SECTION_WORDS) {
            addActionRow(appLanguageLabel(), getString(R.string.app_language_desc), main = true, opens = true, icon = RowIcon.LANGUAGE) {
                showAppLanguagePicker()
            }
        }
        section("maintenance", R.string.theme_maintenance, CR.drawable.ic_reset_vector, ThemeSlot.SECTION_WARNING) { addMaintenanceRows() }
        section("foundation", R.string.theme_group_foundation, R.drawable.ic_color_outline_vector, ThemeSlot.SECTION_BASE) { slots(ThemeGroup.FOUNDATION) }
        section("text", UiRoleSection.TEXT.labelRes, CR.drawable.ic_article_outline_vector, ThemeSlot.SECTION_WORDS) {
            slots(ThemeGroup.TEXT_STATUS)
            addRoleRows(UiRoleSection.TEXT)
        }
        section("buttons", UiRoleSection.BUTTONS.labelRes, CR.drawable.ic_check_circle_vector, ThemeSlot.SECTION_CONTROLS) {
            addRoleRows(UiRoleSection.BUTTONS)
            addSubsection(R.string.theme_sub_fab_menu)
            slots(ThemeGroup.BUTTONS)
        }
        section("fields", UiRoleSection.FIELDS.labelRes, CR.drawable.ic_rename_vector, ThemeSlot.SECTION_WORDS) { addRoleRows(UiRoleSection.FIELDS) }
        section("switches", UiRoleSection.SWITCHES.labelRes, R.drawable.ic_circle_vector, ThemeSlot.SECTION_CONTROLS) {
            slots(ThemeGroup.SWITCHES)
            addRoleRows(UiRoleSection.SWITCHES)
        }
        // Dialogs — the frame drawn around every dialog, its buttons, every colour of a dialog and of the pickers.
        section("dialogs", R.string.theme_group_dialogs, CR.drawable.ic_info_outline_vector, ThemeSlot.SECTION_REACH) {
            addThicknessRow(R.string.theme_dialog_border_width, config.dialogBorderWidth, min = 0, max = 8) {
                config.dialogBorderWidth = it
            }
            addToggleRow(R.string.theme_dialog_pill_buttons, config.pillDialogButtons, R.string.theme_dialog_pill_buttons_desc) {
                config.pillDialogButtons = it
            }
            addToggleRow(R.string.theme_dialog_styled_buttons, config.styledDialogButtons) {
                config.styledDialogButtons = it
            }
            addRoleRows(UiRoleSection.DIALOGS)
            addSubsection(R.string.theme_sub_time_entry)
            slots(ThemeGroup.DIALOGS)
            addSubsection(R.string.theme_sub_exim_panel)
            slots(ThemeGroup.EXIM_PANEL)
            addSubsection(R.string.theme_sub_font_picker)
            slots(ThemeGroup.FONT_PICKER)
        }
        section("marks", UiRoleSection.MARKS.labelRes, CR.drawable.ic_check_vector, ThemeSlot.SECTION_CONTROLS) { addRoleRows(UiRoleSection.MARKS) }
        // Menus — every action row is an outlined pill; its icon is coloured by what the row does.
        section("menus", R.string.theme_group_menus, CR.drawable.ic_three_dots_vector, ThemeSlot.SECTION_REACH) {
            addDescriptionRow(R.string.theme_menus_note)
            slots(ThemeGroup.MENUS)
            addRoleRows(UiRoleSection.MENUS)
        }
        section("lists", UiRoleSection.LISTS.labelRes, CR.drawable.ic_sort_vector, ThemeSlot.SECTION_FRAME) {
            addRoleRows(UiRoleSection.LISTS)
            addSubsection(R.string.theme_sub_calendar_lists)
            slots(ThemeGroup.LISTS)
        }
        section("toasts", UiRoleSection.TOASTS.labelRes, R.drawable.ic_bell_outline_vector, ThemeSlot.SECTION_FLASH) { addRoleRows(UiRoleSection.TOASTS) }
        section("chrome", R.string.theme_group_chrome, CR.drawable.ic_move_to_top_vector, ThemeSlot.SECTION_FRAME) { slots(ThemeGroup.CHROME) }
        section("settings_page", R.string.theme_group_settings_page, CR.drawable.ic_settings_cog_vector, ThemeSlot.SECTION_BASE) {
            slots(ThemeGroup.SETTINGS_PAGE)
            addSubsection(R.string.theme_sub_section_icons)
            addDescriptionRow(R.string.theme_section_icons_note)
            slots(ThemeGroup.SECTION_ICONS)
        }
        section("search", R.string.theme_group_search, CR.drawable.ic_search_vector, ThemeSlot.SECTION_WORDS) { slots(ThemeGroup.SEARCH) }
        section("bars", UiRoleSection.BARS.labelRes, CR.drawable.ic_drag_handle_vector, ThemeSlot.SECTION_FRAME) { addRoleRows(UiRoleSection.BARS) }
        section("cal_views", R.string.theme_sub_views, R.drawable.ic_change_view_vector, ThemeSlot.SECTION_BASE) { addCalendarViewRows() }
        section("cal_boxes", R.string.theme_sub_day_boxes, R.drawable.ic_today_vector, ThemeSlot.SECTION_FRAME) { addDayBoxRows() }
        section("cal_events", R.string.theme_sub_events, R.drawable.ic_calendar_vector, ThemeSlot.SECTION_BASE) { addEventRows() }
        section("editor", R.string.theme_sub_editor, CR.drawable.ic_edit_vector, ThemeSlot.SECTION_WORDS) { slots(ThemeGroup.EDITOR) }
        section("widget_setup", R.string.theme_sub_widget_setup, CR.drawable.ic_apps_vector, ThemeSlot.SECTION_REACH) { slots(ThemeGroup.WIDGET_SETUP) }
        sections = orderedSections(built)

        addSectionIndex()
        sections.forEachIndexed { index, section -> addSection(section, index == 0) }
    }

    // What every calendar view shares, the weekly view, the year view and the grid lines.
    private fun addCalendarViewRows() {
        slotsOf(ThemeGroup.VIEWS).forEach { addSlot(it) }
        addSubsection(R.string.theme_sub_weekly_view)
        addToggleRow(R.string.theme_show_time_grid_weekly, config.showTimeGridWeekly) {
            config.showTimeGridWeekly = it
        }
        addToggleRow(R.string.theme_show_box_grid_weekly, config.showBoxGridWeekly) {
            config.showBoxGridWeekly = it
        }
        addSubsection(R.string.theme_sub_year_view)
        slotsOf(ThemeGroup.YEAR).forEach { addSlot(it) }
        addSubsection(R.string.theme_sub_grid_lines)
        addSlot(ThemeSlot.GRID_LINES)
        WeekGridLine.entries.forEach { addGridLineRows(it) }
    }

    // The day boxes of 週 and 月: every day, today, the weekend, and 休み.
    private fun addDayBoxRows() {
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
        // 休み (days off): an entry with this title styles itself and its whole day, whatever calendar
        // it is filed under.
        addSubsection(R.string.theme_sub_day_off)
        addDescriptionRow(R.string.theme_day_off_description)
        addToggleRow(R.string.theme_day_off_enabled, config.dayOffEnabled) {
            config.dayOffEnabled = it
        }
        addDayOffTitleRow()
        addSlot(ThemeSlot.DAY_OFF_ENTRY_TEXT)
        addSlot(ThemeSlot.DAY_OFF_ENTRY_BACKGROUND)
        addSlot(ThemeSlot.DAY_OFF_FRAME)
        addSlot(ThemeSlot.DAY_OFF_DATE_TEXT)
    }

    // An event as the views draw it, its time zone lines, and the event list.
    private fun addEventRows() {
        addSlot(ThemeSlot.EVENT_TEXT)
        addEventTimeFormatRow()
        addSubsection(R.string.theme_sub_time_zones)
        addSlot(ThemeSlot.TIME_ZONE_TEXT)
        addSlot(ThemeSlot.TIME_ZONE_TAG)
        addSubsection(R.string.theme_sub_event_list)
        slotsOf(ThemeGroup.EVENT_LIST).forEach { addSlot(it) }
    }

    /** The stored order applied to [built]: stored keys first, then sections a later build added, at their place. */
    private fun orderedSections(built: List<UiSection>): List<UiSection> {
        val byKey = built.associateBy { it.key }
        val stored = config.uiSectionOrder.split(',').mapNotNull { byKey[it] }.distinct()
        return stored + built.filter { it !in stored }
    }

    // The jump index: a wrapping grid of small outlined chips, one per section. A tap unfolds the section
    // and scrolls to it; a long-press lifts the chip, the chips reorder live under the finger, and the
    // sections follow on drop.
    private fun addSectionIndex() {
        val margin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val group = ChipGroup(this).apply {
            chipSpacingHorizontal = px(INDEX_GAP_DP)
            chipSpacingVertical = px(INDEX_GAP_DP)
            setPaddingRelative(margin, px(INDEX_GAP_DP), margin, px(INDEX_GAP_DP))
        }
        sections.forEach { section ->
            group.addView(TextView(this).apply {
                text = section.title
                tag = section.key
                setTextColor(uiColor(UiRole.BUTTON_TEXT))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, INDEX_TEXT_SP)
                setPadding(px(INDEX_PAD_H_DP), px(INDEX_PAD_V_DP), px(INDEX_PAD_H_DP), px(INDEX_PAD_V_DP))
                background = pillDrawable(uiColor(UiRole.BUTTON_FRAME), uiColor(UiRole.BUTTON_GROUND), main = false)
                // the section's own icon, in its role colour; the chip stays as wide as icon + label
                gravity = Gravity.CENTER_VERTICAL
                setCompoundDrawablesRelative(sectionIcon(section, textSize), null, null, null)
                compoundDrawablePadding = px(INDEX_ICON_GAP_DP)
                setOnClickListener { jumpToSection(section.key) }
                setOnLongClickListener { chip ->
                    chip.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    chip.startDragAndDrop(null, View.DragShadowBuilder(chip), chip, 0)
                    chip.alpha = INDEX_DRAGGED_ALPHA
                    true
                }
            })
        }

        val orderBefore = sections.map { it.key }
        // The chip last moved past: the same chip is not swapped again until the finger has been over the
        // dragged one, or two chips of unequal width would swap back and forth under a still finger.
        var lastTarget: View? = null
        group.setOnDragListener { _, event ->
            val dragged = event.localState as? View
            if (dragged == null || dragged.parent !== group) return@setOnDragListener false
            when (event.action) {
                DragEvent.ACTION_DRAG_LOCATION -> {
                    val target = (0 until group.childCount).map { group.getChildAt(it) }
                        .firstOrNull { event.x >= it.left && event.x < it.right && event.y >= it.top && event.y < it.bottom }
                    if (target === dragged) {
                        lastTarget = null
                    } else if (target != null && target !== lastTarget) {
                        val to = group.indexOfChild(target)
                        group.removeView(dragged)
                        group.addView(dragged, to)
                        lastTarget = target
                    }
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    dragged.alpha = 1f
                    val order = (0 until group.childCount).map { group.getChildAt(it).tag as String }
                    if (order != orderBefore) {
                        config.uiSectionOrder = order.joinToString(",")
                        // not from inside the drag dispatch: the rebuild removes the view being dispatched to
                        group.post { buildRows() }
                    }
                }
            }
            true
        }
        binding.themeHolder.addView(group)
    }

    // A folding section: the 1 px darker-accent line above it (every section but the first; it stays whether
    // the section is folded or open), its bold heading underlined as wide as its text with › folded / ⌄ open
    // at the right, and its rows in a body that folds away.
    private fun addSection(section: UiSection, first: Boolean) {
        val margin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (!first) {
            wrapper.addView(
                View(this).apply { setBackgroundColor(uiColor(UiRole.LIST_DIVIDER)) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { topMargin = px(SECTION_GAP_DP) },
            )
        }

        val label = TextView(this).apply {
            text = section.title
            setTextColor(primaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, SECTION_TITLE_SP)
            setTypeface(typeface, Typeface.BOLD)
        }
        val labelColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label, LinearLayout.LayoutParams(wrap, wrap))
            // as wide as the column, which is as wide as the label's text
            addView(
                View(this@ThemeActivity).apply { setBackgroundColor(primaryColor) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(2.5f)).apply { topMargin = px(2f) },
            )
        }
        val chevron = TextView(this).apply {
            setTextColor(primaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, SECTION_CHEVRON_SP)
            includeFontPadding = false
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(margin, px(SECTION_HEADER_PAD_DP), margin, px(SECTION_HEADER_PAD_DP))
            background = rippleDrawable()
            addView(
                ImageView(this@ThemeActivity).apply { setImageDrawable(sectionIcon(section, label.textSize)) },
                LinearLayout.LayoutParams(iconSize(label.textSize), iconSize(label.textSize)).apply {
                    marginEnd = px(SECTION_ICON_GAP_DP)
                },
            )
            addView(labelColumn, LinearLayout.LayoutParams(wrap, wrap))
            addView(View(this@ThemeActivity), LinearLayout.LayoutParams(0, 0, 1f))
            addView(chevron)
            setOnClickListener { setSectionOpen(section.key, section.key !in config.uiSectionsOpen) }
        }
        wrapper.addView(header)

        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrapper.addView(inner)
        binding.themeHolder.addView(wrapper)

        sectionWrappers[section.key] = wrapper
        sectionBodies[section.key] = inner
        sectionChevrons[section.key] = chevron
        showSectionState(section.key, section.key in config.uiSectionsOpen)
    }

    private fun iconSize(labelTextSizePx: Float) = (labelTextSizePx * ICON_PER_TEXT).toInt()

    /** The section's icon, sized by its label and in the colour of its role — the same on its heading and on its chip. */
    private fun sectionIcon(section: UiSection, labelTextSizePx: Float) =
        resources.getColoredDrawableWithColor(section.icon, themeColor(section.role)).apply {
            setBounds(0, 0, iconSize(labelTextSizePx), iconSize(labelTextSizePx))
        }

    private fun setSectionOpen(key: String, open: Boolean) {
        val stored = config.uiSectionsOpen
        config.uiSectionsOpen = if (open) stored + key else stored - key
        showSectionState(key, open)
    }

    // A folded section's rows are not built until it is first opened.
    private fun showSectionState(key: String, open: Boolean) {
        val inner = sectionBodies[key] ?: return
        if (open && sectionsFilled.add(key)) {
            // rows are added to `body`; it points here only while this section's own rows are built
            body = inner
            currentRowIndent = 1
            sections.firstOrNull { it.key == key }?.fill?.invoke()
            body = binding.themeHolder
            currentRowIndent = 0
        }
        inner.beVisibleIf(open)
        sectionChevrons[key]?.text = if (open) "⌄" else "›"
    }

    /** A tap on an index chip: unfold the section and scroll to it. */
    private fun jumpToSection(key: String) {
        val wrapper = sectionWrappers[key] ?: return
        setSectionOpen(key, true)
        binding.themeHolder.post { binding.themeNestedScrollview.smoothScrollTo(0, wrapper.top) }
    }

    // ---- Maintenance ----

    private fun addMaintenanceRows() {
        addActionRow(
            getString(R.string.theme_reset_section_order), getString(R.string.theme_reset_section_order_desc),
            main = false, opens = false, icon = RowIcon.RESET
        ) {
            config.uiSectionOrder = ""
            config.uiSectionsOpen = emptySet()
            buildRows()
        }
        addActionRow(
            getString(R.string.theme_reset_all_colours), getString(R.string.theme_reset_all_colours_desc),
            main = false, opens = false, icon = RowIcon.DELETE, destructive = true
        ) {
            ConfirmationDialog(
                activity = this,
                message = getString(R.string.theme_reset_all_colours_confirm),
                positive = R.string.theme_reset_action,
                negative = org.fossify.commons.R.string.cancel,
                destructive = true,
            ) {
                resetAllThemeColors()
                recreate()
            }
        }
    }

    // The row's icon, before its label and in the colour of what the row does; sized by the label.
    private fun TextView.setRowIcon(icon: RowIcon, color: Int = themeColor(icon.role), onGutter: ((Int) -> Unit)? = null) =
        setLabelIcon(icon.res, color, onGutter)

    // A swatch: the colour inside a thin ring of the text role, so a swatch in the background colour shows.
    private fun swatch(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(px(1f), textColor)
    }

    // The Export/Import panel: the section's main pill, with a status line beneath that is refreshed
    // with the newest export in the chosen directory every time the page opens (buildRows runs in onResume).
    private fun addExportImportRow() {
        val statusView = addActionRow(
            getString(R.string.eim_heading), getString(R.string.eim_desc), main = true, opens = true,
            icon = RowIcon.SHARE
        ) { openExportImport() } ?: return
        // Query the export directory for the latest export off the main thread — SAF listing is slow.
        // Warnings (no directory set / no export yet) show in red, like in the panel.
        ensureBackgroundThread {
            val (status, warn) = SettingsTransfer.lastExportStatus(this)
            runOnUiThread {
                statusView.text = status
                if (warn) {
                    statusView.setTextColor(themeColor(ThemeSlot.STATUS_WARNING))
                }
            }
        }
    }

    private fun openAppSettings() {
        if (intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)) {
            finish()
        } else {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // ---- Automation: a subgroup of Export / Import, since every automation intent drives that export
    // (see receivers/StateExportReceiver) ----

    private fun addAutomationRows() {
        // Three rows, in the order every sister app uses since contract v2 (2026-09-04): the master
        // switch, which now ships ON; then "Use authorization token?", which ships OFF; then the token
        // itself, shown ONLY while the second switch is on. A 48-character secret sitting under an off
        // switch invites 白い熊 to paste it somewhere it will do nothing.
        addToggleRow(
            R.string.enable_automation, config.automationEnabled, R.string.enable_automation_desc, RowIcon.AUTOMATION
        ) {
            config.automationEnabled = it
        }
        addToggleRow(
            R.string.automation_require_token, config.automationRequireToken, R.string.automation_require_token_desc,
            RowIcon.SECRET
        ) {
            config.automationRequireToken = it
            showTokenRow(it)
        }
        addTokenRow()
        showTokenRow(config.automationRequireToken)

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
        val row = ItemThemeTokenBinding.inflate(layoutInflater, body, false)
        tokenRow = row
        row.themeTokenLabel.text = getString(R.string.automation_token)
        row.themeTokenLabel.setTextColor(textColor)
        row.themeTokenLabel.setRowIcon(RowIcon.SECRET)
        row.themeTokenValue.setTextColor(noteColor())
        row.themeTokenRegenerate.text = getString(R.string.automation_token_regenerate)
        row.themeTokenRegenerate.setTextColor(uiColor(UiRole.BUTTON_TEXT))
        // An action in place, after a confirmation: a thin pill, no ›
        row.themeTokenRegenerate.background =
            pillDrawable(uiColor(UiRole.BUTTON_FRAME), uiColor(UiRole.BUTTON_GROUND), main = false)
        row.themeTokenRegenerate.setPadding(px(16f), px(9f), px(16f), px(9f))
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
        tighten(row.root)
        indentView(row.root, currentRowIndent)
        body.addView(row.root)
    }

    /**
     * Show or hide the token row, and fill it in the first time it is actually shown.
     *
     * The value is read here rather than when the row is inflated because [Config.automationToken]
     * generates the secret lazily on first read — so an app whose token is not being asked for never
     * creates one at all, which is the point of the switch above it.
     */
    private fun showTokenRow(visible: Boolean) {
        val row = tokenRow ?: return
        row.root.beVisibleIf(visible)
        if (visible) {
            row.themeTokenValue.text = abbreviateToken(config.automationToken)
        }
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
        val status = getString(if (granted) R.string.all_files_access_granted else R.string.all_files_access_needed)
        val statusView = addActionRow(
            getString(R.string.all_files_access), status, main = false, opens = true, icon = RowIcon.DIRECTORY
        ) {
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
        if (!granted) {
            statusView?.setTextColor(themeColor(ThemeSlot.STATUS_WARNING))
        }
    }

    /** A loose note that belongs to no single item (a section intro): dim gold, no rule. */
    private fun addDescriptionRow(@StringRes textRes: Int) {
        val margin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val view = noteView(getString(textRes)).apply {
            setPadding(margin, px(2f), margin, px(4f))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        indentView(view, currentRowIndent)
        body.addView(view)
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

    // ---- Colour roles of what the commons draws (helpers/UiRoles.kt) ----

    // One swatch row per role of [section] that no slot of this app already shows under its own label.
    private fun addRoleRows(section: UiRoleSection) {
        val shownAsSlots = ThemeSlot.entries.mapNotNull { it.role }.toSet()
        UiRole.entries.filter { it.section == section && it !in shownAsSlots }.forEach { addRoleRow(it) }
    }

    // The "Default" badge shows while the role follows its default; the picker's default button resets it.
    private fun addRoleRow(role: UiRole) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, body, false)
        fun refresh() {
            row.themeColorPreview.background = swatch(uiColor(role))
            row.themeColorDefault.beVisibleIf(!isUiColorSet(role))
        }
        row.themeColorLabel.text = getString(role.labelRes)
        row.themeColorLabel.setTextColor(textColor)
        row.themeColorLabel.setRowIcon(RowIcon.COLOR)
        row.themeColorDefault.setTextColor(noteColor())
        refresh()
        row.root.setOnClickListener {
            AlphaColorPickerDialog(this, uiColor(role), addDefaultColorButton = true) { wasPositive, color ->
                if (wasPositive) setUiColor(role, color) else resetUiColor(role)
                config.themeRevision = config.themeRevision + 1
                refresh()
            }
        }
        tighten(row.root)
        indentView(row.root, currentRowIndent)
        body.addView(row.root)
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
        val row = ItemThemeColorBinding.inflate(layoutInflater, body, false)
        row.themeColorLabel.text = getString(R.string.theme_grid_line_color)
        row.themeColorLabel.setTextColor(textColor)
        row.themeColorLabel.setRowIcon(RowIcon.COLOR)
        row.themeColorPreview.background = swatch(gridLineColor(line))
        row.themeColorDefault.setTextColor(noteColor())
        row.themeColorDefault.beVisibleIf(config.getGridLineColor(line.key) == THEME_UNSET)
        row.root.setOnClickListener {
            AlphaColorPickerDialog(this, gridLineColor(line), addDefaultColorButton = true) { wasPositive, color ->
                config.setGridLineColor(line.key, if (wasPositive) color else THEME_UNSET)
                row.themeColorPreview.background = swatch(gridLineColor(line))
                row.themeColorDefault.beVisibleIf(config.getGridLineColor(line.key) == THEME_UNSET)
            }
        }
        tighten(row.root)
        indentView(row.root, currentRowIndent)
        body.addView(row.root)
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

    private fun addSubsection(@StringRes titleRes: Int) {
        val sub = ItemThemeSubsectionBinding.inflate(layoutInflater, body, false)
        sub.themeSubsectionLabel.text = getString(titleRes)
        sub.themeSubsectionLabel.setTextColor(primaryColor)
        sub.themeSubsectionUnderline.setBackgroundColor(primaryColor)
        indentView(sub.root, 1)
        body.addView(sub.root)
        currentRowIndent = 2
    }

    private fun addColorRow(slot: ThemeSlot) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, body, false)
        row.themeColorLabel.text = getString(slot.labelRes)
        row.themeColorLabel.setTextColor(textColor)
        row.themeColorLabel.setRowIcon(RowIcon.COLOR)
        row.themeColorPreview.background = swatch(themeColor(slot))
        row.themeColorDefault.setTextColor(noteColor())
        row.themeColorDefault.beVisibleIf(followsDefault(slot))
        row.root.setOnClickListener { openColorPicker(slot) }
        previews[slot] = row.themeColorPreview
        defaultBadges[slot] = row.themeColorDefault
        tighten(row.root)
        indentView(row.root, currentRowIndent)
        body.addView(row.root)
    }

    private fun addTextSlot(slot: ThemeSlot) {
        val b = ItemThemeTextBinding.inflate(layoutInflater, body, false)
        b.themeTextLabel.text = getString(slot.labelRes)
        listOf(
            b.themeTextLabel, b.themeTextFontTitle, b.themeTextFontValue,
            b.themeTextWeightTitle, b.themeTextWeightValue, b.themeTextSizeTitle, b.themeTextSizeValue
        ).forEach { it.setTextColor(textColor) }
        // the element's own line sets its colour; the lines beneath set its font, weight and size
        b.themeTextLabel.setRowIcon(RowIcon.COLOR)
        b.themeTextFontTitle.setRowIcon(RowIcon.FONT)
        b.themeTextWeightTitle.setRowIcon(RowIcon.WEIGHT)
        b.themeTextSizeTitle.setRowIcon(RowIcon.SIZE)

        b.themeTextColorPreview.background = swatch(themeColor(slot))
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

        listOf<View>(b.themeTextColorRow, b.themeTextFontRow, b.themeTextWeightRow, b.themeTextSizeRow).forEach { tighten(it) }
        b.themeTextSample.setPaddingRelative(b.themeTextSample.paddingStart, 0, b.themeTextSample.paddingEnd, px(4f))
        indentView(b.root, currentRowIndent)
        body.addView(b.root)
    }

    /**
     * A switch row. With an explanation it becomes one block: the label bold, the explanation beneath
     * in dim gold, and the thin dim-gold rule down its left edge.
     */
    private fun addToggleRow(
        @StringRes labelRes: Int,
        isChecked: Boolean,
        @StringRes descRes: Int? = null,
        icon: RowIcon = RowIcon.TOGGLE,
        onToggle: (Boolean) -> Unit,
    ) {
        val row = ItemThemeToggleBinding.inflate(layoutInflater, body, false)
        row.themeToggleSwitch.text = getString(labelRes)
        row.themeToggleSwitch.setColors(textColor, primaryColor, getProperBackgroundColor())
        row.themeToggleSwitch.isChecked = isChecked
        row.themeToggleSwitch.applyOutlinedSwitch()
        row.themeToggleSwitch.setRowIcon(icon)
        row.themeToggleHolder.setOnClickListener {
            row.themeToggleSwitch.toggle()
            onToggle(row.themeToggleSwitch.isChecked)
        }
        tighten(row.root)
        if (descRes == null) {
            indentView(row.root, currentRowIndent)
            body.addView(row.root)
            return
        }

        row.themeToggleSwitch.setTypeface(row.themeToggleSwitch.typeface, Typeface.BOLD)
        val endPadding = row.root.paddingEnd
        row.root.setPaddingRelative(0, px(2f), endPadding, 0)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row.root, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(noteView(getString(descRes)).apply { setPaddingRelative(0, 0, endPadding, px(2f)) })
        }
        val block = ruled(column)
        indentView(block, currentRowIndent)
        body.addView(block)
    }

    /** A slot follows its inherited default when it has no explicit override (write-through slots never do). */
    private fun followsDefault(slot: ThemeSlot): Boolean {
        slot.role?.let { return !slot.isFoundation && !isUiColorSet(it) }
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
                previews[slot]?.background = swatch(themeColor(slot))
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
                b.themeTextColorPreview.background = swatch(themeColor(slot))
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
        val row = ItemThemeValueBinding.inflate(layoutInflater, body, false)
        row.themeValueLabel.text = getString(R.string.theme_day_box_header_align)
        row.themeValueLabel.setTextColor(textColor)
        row.themeValueLabel.setRowIcon(RowIcon.SIZE)
        row.themeValueValue.setTextColor(noteColor())
        row.themeValueValue.text = alignmentText()
        row.root.setOnClickListener { openHeaderAlignmentPicker(row.themeValueValue) }
        tighten(row.root)
        indentView(row.root, currentRowIndent)
        body.addView(row.root)
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
        val row = ItemThemeValueBinding.inflate(layoutInflater, body, false)
        row.themeValueLabel.text = getString(R.string.theme_date_header_format)
        row.themeValueLabel.setTextColor(textColor)
        row.themeValueLabel.setRowIcon(RowIcon.DATE)
        row.themeValueValue.setTextColor(noteColor())
        row.themeValueValue.text = dateFormatSample()
        row.root.setOnClickListener { openDateHeaderFormatPicker(row.themeValueValue) }
        tighten(row.root)
        indentView(row.root, currentRowIndent)
        body.addView(row.root)
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

    // The title that marks a day off (exact match, surrounding spaces ignored).
    private fun addDayOffTitleRow() {
        val row = ItemThemeValueBinding.inflate(layoutInflater, body, false)
        row.themeValueLabel.text = getString(R.string.theme_day_off_title)
        row.themeValueLabel.setTextColor(textColor)
        row.themeValueLabel.setRowIcon(RowIcon.VALUE)
        row.themeValueValue.setTextColor(noteColor())
        row.themeValueValue.text = config.dayOffTitle
        row.root.setOnClickListener { openDayOffTitleDialog(row.themeValueValue) }
        tighten(row.root)
        indentView(row.root, currentRowIndent)
        body.addView(row.root)
    }

    private fun openDayOffTitleDialog(valueView: TextView) {
        val dialogBinding = DialogDayOffTitleBinding.inflate(layoutInflater)
        dialogBinding.dayOffTitle.setText(config.dayOffTitle)
        getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                val title = dialogBinding.dayOffTitle.text.toString().trim()
                config.dayOffTitle = title.ifEmpty { DAY_OFF_TITLE_DEFAULT }
                valueView.text = config.dayOffTitle
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                setupDialogStuff(dialogBinding.root, this, R.string.theme_day_off_title)
            }
    }

    private fun addEventTimeFormatRow() {
        val row = ItemThemeValueBinding.inflate(layoutInflater, body, false)
        row.themeValueLabel.text = getString(R.string.theme_event_time_format)
        row.themeValueLabel.setTextColor(textColor)
        row.themeValueLabel.setRowIcon(RowIcon.TIME)
        row.themeValueValue.setTextColor(noteColor())
        row.themeValueValue.text = eventTimeSample()
        row.root.setOnClickListener { openEventTimeFormatPicker(row.themeValueValue) }
        tighten(row.root)
        indentView(row.root, currentRowIndent)
        body.addView(row.root)
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
        val row = ItemThemeSliderBinding.inflate(layoutInflater, body, false)
        row.themeSliderLabel.text = getString(labelRes)
        row.themeSliderLabel.setTextColor(textColor)
        row.themeSliderLabel.setRowIcon(RowIcon.SIZE)
        row.themeSliderValue.setTextColor(noteColor())
        row.themeSliderValue.text = thicknessText(current)
        row.themeSliderSeekbar.min = min
        row.themeSliderSeekbar.max = max
        row.themeSliderSeekbar.progress = current
        row.themeSliderSeekbar.onSeekBarChangeListener {
            row.themeSliderValue.text = thicknessText(it)
            onChange(it)
        }
        tighten(row.root)
        indentView(row.root, currentRowIndent)
        body.addView(row.root)
    }

    // < 0 = inherit the general thickness, 0 = no border, otherwise the dp value.
    private fun thicknessText(dp: Int) = when {
        dp < 0 -> getString(R.string.theme_default_value)
        dp == 0 -> getString(R.string.theme_thickness_none)
        else -> getString(R.string.theme_thickness_value, dp)
    }

    // ---- The black-yellow page: pills for actions, dim-gold explanations, minimal padding ----

    private fun px(dp: Float) = (dp * resources.displayMetrics.density).roundToInt()

    private fun noteColor() = themeColor(ThemeSlot.SETTINGS_NOTE)

    private fun noteView(text: CharSequence) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(noteColor())
    }

    /** Minimal padding: a row is only as tall as its content, 2 dp above and below. */
    private fun tighten(view: View) {
        view.minimumHeight = 0
        view.setPaddingRelative(view.paddingStart, px(2f), view.paddingEnd, px(2f))
    }

    /** An item that carries an explanation, with the thin (2 dp) dim-gold rule down its left edge. */
    private fun ruled(content: View): View {
        val margin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPaddingRelative(margin, px(2f), 0, px(2f))
            addView(View(context).apply {
                setBackgroundColor(noteColor())
                layoutParams = LinearLayout.LayoutParams(px(2f), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    marginEnd = px(10f)
                }
            })
            addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /**
     * A row that does something when tapped: an outlined pill as wide as its label — never stretched
     * across the line — with a › when it opens something, 3 dp and bold for the main action of its
     * section. The explanation sits beneath in dim gold, aligned with the label, and the two share the
     * rule. Only the pill is the touch target. Returns the explanation, so a caller can refresh it.
     */
    @Suppress("LongParameterList")
    private fun addActionRow(
        title: String,
        desc: CharSequence?,
        main: Boolean,
        opens: Boolean,
        icon: RowIcon = RowIcon.SETTINGS,
        destructive: Boolean = false,
        onClick: () -> Unit,
    ): TextView? {
        val padH = px(16f)
        val padV = px(9f)
        var descUnderPill: TextView? = null
        val ink = uiColor(if (destructive) UiRole.BUTTON_DESTRUCTIVE else UiRole.BUTTON_TEXT)
        val frame = uiColor(if (destructive) UiRole.BUTTON_DESTRUCTIVE else UiRole.BUTTON_FRAME)
        val pill = TextView(this).apply {
            text = if (opens) "$title  ›" else title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(ink)
            if (main) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            // the explanation beneath starts under the label's text, wherever the icon's size puts that
            setRowIcon(icon) { gutter -> descUnderPill?.setPaddingRelative(padH + gutter, 0, padH, px(2f)) }
            background = pillDrawable(frame, uiColor(UiRole.BUTTON_GROUND), main)
            setPadding(padH, padV, padH, padV)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = px(3f)
                bottomMargin = px(3f)
            }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(pill)
        }
        val descView = desc?.let { text ->
            noteView(text).apply {
                setPaddingRelative(padH + pill.labelIconGutter(), 0, padH, px(2f))
                descUnderPill = this
                column.addView(this)
            }
        }
        val row = if (descView != null) {
            ruled(column)
        } else {
            val margin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
            column.apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPaddingRelative(margin, 0, margin, 0)
            }
        }
        indentView(row, currentRowIndent)
        body.addView(row)
        return descView
    }
}
