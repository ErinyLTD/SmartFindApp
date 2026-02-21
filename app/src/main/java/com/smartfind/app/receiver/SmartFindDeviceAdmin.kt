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

package com.smartfind.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Device Admin receiver that prevents SmartFind from being silently uninstalled.
 *
 * When enabled as a device administrator, the system will warn the user before
 * they can uninstall the app — they must first disable the device admin.
 * This prevents a malicious person from simply uninstalling SmartFind to
 * avoid being located.
 *
 * The user is prompted to enable device admin during first-run onboarding.
 * This is optional — SmartFind works without it, but with reduced protection.
 *
 * Note: This does NOT prevent force-stop, which is an Android system limitation.
 * The SmsCheckWorker (WorkManager) will resume after force-stop when the system
 * restarts background work.
 */
class SmartFindDeviceAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(
            context,
            "SmartFind uninstall protection enabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(
            context,
            "SmartFind uninstall protection disabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling device admin will remove SmartFind's uninstall protection. " +
            "Anyone with access to this phone will be able to uninstall SmartFind."
    }
}
