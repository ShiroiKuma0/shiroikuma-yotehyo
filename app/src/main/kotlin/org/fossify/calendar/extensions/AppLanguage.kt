package org.fossify.calendar.extensions

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.LocaleList
import org.fossify.calendar.BuildConfig
import org.fossify.calendar.R
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.helpers.PREFS_KEY
import org.fossify.commons.models.RadioItem
import java.util.Locale
import kotlin.system.exitProcess

// The app's own language, chosen in Settings and applied by the app itself — the system language
// and Android's per-app language screen (13+) are left alone. Stored as a BCP 47 tag; empty means
// "follow the system". Read straight from the preferences file, because an activity asks for it in
// attachBaseContext, before it has an application context.
const val APP_LANGUAGE = "app_language"

private fun Context.languagePrefs() = getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)

var Context.appLanguage: String
    get() = languagePrefs().getString(APP_LANGUAGE, "").orEmpty()
    set(value) = languagePrefs().edit().putString(APP_LANGUAGE, value).apply()

/** Every language the app carries strings for, as BCP 47 tags; English, the unqualified one, first. */
val appLanguageTags: List<String> get() = BuildConfig.APP_LOCALES.split(",")

/** The language's name in that language — 日本語, Čeština — which is how a picker should list it. */
fun languageDisplayName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

private fun Configuration.withLanguage(tag: String) = Configuration(this).apply {
    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    setLocales(LocaleList(locale))
}

/** For an activity's attachBaseContext: the same context, speaking the chosen language. */
fun Context.withAppLanguage(): Context {
    val tag = appLanguage
    return if (tag.isEmpty()) this else createConfigurationContext(resources.configuration.withLanguage(tag))
}

// The application's resources are shared by every receiver, service and worker — the notifications
// and the widgets are built from them. They are reset by each configuration change (a fold, a
// rotation), so this runs at start and again from onConfigurationChanged.
@Suppress("DEPRECATION")
fun Application.applyAppLanguage() {
    val tag = appLanguage
    if (tag.isEmpty()) return
    resources.updateConfiguration(resources.configuration.withLanguage(tag), resources.displayMetrics)
}

// "Use English language" was the only choice before the picker; an install that had it on keeps
// English. The commons switch is cleared so that its own wrapper no longer competes with ours.
fun Application.migrateUseEnglishToAppLanguage() {
    if (!baseConfig.useEnglish) return
    if (appLanguage.isEmpty()) appLanguage = "en"
    baseConfig.useEnglish = false
}

/** What the language row shows: the chosen language in its own name, or "System default". */
fun Context.appLanguageLabel(): String {
    val tag = appLanguage
    return if (tag.isEmpty()) getString(org.fossify.commons.R.string.system_default) else languageDisplayName(tag)
}

// The picker of the UI page: "System default", then every language under its own name. A new choice
// restarts the app — the process default locale has changed, and every screen must be rebuilt.
fun Activity.showAppLanguagePicker() {
    val current = appLanguage
    val tags = appLanguageTags.sortedBy { languageDisplayName(it).lowercase() }
    val items = arrayListOf(RadioItem(0, getString(org.fossify.commons.R.string.system_default), ""))
    tags.forEachIndexed { index, tag -> items.add(RadioItem(index + 1, languageDisplayName(tag), tag)) }
    val checked = items.firstOrNull { it.value == current }?.id ?: 0
    RadioGroupDialog(this, items, checked, R.string.app_language) { picked ->
        val tag = picked as String
        if (tag != current) {
            // commit, not apply: the process is about to end
            getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE).edit().putString(APP_LANGUAGE, tag).commit()
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                startActivity(Intent.makeRestartActivityTask(it.component))
            }
            exitProcess(0)
        }
    }
}
