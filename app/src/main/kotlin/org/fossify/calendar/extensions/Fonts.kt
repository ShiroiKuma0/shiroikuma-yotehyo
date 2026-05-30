package org.fossify.calendar.extensions

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.TypedValue
import android.widget.TextView
import androidx.annotation.StringRes
import org.fossify.calendar.R
import org.fossify.commons.helpers.FontHelper
import java.io.File

private const val DEFAULT_SAMPLE_TEXT_SP = 16f
private const val SEMIBOLD_WEIGHT = 600
private const val BOLD_WEIGHT = 700
private val FONT_EXTENSIONS = setOf("ttf", "otf")

// Built-in families (mirroring the app's stock font options); sentinels that can't be real filenames.
const val MONOSPACE_FONT = "@monospace"

// A selectable font weight. value 0 = leave the family's own default weight.
enum class FontWeightOption(val value: Int, @StringRes val labelRes: Int) {
    DEFAULT(0, R.string.font_weight_default),
    THIN(100, R.string.font_weight_thin),
    LIGHT(300, R.string.font_weight_light),
    REGULAR(400, R.string.font_weight_regular),
    MEDIUM(500, R.string.font_weight_medium),
    SEMIBOLD(600, R.string.font_weight_semibold),
    BOLD(700, R.string.font_weight_bold),
    BLACK(900, R.string.font_weight_black);

    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

// One pickable font; an empty fileName means "system / global default".
data class FontOption(val displayName: String, val fileName: String)

private val typefaceCache = HashMap<String, Typeface>()

/** Built-in families + every font the user has imported (the imported set is shared across all elements). */
fun Context.availableFontOptions(): List<FontOption> {
    val options = mutableListOf(
        FontOption(getString(R.string.font_system_default), ""),
        FontOption(getString(R.string.font_monospace), MONOSPACE_FONT),
    )
    FontHelper.getFontsDir(this).listFiles()
        ?.filter { it.isFile && it.extension.lowercase() in FONT_EXTENSIONS }
        ?.sortedBy { it.name.lowercase() }
        ?.forEach { options.add(FontOption(it.nameWithoutExtension, it.name)) }
    return options
}

/** Human-readable name for a stored family value ("" = system default, sentinel = built-in, else filename). */
fun Context.fontDisplayName(fileName: String): String = when {
    fileName.isEmpty() -> getString(R.string.font_system_default)
    fileName == MONOSPACE_FONT -> getString(R.string.font_monospace)
    else -> File(fileName).nameWithoutExtension
}

/** Typeface for a stored family value ("" = system default, sentinel = built-in, else custom file), cached. */
@Suppress("TooGenericExceptionCaught", "SwallowedException") // a bad/corrupt font file just falls back to default
fun Context.fontTypeface(fileName: String): Typeface = when {
    fileName.isEmpty() -> Typeface.DEFAULT
    fileName == MONOSPACE_FONT -> Typeface.MONOSPACE
    else -> typefaceCache.getOrPut(fileName) {
        try {
            Typeface.createFromFile(File(FontHelper.getFontsDir(this), fileName))
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
    }
}

/** Combine a family + weight with a base text style (e.g. a header's bold). */
fun Context.themeTypeface(family: String, weight: Int, baseStyle: Int = Typeface.NORMAL): Typeface {
    val base = fontTypeface(family)
    if (weight <= 0) {
        return Typeface.create(base, baseStyle)
    }

    val italic = baseStyle == Typeface.ITALIC || baseStyle == Typeface.BOLD_ITALIC
    val bold = baseStyle == Typeface.BOLD || baseStyle == Typeface.BOLD_ITALIC
    val effectiveWeight = if (bold) maxOf(weight, BOLD_WEIGHT) else weight
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(base, effectiveWeight, italic)
    } else {
        Typeface.create(base, if (effectiveWeight >= SEMIBOLD_WEIGHT) Typeface.BOLD else Typeface.NORMAL)
    }
}

// The today/weekend header text inherit the day-box header text's font (family / weight / size)
// per field until they are given their own — matching the inherited text color (see themeDefault).
private fun ThemeSlot.fontParent(): ThemeSlot? = when (this) {
    ThemeSlot.TODAY_TEXT, ThemeSlot.WEEKEND_TEXT -> ThemeSlot.DAY_BOX_HEADER_TEXT
    else -> null
}

/** The effective font family for a slot: its own if set, else its parent's, else the system default. */
fun Context.effectiveFontFamily(slot: ThemeSlot): String {
    val own = config.getFontFamily(slot.key)
    return if (own.isNotEmpty()) own else slot.fontParent()?.let { config.getFontFamily(it.key) } ?: ""
}

/** The effective font weight for a slot: its own if set, else its parent's, else 0 (family default). */
fun Context.effectiveFontWeight(slot: ThemeSlot): Int {
    val own = config.getFontWeight(slot.key)
    return if (own > 0) own else slot.fontParent()?.let { config.getFontWeight(it.key) } ?: 0
}

/** The effective font size (sp) for a slot: its own if set, else its parent's, else 0 (no override). */
fun Context.effectiveFontSize(slot: ThemeSlot): Int {
    val own = config.getFontSize(slot.key)
    return if (own > 0) own else slot.fontParent()?.let { config.getFontSize(it.key) } ?: 0
}

/** The configured typeface (family + weight) for a slot, with parent inheritance. */
fun Context.themeTypeface(slot: ThemeSlot, baseStyle: Int = Typeface.NORMAL): Typeface =
    themeTypeface(effectiveFontFamily(slot), effectiveFontWeight(slot), baseStyle)

/** Apply a text slot's configured family + weight + size to a real text view (size only overrides if set). */
fun TextView.applyThemeFont(slot: ThemeSlot, baseStyle: Int = Typeface.NORMAL) {
    val ctx = context
    typeface = ctx.themeTypeface(slot, baseStyle)
    val sizeSp = ctx.effectiveFontSize(slot)
    if (sizeSp > 0) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
    }
}

/** Render the live sample line in an explicit family/weight/size/color (used by the on-page preview). */
fun TextView.showFontSample(family: String, weight: Int, sizeSp: Int, color: Int) {
    text = context.getString(R.string.font_sample_text)
    typeface = context.themeTypeface(family, weight)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (sizeSp > 0) sizeSp.toFloat() else DEFAULT_SAMPLE_TEXT_SP)
    setTextColor(color)
}

/** Copy a picked font file into the shared app fonts dir; returns its filename, or null on failure. */
fun Context.importFont(uri: Uri): String? {
    val name = fontFileName(uri) ?: return null
    if (name.substringAfterLast('.', "").lowercase() !in FONT_EXTENSIONS) {
        return null
    }
    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (!FontHelper.saveFontData(this, bytes, name)) {
        return null
    }
    typefaceCache.remove(name)
    return name
}

private fun Context.fontFileName(uri: Uri): String? {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                return cursor.getString(index)
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}
