package org.fossify.calendar.dialogs

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import org.fossify.calendar.R
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.helpers.SettingsTransfer
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.ensureBackgroundThread

/**
 * The Export/Import panel opened from the top section of 白い熊 予定表 UI (Kōjiki flow): the persisted
 * export directory box with a "last export" line, one checkbox per category, and an ArcaneChat-style
 * button row — round pills, Cancel alone on the left, Import / Export grouped on the right. The
 * SAF pickers live in ThemeActivity (launchers must be registered before RESUMED) and feed results
 * back through the on*Picked callbacks. Success info dialogs close the whole chain (info dialog →
 * this panel → the UI settings page via [onChainClose]); failures leave the panel open.
 */
class ExportImportDialog(
    private val activity: SimpleActivity,
    private val launchDirPicker: (initial: Uri?) -> Unit,
    private val launchImportPicker: () -> Unit,
    private val launchExportCreate: (suggestedName: String) -> Unit,
    private val onChainClose: () -> Unit,
) {
    private val checks = LinkedHashMap<SettingsTransfer.Category, CheckBox>()
    private var dialog: AlertDialog? = null
    private var folderValueTv: TextView? = null
    private var statusTv: TextView? = null

    private val accentColor = activity.getProperPrimaryColor()
    private val textColor = activity.getProperTextColor()
    private val backgroundColor = activity.getProperBackgroundColor()
    private val density = activity.resources.displayMetrics.density

    init {
        val view = buildView()
        activity.getAlertDialogBuilder().apply {
            activity.setupDialogStuff(view, this, R.string.eim_heading) { alertDialog ->
                dialog = alertDialog
            }
        }
        refreshStatus()
    }

    // ---------- Results forwarded from ThemeActivity's SAF launchers ----------

    fun onDirPicked(uri: Uri?) {
        if (uri == null) return
        SettingsTransfer.setDirUri(activity, uri)
        refreshStatus()
    }

    fun onImportFilePicked(uri: Uri?) {
        if (uri != null) doImport(uri)
    }

    fun onExportTargetPicked(uri: Uri?) {
        if (uri != null) exportToUri(uri)
    }

    // ---------- The panel ----------

    private fun dp(v: Int) = (v * density).toInt()

    private fun buildView(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(12))
        }

        root.addView(text(activity.getString(R.string.eim_desc), 13f, textColor).apply {
            alpha = 0.85f
            setPadding(0, 0, 0, dp(10))
        })

        // Persisted export directory — a bordered, clearly-tappable box that stands out when unset.
        val dirBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = 10 * density
                setColor(backgroundColor)
                setStroke(dp(2), accentColor)
            }
            setOnClickListener { launchDirPicker(SettingsTransfer.dirUri(activity)) }
        }
        dirBox.addView(text(activity.getString(R.string.eim_dir), 12f, accentColor))
        folderValueTv = text("", 15f, textColor, bold = true)
        dirBox.addView(folderValueTv)
        root.addView(
            dirBox,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = dp(6); it.bottomMargin = dp(6) }
        )
        statusTv = text("", 14f, textColor).apply { setPadding(dp(2), 0, 0, dp(8)) }
        root.addView(statusTv)

        root.addView(divider())

        // The category checklist, in the family's shape: a 全選択 master toggle, then each top-level
        // category, with its parts (sub-options) indented beneath it and following its toggle. What
        // starts ticked comes from Category.defaultOn — the same answer LIST_CATEGORIES sends 自由作業盤,
        // so this sheet and the automation picker open on the same selection.
        val selectAll = checkbox(activity.getString(R.string.eim_select_all), bold = true).apply {
            isChecked = SettingsTransfer.Category.entries.all { it.defaultOn }
        }
        root.addView(selectAll)
        for (cat in SettingsTransfer.Category.entries.filter { it.isTopLevel }) {
            val cb = checkbox(activity.getString(cat.labelRes)).apply { isChecked = cat.defaultOn }
            checks[cat] = cb
            root.addView(cb)
            for (child in cat.children) {
                val childCb = checkbox(activity.getString(child.labelRes), indent = 1)
                    .apply { isChecked = child.defaultOn }
                checks[child] = childCb
                root.addView(childCb)
            }
            if (cat.children.isNotEmpty()) {
                cb.setOnCheckedChangeListener { _, isChecked ->
                    cat.children.forEach { checks[it]?.isChecked = isChecked }
                }
            }
        }
        selectAll.setOnCheckedChangeListener { _, isChecked ->
            checks.values.forEach { it.isChecked = isChecked }
        }

        root.addView(divider().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(8) })

        // ArcaneChat-style dialog button row: round pills, Cancel alone on the left, the
        // Import / Export actions grouped on the right.
        val buttons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        buttons.addView(pillButton(org.fossify.commons.R.string.cancel).apply {
            setOnClickListener { dialog?.dismiss() }
        })
        buttons.addView(View(activity), LinearLayout.LayoutParams(0, 0, 1f))
        buttons.addView(pillButton(R.string.eim_import).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = dp(8) }
            setOnClickListener { onImportClicked() }
        })
        buttons.addView(pillButton(R.string.eim_export).apply {
            setOnClickListener { onExportClicked() }
        })
        root.addView(buttons)

        return ScrollView(activity).apply { addView(root) }
    }

    private fun text(content: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(activity).apply {
            text = content
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun checkbox(label: String, bold: Boolean = false, indent: Int = 0): CheckBox =
        CheckBox(activity).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            buttonTintList = ColorStateList.valueOf(accentColor)
            setPadding(dp(8), dp(7), 0, dp(7))
            if (indent > 0) {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.marginStart = indent * dp(24) }
            }
        }

    private fun divider(): View = View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        setBackgroundColor(accentColor)
        alpha = 0.4f
    }

    // A round-pill outline button (ArcaneChat recipe): theme-background fill, huge corner radius,
    // thin accent stroke, accent text, accent ripple.
    private fun pillButton(@StringRes labelRes: Int): Button {
        val pill = GradientDrawable().apply {
            cornerRadius = 100 * density // > half the height → a pill
            setColor(backgroundColor)
            setStroke((1.5f * density).toInt(), accentColor)
        }
        val ripple = RippleDrawable(ColorStateList.valueOf(accentColor.adjustAlpha(0.2f)), pill, null)
        return Button(activity).apply {
            text = activity.getString(labelRes)
            isAllCaps = false
            setTextColor(accentColor)
            background = ripple
            stateListAnimator = null
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(20), dp(10), dp(20), dp(10))
        }
    }

    private fun selectedCats(): Set<SettingsTransfer.Category> = checks.filterValues { it.isChecked }.keys

    private fun refreshStatus() {
        folderValueTv?.text = ""
        ensureBackgroundThread {
            val name = SettingsTransfer.exportDir(activity)?.name
                ?: SettingsTransfer.dirUri(activity)?.lastPathSegment
            val (status, warn) = SettingsTransfer.lastExportStatus(activity)
            activity.runOnUiThread {
                folderValueTv?.text = name ?: activity.getString(R.string.eim_dir_unset)
                folderValueTv?.setTextColor(if (name == null) SettingsTransfer.WARN_COLOR else textColor)
                statusTv?.text = status
                statusTv?.setTextColor(if (warn) SettingsTransfer.WARN_COLOR else textColor)
                statusTv?.alpha = if (warn) 1f else 0.8f
            }
        }
    }

    // ---------- Export ----------

    private fun onExportClicked() {
        val cats = selectedCats()
        if (cats.isEmpty()) {
            showFailure(activity.getString(R.string.eim_none_selected))
            return
        }
        val dir = SettingsTransfer.exportDir(activity)
        if (dir == null) {
            launchExportCreate(SettingsTransfer.exportFileName()) // no folder set → save-as picker
        } else {
            exportToFolder(dir, cats)
        }
    }

    private fun exportToFolder(dir: DocumentFile, cats: Set<SettingsTransfer.Category>) {
        ensureBackgroundThread {
            val result = runCatching {
                val name = SettingsTransfer.exportFileName()
                val file = dir.createFile("application/zip", name) ?: error("could not create file in folder")
                runCatching {
                    activity.contentResolver.openOutputStream(file.uri)?.use { out ->
                        SettingsTransfer.export(activity, cats, out)
                    } ?: error("no output stream")
                }.onFailure {
                    // Same rule as the headless path: a run that failed leaves the backup directory as
                    // it found it, so no truncated ZIP is left to be read as the last export.
                    runCatching { file.delete() }
                }.getOrThrow()
                name
            }
            activity.runOnUiThread {
                result
                    .onSuccess { showExportDone(it) }
                    .onFailure { showFailure(activity.getString(R.string.eim_export_fail, it.message ?: "")) }
            }
        }
    }

    private fun exportToUri(uri: Uri) {
        val cats = selectedCats()
        ensureBackgroundThread {
            val result = runCatching {
                activity.contentResolver.openOutputStream(uri)?.use { out ->
                    SettingsTransfer.export(activity, cats, out)
                } ?: error("no output stream")
                uri.lastPathSegment?.substringAfterLast('/') ?: "export"
            }
            activity.runOnUiThread {
                result
                    .onSuccess { showExportDone(it) }
                    .onFailure { showFailure(activity.getString(R.string.eim_export_fail, it.message ?: "")) }
            }
        }
    }

    // ---------- Import ----------

    private fun onImportClicked() {
        if (selectedCats().isEmpty()) {
            showFailure(activity.getString(R.string.eim_none_selected))
            return
        }
        launchImportPicker()
    }

    private fun doImport(uri: Uri) {
        val cats = selectedCats()
        ensureBackgroundThread {
            val result = runCatching {
                val bytes = activity.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("no input stream")
                require(SettingsTransfer.categoriesIn(bytes).isNotEmpty()) {
                    activity.getString(R.string.eim_import_none)
                }
                SettingsTransfer.import(activity, bytes, cats)
            }
            activity.runOnUiThread {
                result
                    .onSuccess { showImportDone(it) }
                    .onFailure { showFailure(activity.getString(R.string.eim_import_fail, it.message ?: "")) }
            }
        }
    }

    // ---------- Result dialogs ----------

    private fun messageView(message: String): View = TextView(activity).apply {
        text = message
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(textColor)
        setPadding(dp(24), dp(4), dp(24), dp(4))
    }

    // Success: acknowledging the info dialog closes the whole chain — it, this panel and the UI page.
    private fun showExportDone(name: String) {
        refreshStatus()
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> closeChain() }
            .apply {
                activity.setupDialogStuff(
                    view = messageView(activity.getString(R.string.eim_export_done_body, name)),
                    dialog = this,
                    titleId = R.string.eim_export_done_title,
                    cancelOnTouchOutside = false
                ) { it.setCancelable(false) }
            }
    }

    // Success: "Restart now" restarts the app; "Later" closes the whole chain like the export OK.
    private fun showImportDone(summary: String) {
        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.eim_restart_now) { _, _ -> restartApp() }
            .setNegativeButton(R.string.eim_restart_later) { _, _ -> closeChain() }
            .apply {
                activity.setupDialogStuff(
                    view = messageView(activity.getString(R.string.eim_import_done_body, summary)),
                    dialog = this,
                    titleId = R.string.eim_import_done_title,
                    cancelOnTouchOutside = false
                ) { it.setCancelable(false) }
            }
    }

    // Failure: acknowledging only dismisses the info dialog — the panel stays open.
    private fun showFailure(message: String) {
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .apply {
                activity.setupDialogStuff(view = messageView(message), dialog = this)
            }
    }

    private fun closeChain() {
        dialog?.dismiss()
        onChainClose()
    }

    private fun restartApp() {
        val context: Context = activity.applicationContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(launchIntent.component))
        Runtime.getRuntime().exit(0)
    }
}
