package org.fossify.calendar.extensions

import android.content.Context
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.getNowSeconds
import org.joda.time.Days

/**
 * Unfinished tasks roll over to today.
 *
 * A one-off task that is still unticked once its own day is over is moved forward to today, keeping
 * its clock time, so it stays in sight until it is actually done. Ticking a task off freezes it on
 * the day it was completed. Repeating tasks are left alone: their occurrences come from the
 * repetition rule rather than from the stored timestamp, so moving the row would shift every one.
 * An entry marked fixed is left alone too: it is a record of something that happened on its date,
 * not a to-do, so it has no checkbox to tick and nothing to carry forward.
 *
 * This is a real move — `start_ts` / `end_ts` of the stored task are rewritten, reminders are
 * rescheduled onto the new day, and a CalDAV task is pushed upstream. It runs at most once per day
 * (guarded by the last-rollover day code) and is called from `EventsHelper.getEventsSync`, the one
 * path every view and every widget fetches through.
 */
fun Context.rollOverIncompleteTasks() {
    if (!config.rollOverIncompleteTasks) {
        return
    }

    val todayCode = Formatter.getTodayCode()
    if (config.lastTaskRolloverDayCode == todayCode) {
        return
    }

    // Claim the day before touching anything: the work below fetches events again (rescheduling
    // reminders, refreshing widgets), and the claim is what keeps that from re-entering here.
    config.lastTaskRolloverDayCode = todayCode

    // a failed read must not take the surrounding fetch down with it — the views come first
    val overdueTasks = runCatching {
        eventsDB.getOneTimeTasksBefore(Formatter.getDayStartTS(todayCode))
            .filter { !it.isFixedEntry() && !isTaskCompleted(it) }
    }.getOrElse { return }

    if (overdueTasks.isEmpty()) {
        return
    }

    val today = Formatter.getDateTimeFromTS(getNowSeconds()).toLocalDate()
    var movedAny = false
    overdueTasks.forEach { task ->
        val oldStartTS = task.startTS
        val oldStart = Formatter.getDateTimeFromTS(oldStartTS)
        // plusDays() keeps the clock time across a DST change, which withDate() would not
        val daysBehind = Days.daysBetween(oldStart.toLocalDate(), today).days
        val newStartTS = oldStart.plusDays(daysBehind).seconds()
        if (daysBehind <= 0 || newStartTS == oldStartTS) {
            return@forEach
        }

        // a completion record keyed to the day it is leaving would otherwise linger unreachable
        completedTasksDB.deleteTaskWithIdAndTs(task.id!!, oldStartTS)
        task.startTS = newStartTS
        task.endTS = newStartTS
        task.lastUpdated = System.currentTimeMillis()
        eventsHelper.updateEvent(
            event = task,
            updateAtCalDAV = true,
            showToasts = false,
            // a hidden calendar stays hidden — a task moving is no reason to unfilter it
            enableCalendar = false,
            // the widgets are refreshed once at the end instead of once per moved task
            updateWidgets = false
        )
        movedAny = true
    }

    if (movedAny) {
        updateWidgets()
    }
}
