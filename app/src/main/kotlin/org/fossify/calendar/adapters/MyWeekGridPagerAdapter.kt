package org.fossify.calendar.adapters

import android.os.Bundle
import android.util.SparseArray
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import org.fossify.calendar.fragments.WeekGridFragment
import org.fossify.calendar.helpers.WEEK_START_TIMESTAMP

class MyWeekGridPagerAdapter(fm: FragmentManager, private val weekTimestamps: List<Long>) :
    FragmentStatePagerAdapter(fm) {
    private val fragments = SparseArray<WeekGridFragment>()

    override fun getCount() = weekTimestamps.size

    override fun getItem(position: Int): Fragment {
        val bundle = Bundle()
        bundle.putLong(WEEK_START_TIMESTAMP, weekTimestamps[position])
        return WeekGridFragment().apply { arguments = bundle }
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val item = super.instantiateItem(container, position)
        if (item is WeekGridFragment) {
            fragments.put(position, item)
        }
        return item
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        fragments.remove(position)
        super.destroyItem(container, position, `object`)
    }

    fun refreshFragments(pos: Int) {
        for (i in -1..1) {
            fragments[pos + i]?.refreshEvents()
        }
    }
}
