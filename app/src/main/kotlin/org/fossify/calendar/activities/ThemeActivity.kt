package org.fossify.calendar.activities

import android.os.Bundle
import android.widget.ImageView
import org.fossify.calendar.databinding.ActivityThemeBinding
import org.fossify.calendar.databinding.ItemThemeColorBinding
import org.fossify.calendar.databinding.ItemThemeSectionBinding
import org.fossify.calendar.extensions.ThemeGroup
import org.fossify.calendar.extensions.ThemeSlot
import org.fossify.calendar.extensions.resetThemeColor
import org.fossify.calendar.extensions.setThemeColor
import org.fossify.calendar.extensions.themeColor
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon

class ThemeActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThemeBinding::inflate)
    private val previews = HashMap<ThemeSlot, ImageView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.themeNestedScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.themeNestedScrollview,
            topAppBar = binding.themeAppbar
        )
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(topAppBar = binding.themeAppbar, navigationIcon = NavigationIcon.Arrow)
        buildRows()
    }

    private fun buildRows() {
        binding.themeHolder.removeAllViews()
        previews.clear()

        val textColor = getProperTextColor()
        val primaryColor = getProperPrimaryColor()

        ThemeGroup.entries.forEach { group ->
            val section = ItemThemeSectionBinding.inflate(layoutInflater, binding.themeHolder, false)
            section.themeSectionLabel.text = getString(group.labelRes)
            section.themeSectionLabel.setTextColor(primaryColor)
            binding.themeHolder.addView(section.root)

            ThemeSlot.entries.filter { it.group == group }.forEach { slot ->
                val row = ItemThemeColorBinding.inflate(layoutInflater, binding.themeHolder, false)
                row.themeColorLabel.text = getString(slot.labelRes)
                row.themeColorLabel.setTextColor(textColor)
                row.themeColorPreview.background.setTint(themeColor(slot))
                row.root.setOnClickListener { openPicker(slot) }
                previews[slot] = row.themeColorPreview
                binding.themeHolder.addView(row.root)
            }
        }
    }

    private fun openPicker(slot: ThemeSlot) {
        ColorPickerDialog(this, themeColor(slot), addDefaultColorButton = true) { wasPositive, color ->
            if (wasPositive) {
                setThemeColor(slot, color)
            } else {
                resetThemeColor(slot)
            }

            if (slot.isFoundation) {
                // foundation cascades into the chrome + every inheriting preview
                recreate()
            } else {
                previews[slot]?.background?.setTint(themeColor(slot))
            }
        }
    }
}
