package org.fossify.calendar

import android.content.res.Configuration
import org.fossify.calendar.extensions.applyAppLanguage
import org.fossify.calendar.extensions.hasDummyAlarm
import org.fossify.calendar.extensions.migrateUseEnglishToAppLanguage
import org.fossify.calendar.extensions.migrateSlotsToRoles
import org.fossify.calendar.extensions.migrateToPureYellowIfNeeded
import org.fossify.calendar.extensions.seedBlackYellowThemeIfNeeded
import org.fossify.calendar.extensions.seedDialogStyleIfNeeded
import org.fossify.calendar.extensions.seedPillDialogButtonsIfNeeded
import org.fossify.calendar.extensions.seedTodayBoxBorderIfNeeded
import org.fossify.calendar.jobs.AppStartupWorker
import org.fossify.commons.FossifyApp

class App : FossifyApp() {
    override fun onCreate() {
        super.onCreate()
        // The app's own language, before anything builds a string.
        migrateUseEnglishToAppLanguage()
        applyAppLanguage()
        // Apply the default black/yellow look once, before any activity themes itself.
        seedBlackYellowThemeIfNeeded()
        // Seed the yellow dialog border + boxed buttons once (separate flag so existing installs get it).
        seedDialogStyleIfNeeded()
        seedPillDialogButtonsIfNeeded()
        // Seed the blue border around today's day box once (own flag, so existing installs get it).
        seedTodayBoxBorderIfNeeded()
        // Rewrite any persisted material-yellow (#FFEB3B) colors to pure yellow once (alpha kept).
        migrateToPureYellowIfNeeded()
        migrateSlotsToRoles()
        if (!hasDummyAlarm()) {
            AppStartupWorker.start(this)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyAppLanguage()
    }
}
