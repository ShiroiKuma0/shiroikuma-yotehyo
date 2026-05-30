package org.fossify.calendar

import org.fossify.calendar.extensions.hasDummyAlarm
import org.fossify.calendar.extensions.seedBlackYellowThemeIfNeeded
import org.fossify.calendar.jobs.AppStartupWorker
import org.fossify.commons.FossifyApp
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.helpers.SIDELOADING_FALSE

class App : FossifyApp() {
    override fun onCreate() {
        super.onCreate()
        // Fossify Commons' sideloading detection probes for a Commons drawable that resource
        // shrinking strips from our custom-signed build, then shows a "corrupt/fake version"
        // dialog. Mark the app as not sideloaded here — before ANY activity runs its check
        // (incl. EventActivity / TaskActivity cold-started straight from a notification or widget,
        // which never pass through SplashActivity).
        baseConfig.appSideloadingStatus = SIDELOADING_FALSE
        // Apply the default black/yellow look once, before any activity themes itself.
        seedBlackYellowThemeIfNeeded()
        if (!hasDummyAlarm()) {
            AppStartupWorker.start(this)
        }
    }
}
