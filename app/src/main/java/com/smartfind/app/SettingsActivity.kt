/*
 * SmartFind - Find your phone with an SMS from a trusted contact
 * Copyright (C) 2026 ErinyLTD
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.smartfind.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.slider.Slider
import com.smartfind.app.data.AuditEvent
import com.smartfind.app.data.EventLogger
import com.smartfind.app.util.AuthHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings = SettingsManager(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.settingsToolbar)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { finish() }

        setupCooldownSlider()
    }

    private fun setupCooldownSlider() {
        val slider = findViewById<Slider>(R.id.cooldownSlider)
        val valueText = findViewById<TextView>(R.id.cooldownValue)

        val currentMin = settings.getCooldownMinutes()
        slider.value = currentMin.toFloat()
        valueText.text = formatCooldown(currentMin)

        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            private var previousValue = currentMin

            override fun onStartTrackingTouch(slider: Slider) {
                previousValue = slider.value.toInt()
            }

            override fun onStopTrackingTouch(slider: Slider) {
                val minutes = slider.value.toInt()
                if (minutes != previousValue) {
                    AuthHelper.authenticate(
                        activity = this@SettingsActivity,
                        title = "Change Setting",
                        subtitle = "Authenticate to change cooldown period",
                        onSuccess = {
                            settings.setCooldownMinutes(minutes)
                            valueText.text = formatCooldown(minutes)
                            EventLogger.logSettingChanged(
                                this@SettingsActivity, "cooldown_minutes", minutes.toString()
                            )
                        },
                        onFailure = {
                            // Revert slider to previous value
                            slider.value = previousValue.toFloat()
                            valueText.text = formatCooldown(previousValue)
                            EventLogger.log(
                                this@SettingsActivity, AuditEvent.TYPE_AUTH_FAILED, "cooldown_minutes"
                            )
                        }
                    )
                }
            }
        })

        // Update display text as user drags (visual feedback only, not persisted)
        slider.addOnChangeListener { _, value, _ ->
            valueText.text = formatCooldown(value.toInt())
        }
    }

    private fun formatCooldown(minutes: Int): String {
        return "$minutes minutes"
    }
}
