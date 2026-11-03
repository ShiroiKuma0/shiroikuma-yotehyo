package org.fossify.calendar.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.databinding.ItemSelectTimeZoneBinding
import org.fossify.calendar.extensions.ThemeSlot
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.themeColor
import org.fossify.calendar.extensions.zoneDescription
import org.fossify.calendar.helpers.EVENT_TIME_FORMAT_JAPANESE
import org.fossify.calendar.helpers.getNowSeconds
import org.fossify.calendar.models.MyTimeZone
import org.fossify.commons.extensions.getProperTextColor

/** A picker row: a zone, reached directly, through an airport code, or from the recent list. */
class ZonePickerItem(val zone: MyTimeZone, val airportCode: String? = null, val airportCity: String? = null, val isRecent: Boolean = false)

class SelectTimeZoneAdapter(
    val activity: SimpleActivity,
    var items: ArrayList<ZonePickerItem>,
    private val recentLabel: String,
    val itemClick: (Any) -> Unit,
) : RecyclerView.Adapter<SelectTimeZoneAdapter.TimeZoneViewHolder>() {
    val textColor = activity.getProperTextColor()
    private val detailColor = activity.themeColor(ThemeSlot.TIME_ZONE_TEXT)
    private val tagColor = activity.themeColor(ThemeSlot.TIME_ZONE_TAG)
    private val japanese = activity.config.eventTimeFormat == EVENT_TIME_FORMAT_JAPANESE
    private val now = getNowSeconds()
    private val descriptions = HashMap<String, String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeZoneViewHolder {
        return TimeZoneViewHolder(
            binding = ItemSelectTimeZoneBinding.inflate(activity.layoutInflater, parent, false)
        )
    }

    override fun onBindViewHolder(holder: TimeZoneViewHolder, position: Int) {
        holder.bindView(items[position])
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: ArrayList<ZonePickerItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class TimeZoneViewHolder(val binding: ItemSelectTimeZoneBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindView(item: ZonePickerItem) {
            val zoneName = item.zone.zoneName
            // プラハ · CET · UTC+1 — the offset as of now, so DST shows as it currently stands
            val description = descriptions.getOrPut(zoneName) { zoneDescription(zoneName, now, japanese) }
            binding.apply {
                if (item.airportCode != null) {
                    itemTimeZoneTitle.text = "✈ ${item.airportCode}  ${item.airportCity}"
                    itemTimeZoneTitle.setTextColor(tagColor)
                    itemTimeZoneShift.text = "$zoneName · $description"
                } else {
                    itemTimeZoneTitle.text = zoneName
                    itemTimeZoneTitle.setTextColor(if (item.isRecent) tagColor else textColor)
                    itemTimeZoneShift.text = if (item.isRecent) "$recentLabel · $description" else description
                }
                itemTimeZoneShift.setTextColor(detailColor)

                itemSelectTimeZoneHolder.setOnClickListener {
                    itemClick(item.zone)
                }
            }
        }
    }
}
