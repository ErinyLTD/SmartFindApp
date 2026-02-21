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

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.smartfind.app.data.AuditEvent
import com.smartfind.app.data.EventLogger
import com.smartfind.app.receiver.SmartFindDeviceAdmin
import com.smartfind.app.util.AuthHelper

/**
 * First-run onboarding screen shown once when the app is launched for the first time.
 * Explains what SmartFind does, its limitations, and offers to enable device admin
 * for uninstall protection.
 *
 * S2: Both "Enable Protection" and "Get Started" require device-credential
 * authentication when a lock screen is configured. This prevents an abuser
 * from silently setting up the app on a victim's unlocked phone without
 * re-entering the PIN/pattern/password.
 *
 * After the user completes onboarding, the flag is stored in SettingsManager
 * and they're redirected to MainActivity.
 */
class OnboardingActivity : AppCompatActivity() {

    companion object {
        private const val DEVICE_ADMIN_REQUEST_CODE = 5001
    }

    /** Single instance for the activity lifetime.  Avoids creating a new
     *  [SettingsManager] in [completeOnboarding], which could resolve to a
     *  different SharedPreferences backend (encrypted vs. plain fallback)
     *  than the one [MainActivity] used to check [SettingsManager.isFirstRunComplete]. */
    private lateinit var settings: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsManager(this)
        setContentView(R.layout.activity_onboarding)

        val continueButton = findViewById<MaterialButton>(R.id.onboardingContinueButton)
        val enableProtectionButton = findViewById<MaterialButton>(R.id.enableProtectionButton)

        // Request device admin for uninstall protection
        // S2: Require auth before enrolling device admin
        enableProtectionButton.setOnClickListener {
            AuthHelper.authenticate(
                activity = this,
                title = "Enable Protection",
                subtitle = "Authenticate to enable uninstall protection",
                onSuccess = {
                    EventLogger.log(this, "AUTH_SUCCESS", "onboarding_device_admin")
                    val componentName = ComponentName(this, SmartFindDeviceAdmin::class.java)
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "SmartFind uses device admin to prevent unauthorized uninstallation. " +
                                "This ensures the app cannot be silently removed."
                        )
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, DEVICE_ADMIN_REQUEST_CODE)
                },
                onFailure = {
                    EventLogger.log(this, AuditEvent.TYPE_AUTH_FAILED, "onboarding_device_admin")
                }
            )
        }

        // S2: Require auth before completing onboarding
        continueButton.setOnClickListener {
            AuthHelper.authenticate(
                activity = this,
                title = "Complete Setup",
                subtitle = "Authenticate to finish SmartFind setup",
                onSuccess = {
                    EventLogger.log(this, AuditEvent.TYPE_AUTH_SUCCESS, "onboarding_complete")
                    completeOnboarding()
                },
                onFailure = {
                    EventLogger.log(this, AuditEvent.TYPE_AUTH_FAILED, "onboarding_complete")
                }
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DEVICE_ADMIN_REQUEST_CODE) {
            // Whether they accepted or not, they can continue
            // The button text could update to show status, but keeping it simple
        }
    }

    private fun completeOnboarding() {
        settings.setFirstRunComplete(true)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
