package org.fossify.calendar.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import org.fossify.calendar.R
import org.fossify.calendar.activities.MainActivity
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.adapters.DayEventsAdapter
import org.fossify.calendar.databinding.FragmentDayBinding
import org.fossify.calendar.databinding.TopNavigationBinding
import org.fossify.calendar.extensions.ThemeSlot
import org.fossify.calendar.extensions.applyThemeFont
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.extensions.getViewBitmap
import org.fossify.calendar.extensions.launchNewEventIntent
import org.fossify.calendar.extensions.launchNewTaskIntent
import org.fossify.calendar.extensions.printBitmap
import org.fossify.calendar.helpers.*
import org.fossify.calendar.interfaces.NavigationListener
import org.fossify.calendar.models.Event
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.models.RadioItem

class DayFragment : Fragment() {
    var mListener: NavigationListener? = null
    private var mTextColor = 0
    private var mDayCode = ""
    private var lastHash = 0

    private lateinit var binding: FragmentDayBinding
    private lateinit var topNavigationBinding: TopNavigationBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDayBinding.inflate(inflater, container, false)
        topNavigationBinding = TopNavigationBinding.bind(binding.root)
        mDayCode = requireArguments().getString(DAY_CODE)!!
        setupButtons()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        updateCalendar()
    }

    private fun setupButtons() {
        mTextColor = requireContext().getProperTextColor()

        topNavigationBinding.topLeftArrow.apply {
            applyColorFilter(mTextColor)
            background = null
            setOnClickListener {
                mListener?.goLeft()
            }

            val pointerLeft = requireContext().getDrawable(org.fossify.commons.R.drawable.ic_chevron_left_vector)
            pointerLeft?.isAutoMirrored = true
            setImageDrawable(pointerLeft)
            contentDescription = getString(R.string.accessibility_previous_day)
        }

        topNavigationBinding.topRightArrow.apply {
            applyColorFilter(mTextColor)
            background = null
            setOnClickListener {
                mListener?.goRight()
            }

            val pointerRight = requireContext().getDrawable(org.fossify.commons.R.drawable.ic_chevron_right_vector)
            pointerRight?.isAutoMirrored = true
            setImageDrawable(pointerRight)
            contentDescription = getString(R.string.accessibility_next_day)
        }

        val day = Formatter.getDayTitle(requireContext(), mDayCode)
        topNavigationBinding.topValue.apply {
            text = day
            contentDescription = text
            setOnClickListener {
                (activity as MainActivity).showGoToDateDialog()
            }
            setTextColor(context.getProperTextColor())
            applyThemeFont(ThemeSlot.TEXT)
        }

        setupLongPressToAdd()
    }

    // Long-press on empty space (no event row under the touch) adds an event/task for this day,
    // matching the box week view's empty-space long-press. A RecyclerView swallows a plain
    // OnLongClickListener, so detect it via a GestureDetector and skip when over an event row
    // (those keep their own long-press -> selection behavior).
    private fun setupLongPressToAdd() {
        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if (binding.dayEvents.findChildViewUnder(e.x, e.y) == null) {
                    addEventOrTask()
                }
            }
        })

        binding.dayEvents.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(e)
                return false
            }
        })
    }

    private fun addEventOrTask() {
        val ctx = requireContext()
        if (ctx.config.allowCreatingTasks) {
            val items = arrayListOf(
                RadioItem(TYPE_EVENT, getString(R.string.event)),
                RadioItem(TYPE_TASK, getString(R.string.task))
            )
            RadioGroupDialog(requireActivity(), items) {
                if (it as Int == TYPE_TASK) ctx.launchNewTaskIntent(mDayCode) else ctx.launchNewEventIntent(mDayCode)
            }
        } else {
            ctx.launchNewEventIntent(mDayCode)
        }
    }

    fun updateCalendar() {
        val startTS = Formatter.getDayStartTS(mDayCode)
        val endTS = Formatter.getDayEndTS(mDayCode)
        context?.eventsHelper?.getEvents(startTS, endTS) {
            receivedEvents(it)
        }
    }

    private fun receivedEvents(events: List<Event>) {
        val newHash = events.hashCode()
        if (newHash == lastHash || !isAdded) {
            return
        }
        lastHash = newHash

        val replaceDescription = requireContext().config.replaceDescription
        val sorted = ArrayList(events.sortedWith(compareBy({ !it.getIsAllDay() }, { it.startTS }, { it.endTS }, { it.title }, {
            if (replaceDescription) it.location else it.description
        })))

        activity?.runOnUiThread {
            updateEvents(sorted)
        }
    }

    private fun updateEvents(events: ArrayList<Event>) {
        if (activity == null)
            return

        DayEventsAdapter(activity as SimpleActivity, events, binding.dayEvents, mDayCode) {
            editEvent(it as Event)
        }.apply {
            binding.dayEvents.adapter = this
        }

        if (requireContext().areSystemAnimationsEnabled) {
            binding.dayEvents.scheduleLayoutAnimation()
        }
    }

    private fun editEvent(event: Event) {
        Intent(context, getActivityToOpen(event.isTask())).apply {
            putExtra(EVENT_ID, event.id)
            putExtra(EVENT_OCCURRENCE_TS, event.startTS)
            putExtra(IS_TASK_COMPLETED, event.isTaskCompleted())
            startActivity(this)
        }
    }

    fun printCurrentView() {
        topNavigationBinding.apply {
            topLeftArrow.beGone()
            topRightArrow.beGone()
            topValue.setTextColor(resources.getColor(org.fossify.commons.R.color.theme_light_text_color))
            (binding.dayEvents.adapter as? DayEventsAdapter)?.togglePrintMode()

            Handler().postDelayed({
                requireContext().printBitmap(binding.dayHolder.getViewBitmap())

                Handler().postDelayed({
                    topLeftArrow.beVisible()
                    topRightArrow.beVisible()
                    topValue.setTextColor(requireContext().getProperTextColor())
                    (binding.dayEvents.adapter as? DayEventsAdapter)?.togglePrintMode()
                }, 1000)
            }, 1000)
        }
    }
}
