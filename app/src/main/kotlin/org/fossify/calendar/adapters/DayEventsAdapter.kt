package org.fossify.calendar.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import org.fossify.calendar.R
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.databinding.EventListItemBinding
import org.fossify.calendar.dialogs.DeleteEventDialog
import org.fossify.calendar.extensions.*
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.THEME_UNSET
import org.fossify.calendar.models.Event
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.MEDIUM_ALPHA
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView

class DayEventsAdapter(activity: SimpleActivity, val events: ArrayList<Event>, recyclerView: MyRecyclerView, var dayCode: String, itemClick: (Any) -> Unit) :
    MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private val allDayString = resources.getString(R.string.all_day)
    private val displayDescription = activity.config.displayDescription
    private val replaceDescriptionWithLocation = activity.config.replaceDescription
    private val dimPastEvents = activity.config.dimPastEvents
    private val dimCompletedTasks = activity.config.dimCompletedTasks
    private var isPrintVersion = false
    private val mediumMargin = activity.resources.getDimension(org.fossify.commons.R.dimen.medium_margin).toInt()

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_day

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        when (id) {
            R.id.cab_share -> shareEvents()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = events.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = events.getOrNull(position)?.id?.toInt()

    override fun getItemKeyPosition(key: Int) = events.indexOfFirst { it.id?.toInt() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(
            view = EventListItemBinding.inflate(activity.layoutInflater, parent, false).root
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        holder.bindView(event, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, event)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = events.size

    fun togglePrintMode() {
        isPrintVersion = !isPrintVersion
        textColor = if (isPrintVersion) {
            resources.getColor(org.fossify.commons.R.color.theme_light_text_color)
        } else {
            activity.getProperTextColor()
        }

        notifyDataSetChanged()
    }

    private fun setupView(view: View, event: Event) {
        EventListItemBinding.bind(view).apply {
            eventItemHolder.isSelected = selectedKeys.contains(event.id?.toInt())
            // Category background fills the row when set (selection still shows via the foreground);
            // otherwise restore the default stroke. Reset on every bind so recycled rows stay correct.
            if (event.categoryBackgroundColor != THEME_UNSET) {
                eventItemHolder.setBackgroundColor(event.categoryBackgroundColor)
            } else {
                eventItemHolder.setBackgroundResource(org.fossify.commons.R.drawable.section_holder_stroke)
                eventItemHolder.background.applyColorFilter(textColor)
            }
            val tzAnnotation = if (event.getIsAllDay()) {
                null
            } else {
                eventTimeZoneAnnotation(event.startTS, event.endTS, event.getTimeZoneString(), event.getEndTimeZoneString())
            }
            eventItemTitle.text = if (tzAnnotation != null) "${event.title}$tzAnnotation" else event.title
            eventItemTitle.checkViewStrikeThrough(event.shouldStrikeThrough())
            val timeFormat = activity.config.eventTimeFormat
            eventItemTime.text = if (event.getIsAllDay()) allDayString else formatEventClock(event.startTS, timeFormat)
            if (event.startTS != event.endTS) {
                val startDayCode = Formatter.getDayCodeFromTS(event.startTS)
                val endDayCode = Formatter.getDayCodeFromTS(event.endTS)
                val startDate = Formatter.getDayTitle(activity, startDayCode, false)
                val endDate = Formatter.getDayTitle(activity, endDayCode, false)
                val startDayString = if (startDayCode != dayCode) " ($startDate)" else ""
                if (!event.getIsAllDay()) {
                    val endTimeString = formatEventClock(event.endTS, timeFormat)
                    val endDayString = if (endDayCode != dayCode) " ($endDate)" else ""
                    val separator = eventTimeSeparator(timeFormat)
                    val duration = eventDurationSuffix(event.startTS, event.endTS, timeFormat)
                    eventItemTime.text = "${eventItemTime.text}$startDayString$separator$endTimeString$endDayString$duration"
                } else {
                    val endDayString = if (endDayCode != dayCode) " - ($endDate)" else ""
                    eventItemTime.text = "${eventItemTime.text}$startDayString$endDayString"
                }
            }

            eventItemDescription.text = if (replaceDescriptionWithLocation) event.location else event.description.replace("\n", " ")
            eventItemDescription.beVisibleIf(displayDescription && eventItemDescription.text.isNotEmpty())
            eventItemColorBar.background.applyColorFilter(event.color)

            var newTextColor = textColor

            val adjustAlpha = if (event.isTask()) {
                dimCompletedTasks && event.isTaskCompleted()
            } else {
                dimPastEvents && event.isPastEvent && !isPrintVersion
            }

            if (adjustAlpha) {
                newTextColor = newTextColor.adjustAlpha(MEDIUM_ALPHA)
            }

            eventItemTime.setTextColor(newTextColor)
            eventItemTitle.setTextColor(newTextColor)
            eventItemDescription.setTextColor(newTextColor)
            eventItemTitle.applyThemeFont(ThemeSlot.EVENT_TEXT)
            eventItemTime.applyThemeFont(ThemeSlot.TEXT_SECONDARY)
            eventItemDescription.applyThemeFont(ThemeSlot.TEXT_SECONDARY)
            // The event title takes its category (event type) colour, and the category font when set.
            eventItemTitle.setTextColor(if (adjustAlpha) event.color.adjustAlpha(MEDIUM_ALPHA) else event.color)
            if (event.categoryFontFamily.isNotEmpty() || event.categoryFontWeight > 0 || event.categoryFontSize > 0) {
                eventItemTitle.applyCategoryFont(event.categoryFontFamily, event.categoryFontWeight, event.categoryFontSize)
            }
            eventItemTaskImage.applyColorFilter(newTextColor)
            eventItemTaskImage.beVisibleIf(event.isTask())

            val startMargin = if (event.isTask()) {
                0
            } else {
                mediumMargin
            }

            (eventItemTitle.layoutParams as ConstraintLayout.LayoutParams).marginStart = startMargin
        }
    }

    private fun shareEvents() = activity.shareEvents(selectedKeys.distinct().map { it.toLong() })

    private fun askConfirmDelete() {
        val eventIds = selectedKeys.map { it.toLong() }.toMutableList()
        val eventsToDelete = events.filter { selectedKeys.contains(it.id?.toInt()) }
        val timestamps = eventsToDelete.map { it.startTS }
        val positions = getSelectedItemPositions()

        val hasRepeatableEvent = eventsToDelete.any { it.repeatInterval > 0 }
        DeleteEventDialog(activity, eventIds, hasRepeatableEvent) { it ->
            events.removeAll(eventsToDelete)

            ensureBackgroundThread {
                val nonRepeatingEventIDs = eventsToDelete.asSequence().filter { it.repeatInterval == 0 }.mapNotNull { it.id }.toMutableList()
                activity.eventsHelper.deleteEvents(nonRepeatingEventIDs, true)

                val repeatingEventIDs = eventsToDelete.asSequence().filter { it.repeatInterval != 0 }.mapNotNull { it.id }.toList()
                activity.handleEventDeleting(repeatingEventIDs, timestamps, it)
                activity.runOnUiThread {
                    removeSelectedItems(positions)
                }
            }
        }
    }
}
