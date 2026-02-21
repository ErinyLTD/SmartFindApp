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

package com.smartfind.app.util

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Helper for BiometricPrompt authentication with device credential fallback.
 * Used to gate sensitive configuration changes:
 * - Adding/removing contacts
 * - Toggling the service
 * - Changing the trigger keyword
 * - Changing safety settings
 */
object AuthHelper {

    /**
     * Returns true if the device has a secure lock screen (PIN/pattern/password) configured.
     * Used to warn users when authentication protection is ineffective.
     */
    fun isDeviceSecure(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceSecure == true
    }

    /**
     * Returns true if the device supports biometric or device credential authentication.
     */
    fun canAuthenticate(activity: FragmentActivity): Boolean {
        val biometricManager = BiometricManager.from(activity)
        val result = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Shows the BiometricPrompt with device credential (PIN/pattern/password) fallback.
     *
     * @param activity The FragmentActivity hosting the prompt
     * @param title Title shown in the biometric dialog
     * @param subtitle Subtitle shown in the biometric dialog
     * @param onSuccess Called when authentication succeeds
     * @param onFailure Called when authentication fails or is cancelled
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Authentication Required",
        subtitle: String = "Confirm your identity to change SmartFind settings",
        onSuccess: () -> Unit,
        onFailure: () -> Unit = {}
    ) {
        if (!canAuthenticate(activity)) {
            // No authentication available — allow the action
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onFailure()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Don't call onFailure here — BiometricPrompt will retry
                // onFailure is called from onAuthenticationError when user cancels
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
