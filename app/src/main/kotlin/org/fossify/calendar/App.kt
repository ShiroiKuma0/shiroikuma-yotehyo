package org.fossify.calendar

import org.fossify.calendar.extensions.hasDummyAlarm
import org.fossify.calendar.extensions.migrateToPureYellowIfNeeded
import org.fossify.calendar.extensions.seedBlackYellowThemeIfNeeded
import org.fossify.calendar.extensions.seedDialogStyleIfNeeded
import org.fossify.calendar.extensions.seedTodayBoxBorderIfNeeded
import org.fossify.calendar.jobs.AppStartupWorker
import org.fossify.commons.FossifyApp

class App : FossifyApp() {
    override fun onCreate() {
        super.onCreate()
        // Apply the default black/yellow look once, before any activity themes itself.
        seedBlackYellowThemeIfNeeded()
        // Seed the yellow dialog border + boxed buttons once (separate flag so existing installs get it).
        seedDialogStyleIfNeeded()
        // Seed the blue border around today's day box once (own flag, so existing installs get it).
        seedTodayBoxBorderIfNeeded()
        // Rewrite any persisted material-yellow (#FFEB3B) colors to pure yellow once (alpha kept).
        migrateToPureYellowIfNeeded()
        if (!hasDummyAlarm()) {
            AppStartupWorker.start(this)
        }
    }
}
