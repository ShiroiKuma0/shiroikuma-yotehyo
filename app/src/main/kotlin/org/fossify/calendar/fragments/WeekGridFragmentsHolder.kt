package org.fossify.calendar.fragments

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import androidx.viewpager.widget.ViewPager
import org.fossify.calendar.activities.MainActivity
import org.fossify.calendar.adapters.MyWeekGridPagerAdapter
import org.fossify.calendar.databinding.FragmentWeekGridHolderBinding
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.getViewBitmap
import org.fossify.calendar.extensions.printBitmap
import org.fossify.calendar.extensions.seconds
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.WEEKLY_GRID_VIEW
import org.fossify.calendar.helpers.WEEK_START_DATE_TIME
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.WEEK_SECONDS
import org.fossify.commons.views.MyViewPager
import org.joda.time.DateTime

// Hosts a swipeable pager of WeekGridFragment pages (one per week), for the Pimlical-style
// "day box" weekly layout. Used both for the dedicated WEEKLY_GRID_VIEW and for the standard
// WEEKLY_VIEW when its style is set to day boxes.
class WeekGridFragmentsHolder : MyFragmentHolder() {
    private val PREFILLED_WEEKS = 101

    private lateinit var binding: FragmentWeekGridHolderBinding
    private lateinit var viewPager: MyViewPager
    private var defaultPage = 0
    private var thisWeekTS = 0L
    private var currentWeekTS = 0L
    private var isGoToTodayVisible = false

    override val viewType = WEEKLY_GRID_VIEW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dateTimeString = arguments?.getString(WEEK_START_DATE_TIME)
        val baseDateTime = if (dateTimeString != null) DateTime.parse(dateTimeString) else DateTime()
        currentWeekTS = getWeekStart(baseDateTime).seconds()
        thisWeekTS = getWeekStart(DateTime()).seconds()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentWeekGridHolderBinding.inflate(inflater, container, false)
        binding.root.background = ColorDrawable(requireContext().getProperBackgroundColor())
        viewPager = binding.weekGridViewPager
        viewPager.id = (System.currentTimeMillis() % 100000).toInt()
        setupFragment()
        return binding.root
    }

    private fun setupFragment() {
        val weekTSs = getWeekTimestamps(currentWeekTS)
        val adapter = MyWeekGridPagerAdapter(requireActivity().supportFragmentManager, weekTSs)
        defaultPage = weekTSs.size / 2

        viewPager.apply {
            this.adapter = adapter
            addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrollStateChanged(state: Int) {}

                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

                override fun onPageSelected(position: Int) {
                    currentWeekTS = weekTSs[position]
                    val newGoToTodayVisible = shouldGoToTodayBeVisible()
                    if (isGoToTodayVisible != newGoToTodayVisible) {
                        (activity as? MainActivity)?.toggleGoToTodayVisibility(newGoToTodayVisible)
                        isGoToTodayVisible = newGoToTodayVisible
                    }
                }
            })
            currentItem = defaultPage
        }
    }

    private fun getWeekTimestamps(targetSeconds: Long): List<Long> {
        val list = ArrayList<Long>(PREFILLED_WEEKS)
        var week = getWeekStart(Formatter.getDateTimeFromTS(targetSeconds)).minusWeeks(PREFILLED_WEEKS / 2)
        for (i in 0 until PREFILLED_WEEKS) {
            list.add(week.seconds())
            week = week.plusWeeks(1)
        }
        return list
    }

    // First day of the 7-day calendar week containing the given date (respects firstDayOfWeek).
    private fun getWeekStart(dateTime: DateTime): DateTime {
        val firstDayOfWeek = requireContext().config.firstDayOfWeek
        var start = dateTime.withTimeAtStartOfDay()
        val dayOfWeek = start.dayOfWeek
        if (dayOfWeek != firstDayOfWeek) {
            start = if (firstDayOfWeek < dayOfWeek) {
                start.withDayOfWeek(firstDayOfWeek)
            } else {
                start.minusWeeks(1).withDayOfWeek(firstDayOfWeek)
            }
        }
        return start
    }

    override fun goToToday() {
        currentWeekTS = thisWeekTS
        setupFragment()
    }

    override fun showGoToDateDialog() {
        if (activity == null) {
            return
        }

        val datePicker = getDatePickerView()
        val dateTime = getCurrentDate() ?: DateTime()
        datePicker.init(dateTime.year, dateTime.monthOfYear - 1, dateTime.dayOfMonth, null)

        activity?.getAlertDialogBuilder()!!
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> dateSelected(dateTime, datePicker) }
            .apply {
                activity?.setupDialogStuff(datePicker, this)
            }
    }

    private fun dateSelected(dateTime: DateTime, datePicker: DatePicker) {
        val newDateTime = dateTime.withDate(datePicker.year, datePicker.month + 1, datePicker.dayOfMonth)
        currentWeekTS = getWeekStart(newDateTime).seconds()
        setupFragment()
    }

    override fun refreshEvents() {
        (viewPager.adapter as? MyWeekGridPagerAdapter)?.refreshFragments(viewPager.currentItem)
    }

    override fun shouldGoToTodayBeVisible() = currentWeekTS != thisWeekTS

    override fun getNewEventDayCode(): String {
        val currentTS = System.currentTimeMillis() / 1000
        return if (currentTS in currentWeekTS until currentWeekTS + WEEK_SECONDS) {
            Formatter.getTodayCode()
        } else {
            Formatter.getDayCodeFromTS(currentWeekTS)
        }
    }

    override fun printView() {
        Handler().post {
            if (isAdded) {
                requireContext().printBitmap(binding.root.getViewBitmap())
            }
        }
    }

    override fun getCurrentDate(): DateTime? {
        return if (currentWeekTS != 0L) {
            Formatter.getDateTimeFromTS(currentWeekTS)
        } else {
            null
        }
    }
}
