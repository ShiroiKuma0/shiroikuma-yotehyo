package org.fossify.calendar.activities

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import org.fossify.calendar.R
import org.fossify.calendar.adapters.SelectTimeZoneAdapter
import org.fossify.calendar.adapters.ZonePickerItem
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.zoneCode
import org.fossify.calendar.extensions.zoneSearchNames
import org.fossify.calendar.helpers.AIRPORTS
import org.fossify.calendar.databinding.ActivitySelectTimeZoneBinding
import org.fossify.calendar.helpers.CURRENT_TIME_ZONE
import org.fossify.calendar.helpers.TIME_ZONE
import org.fossify.calendar.helpers.getAllTimeZones
import org.fossify.calendar.models.MyTimeZone
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import java.util.Locale
import java.util.TimeZone

class SelectTimeZoneActivity : SimpleActivity() {
    private var mSearchMenuItem: MenuItem? = null
    private val allTimeZones = getAllTimeZones()

    private val binding by viewBinding(ActivitySelectTimeZoneBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()

        val initialItems = defaultItems()
        SelectTimeZoneAdapter(this, initialItems, getString(R.string.time_zone_recent)) {
            hideKeyboard()
            val zone = it as MyTimeZone
            config.addRecentTimeZone(zone.zoneName)
            val data = Intent()
            data.putExtra(TIME_ZONE, zone)
            setResult(RESULT_OK, data)
            finish()
        }.apply {
            binding.selectTimeZoneList.adapter = this
        }

        // with recent zones on top the user's own picks are right there; otherwise jump to the current zone
        val currentTimeZone = intent.getStringExtra(CURRENT_TIME_ZONE) ?: TimeZone.getDefault().id
        if (config.recentTimeZones.isEmpty()) {
            val pos = initialItems.indexOfFirst { it.zone.zoneName.equals(currentTimeZone, true) }
            if (pos != -1) {
                binding.selectTimeZoneList.scrollToPosition(pos)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.selectTimeZoneAppBarLayout, NavigationIcon.Arrow, searchMenuItem = mSearchMenuItem)
    }

    private fun setupOptionsMenu() {
        setupSearch(binding.selectTimeZoneToolbar.menu)
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        mSearchMenuItem = menu.findItem(R.id.search)
        (mSearchMenuItem!!.actionView as SearchView).apply {
            queryHint = getString(R.string.time_zone_search_hint)
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            isIconified = false
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    searchQueryChanged(newText)
                    return true
                }
            })
        }

        mSearchMenuItem!!.expandActionView()
        MenuItemCompat.setOnActionExpandListener(mSearchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                searchQueryChanged("")
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                hideKeyboard()
                finish()
                return true
            }
        })
    }

    // Recent picks first (marked), then every zone.
    private fun defaultItems(): ArrayList<ZonePickerItem> {
        val byId = allTimeZones.associateBy { it.zoneName }
        val recent = config.recentTimeZones.mapNotNull { byId[it] }.map { ZonePickerItem(it, isRecent = true) }
        return ArrayList(recent + allTimeZones.map { ZonePickerItem(it) })
    }

    // Each zone's searchable text: its id (spaces for underscores), its city names in English, the
    // device language and Japanese, its current code (CET, TRT…) and GMT offset. Built on first search.
    private val searchIndex: List<Pair<MyTimeZone, String>> by lazy {
        val now = System.currentTimeMillis()
        allTimeZones.map { zone ->
            val names = zoneSearchNames(zone.zoneName)
            val code = zoneCode(zone.zoneName, now)
            zone to (listOf(zone.zoneName, zone.zoneName.replace('_', ' '), code, zone.title) + names)
                .joinToString("\n").lowercase(Locale.getDefault())
        }
    }

    private fun searchQueryChanged(text: String) {
        val query = text.trim().lowercase(Locale.getDefault())
        val adapter = binding.selectTimeZoneList.adapter as? SelectTimeZoneAdapter ?: return
        if (query.isEmpty()) {
            adapter.updateItems(defaultItems())
            return
        }

        val byId = allTimeZones.associateBy { it.zoneName }
        // a three-letter airport code (PRG, IST, NRT) leads with that airport's zone
        val airportItems = if (query.length == 3) {
            AIRPORTS[query.uppercase(Locale.ROOT)]?.let { (city, zoneId) ->
                byId[zoneId]?.let { listOf(ZonePickerItem(it, query.uppercase(Locale.ROOT), city)) }
            } ?: emptyList()
        } else {
            emptyList()
        }
        // airports whose city matches (e.g. "tenerife" → TFS, TFN) when it is not a zone city itself
        val airportCityItems = if (query.length >= 3) {
            AIRPORTS.filter { (_, value) -> value.first.lowercase(Locale.getDefault()).contains(query) }
                .mapNotNull { (code, value) -> byId[value.second]?.let { ZonePickerItem(it, code, value.first) } }
                .filter { item -> airportItems.none { it.airportCode == item.airportCode } }
        } else {
            emptyList()
        }
        val zoneItems = searchIndex.filter { it.second.contains(query) }.map { ZonePickerItem(it.first) }
        adapter.updateItems(ArrayList(airportItems + zoneItems + airportCityItems))
    }
}
