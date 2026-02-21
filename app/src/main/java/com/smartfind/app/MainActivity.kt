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

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.smartfind.app.data.AuditEvent
import com.smartfind.app.data.EventLogger
import com.smartfind.app.receiver.BatterySaverMonitor
import com.smartfind.app.service.AlarmService
import com.smartfind.app.util.AuthHelper
import com.smartfind.app.util.DeviceStateHelper
import com.smartfind.app.util.PhoneNumberHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var statusText: TextView
    private lateinit var serviceToggle: SwitchMaterial
    private lateinit var keywordInput: EditText
    private lateinit var numbersContainer: LinearLayout
    private lateinit var addNumberButton: MaterialButton
    private lateinit var stopAlarmButton: MaterialButton
    private lateinit var testAlarmButton: MaterialButton
    private lateinit var lastCheckText: TextView
    private lateinit var alarmActiveIndicator: View

    private val alarmStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateAlarmUI()
        }
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
            val allGranted = permissions.all { it.value }
        if (!allGranted) {
            Toast.makeText(
                this,
                getString(R.string.permission_denied_warning),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { contactUri ->
        if (contactUri != null) {
            handleContactPicked(contactUri)
        }
    }

    private fun resolveContactName(contactUri: android.net.Uri): String? {
        contentResolver.query(
            contactUri,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                )
            }
        }
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // SettingsManager is injected by Hilt
        if (!viewModel.isFirstRunComplete()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Clear stale alarm flag if the service isn't actually running.
        // This can happen if the app was killed by the OS while the alarm
        // was playing — onDestroy/stopAlarm never ran, leaving the flag true.
        if (viewModel.uiState.value.isAlarmActive && !isAlarmServiceRunning()) {
            Log.d(TAG, getString(R.string.stale_alarm_log))
            viewModel.setAlarmActive(false)
        }

        // Check for SIM / telephony capability
        when (checkSimAvailability()) {
            SimStatus.NO_TELEPHONY_HARDWARE -> {
                // Wi-Fi-only tablet — let user explore the app with a warning
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.no_cellular_title))
                    .setMessage(getString(R.string.no_cellular_msg))
                    .setPositiveButton(getString(R.string.ok), null)
                    .show()
            }
            SimStatus.NO_SIM -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.no_sim_title))
                    .setMessage(getString(R.string.no_sim_msg))
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.close)) { _, _ -> finishAffinity() }
                    .show()
                return
            }
            SimStatus.OK -> { /* continue normal startup */ }
        }

        // Show over lock screen when alarm is active
        if (viewModel.uiState.value.isAlarmActive) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        initViews()
        observeState()
        requestPermissions()

        registerReceiver(
            alarmStateReceiver,
            IntentFilter(AlarmService.ACTION_ALARM_STATE_CHANGED),
            RECEIVER_NOT_EXPORTED
        )

        // Defer system-intent and dialog-showing work until after the first
        // frame renders and the window has focus. Launching a system activity
        // (battery exemption) or showing dialogs during onCreate delays the
        // window focus, which can trigger an ANR ("Application does not have
        // a focused window") on slower devices.
        window.decorView.post {
            requestBatteryOptimizationExemption()
            checkNumbersConfirmation()

            // Security: warn if device has no lock screen configured
            if (!AuthHelper.isDeviceSecure(this)) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.security_warning_title))
                    .setMessage(getString(R.string.security_warning_msg))
                    .setPositiveButton(getString(R.string.ok), null)
                    .show()
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(alarmStateReceiver)
        } catch (_: IllegalArgumentException) { }
        super.onDestroy()
    }

    private fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_audit_log -> {
                startActivity(Intent(this, AuditLogActivity::class.java))
                true
            }
            R.id.menu_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        updateAlarmUI()
    }

    override fun onResume() {
        super.onResume()
        updateAlarmUI()
        updateLastCheckTime()
    }

    private fun initViews() {
        val toolbar = findViewById<MaterialToolbar>(R.id.mainToolbar)
        toolbar.setOnMenuItemClickListener { item ->
            onMenuItemSelected(item)
        }

        statusText = findViewById(R.id.statusText)
        serviceToggle = findViewById(R.id.serviceToggle)
        keywordInput = findViewById(R.id.keywordInput)
        numbersContainer = findViewById(R.id.numbersContainer)
        addNumberButton = findViewById(R.id.addNumberButton)
        stopAlarmButton = findViewById(R.id.stopAlarmButton)
        testAlarmButton = findViewById(R.id.testAlarmButton)
        lastCheckText = findViewById(R.id.lastCheckText)
        alarmActiveIndicator = findViewById(R.id.alarmActiveIndicator)

        // Service toggle listener is set up in loadSettings() via setupServiceToggleListener()

        // Keyword input — require auth before persisting changes
        keywordInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = keywordInput.text?.toString()?.trim() ?: ""
                val current = viewModel.uiState.value.triggerKeyword
                if (text.length >= 3 && text.uppercase() != current) {
                    AuthHelper.authenticate(
                        activity = this,
                        title = getString(R.string.auth_change_keyword_title),
                        subtitle = getString(R.string.auth_change_keyword_subtitle),
                        onSuccess = {
                            viewModel.setTriggerKeyword(text)
                            EventLogger.logKeywordChanged(this)
                        },
                        onFailure = {
                            keywordInput.setText(current)
                            EventLogger.log(this, AuditEvent.TYPE_AUTH_FAILED, "keyword_change")
                        }
                    )
                }
            }
        }

        // Add number button — require auth to prevent unauthorized contact addition
        addNumberButton.setOnClickListener {
            AuthHelper.authenticate(
                activity = this,
                title = getString(R.string.auth_add_contact_title),
                subtitle = getString(R.string.auth_add_contact_subtitle),
                onSuccess = {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        contactPickerLauncher.launch(null)
                    } else {
                        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                },
                onFailure = {
                    EventLogger.log(this, AuditEvent.TYPE_AUTH_FAILED, "add_contact")
                }
            )
        }

        // Stop alarm button
        stopAlarmButton.setOnClickListener {
            val intent = Intent(this, AlarmService::class.java).apply {
                action = AlarmService.ACTION_STOP_ALARM
            }
            startService(intent)
        }

        // Test alarm button — no auth required (benign action; the confirmation
        // dialog provides sufficient intent verification)
        testAlarmButton.setOnClickListener {
            // Safety checks (cooldown and rate limit are skipped — this is
            // a deliberate user action)
            if (DeviceStateHelper.isInCarMode(this)) {
                Toast.makeText(this, getString(R.string.auto_suppressed), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (DeviceStateHelper.isInPhoneCall(this)) {
                Toast.makeText(this, getString(R.string.call_suppressed), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_test_alarm, null)
            val dialog = MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setBackground(ContextCompat.getDrawable(this, R.drawable.bg_dialog))
                .create()

            dialogView.findViewById<MaterialButton>(R.id.dialogStartButton).setOnClickListener {
                dialog.dismiss()
                val intent = Intent(this, AlarmService::class.java).apply {
                    putExtra(AlarmService.EXTRA_IS_TEST, true)
                }
                startForegroundService(intent)
            }
            dialogView.findViewById<MaterialButton>(R.id.dialogCancelButton).setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        }

    }

    /** Sets up the service toggle listener (used to re-attach after auth revert). */
    private fun setupServiceToggleListener() {
        serviceToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && viewModel.uiState.value.phoneNumbers.isEmpty()) {
                serviceToggle.isChecked = false
                Toast.makeText(
                    this, getString(R.string.add_number_first), Toast.LENGTH_SHORT
                ).show()
                return@setOnCheckedChangeListener
            }
            if (isChecked && keywordInput.text.toString().trim().length < 3) {
                serviceToggle.isChecked = false
                Toast.makeText(
                    this, getString(R.string.keyword_too_short), Toast.LENGTH_SHORT
                ).show()
                return@setOnCheckedChangeListener
            }
            val applyToggle = {
                viewModel.setServiceEnabled(isChecked)
                updateStatusText(isChecked)
                SmartFindApplication.updateServiceActiveNotification(this)
                BatterySaverMonitor.syncReceiverState(this)
                EventLogger.logServiceToggled(this, isChecked)
            }

            if (isChecked) {
                AuthHelper.authenticate(
                    activity = this,
                    title = getString(R.string.auth_enable_service_title),
                    subtitle = getString(R.string.auth_enable_service_subtitle),
                    onSuccess = { applyToggle() },
                    onFailure = {
                        serviceToggle.setOnCheckedChangeListener(null)
                        serviceToggle.isChecked = false
                        setupServiceToggleListener()
                        EventLogger.log(this, AuditEvent.TYPE_AUTH_FAILED, "service_enable")
                    }
                )
            } else {
                applyToggle()
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                // Update UI based on state
                keywordInput.setText(state.triggerKeyword)
                
                serviceToggle.setOnCheckedChangeListener(null)
                serviceToggle.isChecked = state.isServiceEnabled
                setupServiceToggleListener()
                updateStatusText(state.isServiceEnabled)
                
                stopAlarmButton.visibility = if (state.isAlarmActive) View.VISIBLE else View.GONE
                alarmActiveIndicator.visibility = if (state.isAlarmActive) View.VISIBLE else View.GONE
                setShowWhenLocked(state.isAlarmActive)
                setTurnScreenOn(state.isAlarmActive)
                
                if (state.lastCheckTimestamp > 0) {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    lastCheckText.text = getString(R.string.last_check_label, sdf.format(Date(state.lastCheckTimestamp)))
                } else {
                    lastCheckText.text = getString(R.string.last_check_default)
                }

                refreshNumbersList()
            }
        }
    }

    private fun updateStatusText(enabled: Boolean) {
        if (enabled) {
            statusText.text = getString(R.string.status_active)
            statusText.setTextColor(ContextCompat.getColor(this, R.color.status_active))
        } else {
            statusText.text = getString(R.string.status_inactive)
            statusText.setTextColor(ContextCompat.getColor(this, R.color.status_inactive))
        }
    }

    private fun updateAlarmUI() {
        // Handled by observeState
        viewModel.refreshState()
    }

    private fun updateLastCheckTime() {
        // Handled by observeState
        viewModel.updateLastCheckTime()
    }

    private fun refreshNumbersList() {
        numbersContainer.removeAllViews()
        val numbers = viewModel.uiState.value.phoneNumbers

        for ((number, contactName) in numbers) {
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_phone_number, numbersContainer, false)

            val nameText = itemView.findViewById<TextView>(R.id.contactNameText)
            val numberText = itemView.findViewById<TextView>(R.id.phoneNumberText)
            val removeButton = itemView.findViewById<ImageButton>(R.id.removeNumberButton)

            nameText.text = contactName ?: getString(R.string.unknown_contact)
            numberText.text = number

            // Require auth to prevent unauthorized contact removal
            removeButton.setOnClickListener {
                val displayName = contactName ?: number
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.remove_contact_title))
                    .setMessage(getString(R.string.remove_contact_msg, displayName, number))
                    .setPositiveButton(getString(R.string.remove_button)) { _, _ ->
                        AuthHelper.authenticate(
                            activity = this,
                            title = getString(R.string.remove_contact_title),
                            subtitle = getString(R.string.auth_remove_contact_subtitle, displayName),
                            onSuccess = {
                                viewModel.removePhoneNumber(number)
                                EventLogger.logContactRemoved(this, number)
                                // UI update via observation
                            },
                            onFailure = {
                                EventLogger.log(this, AuditEvent.TYPE_AUTH_FAILED, "remove_contact")
                            }
                        )
                    }
                    .setNegativeButton(getString(R.string.cancel_button), null)
                    .show()
            }

            numbersContainer.addView(itemView)
        }
    }

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            contactPickerLauncher.launch(null)
        } else {
            Toast.makeText(
                this,
                getString(R.string.contacts_permission_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun handleContactPicked(contactUri: android.net.Uri) {
        val contactName = resolveContactName(contactUri) ?: getString(R.string.unknown_contact)

        // Resolve the actual CONTACT_ID from the picked URI
        var resolvedContactId: String? = null
        contentResolver.query(
            contactUri,
            arrayOf(ContactsContract.Contacts._ID),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                resolvedContactId = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                )
            }
        }

        val contactId = resolvedContactId ?: return
        val phoneCursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL
            ),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )

        val numbers = mutableListOf<Pair<String, String>>() // (number, label)
        phoneCursor?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                ) ?: continue
                val type = cursor.getInt(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
                )
                val customLabel = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
                )
                val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    resources, type, customLabel
                ).toString()
                numbers.add(number to label)
            }
        }

        when {
            numbers.isEmpty() -> {
                Toast.makeText(this, getString(R.string.contact_no_numbers), Toast.LENGTH_SHORT).show()
            }
            numbers.size == 1 -> {
                val normalized = PhoneNumberHelper.normalizeNumber(this, numbers.first().first)
                addDesignatedNumber(normalized, contactName)
            }
            else -> {
                // Multiple numbers — let user choose
                val items = numbers.map { (number, label) -> "$number ($label)" }.toTypedArray()
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.choose_number_title, contactName))
                    .setItems(items) { _, which ->
                        val normalized = PhoneNumberHelper.normalizeNumber(this, numbers[which].first)
                        addDesignatedNumber(normalized, contactName)
                    }
                    .setNegativeButton(getString(R.string.cancel_button), null)
                    .show()
            }
        }
    }

    private fun addDesignatedNumber(number: String, contactName: String? = null) {
        viewModel.addPhoneNumber(number, contactName)
        EventLogger.logContactAdded(this, number)
        SmartFindApplication.scheduleNumbersReminder(this)
        
        val displayName = contactName ?: number
        Toast.makeText(this, getString(R.string.added_contact_toast, displayName), Toast.LENGTH_SHORT).show()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_CONTACTS
        )

        // Only request telephony permissions if the device has telephony hardware
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            permissions.add(Manifest.permission.RECEIVE_SMS)
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            permissionsLauncher.launch(notGranted.toTypedArray())
        }
    }

    /**
     * Prompts the user to exempt SmartFind from battery optimization.
     * This is critical for receiving SMS broadcasts promptly in Doze mode.
     * "Find my phone" is an explicitly approved use case for this permission.
     */
    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(android.os.PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:$packageName")
            )
            try {
                startActivity(intent)
            } catch (_: Exception) {
                // Some OEMs don't support this intent
            }
        }
    }

    private fun checkNumbersConfirmation() {
        if (!viewModel.isNumbersConfirmationDue()) return
        val numbers = viewModel.uiState.value.phoneNumbers
        if (numbers.isEmpty()) return

        val contactsList = numbers.joinToString("\n") { (number, name) ->
            if (name != null) "$name ($number)" else number
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.confirm_contacts_title))
            .setMessage(
                getString(R.string.confirm_contacts_msg, contactsList)
            )
            .setCancelable(false)
            .setPositiveButton(getString(R.string.confirm_button)) { _, _ ->
                viewModel.confirmNumbers()
                SmartFindApplication.scheduleNumbersReminder(this)
                Toast.makeText(this, getString(R.string.numbers_confirmed_toast), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.edit_numbers_button)) { _, _ ->
                viewModel.confirmNumbers()
                SmartFindApplication.scheduleNumbersReminder(this)
                // User will edit numbers in the main UI
            }
            .show()
    }

    private enum class SimStatus { OK, NO_SIM, NO_TELEPHONY_HARDWARE }

    /**
     * Distinguishes between "no telephony hardware" (Wi-Fi-only tablets /
     * emulators) and "telephony present but SIM absent".  The former should
     * receive a soft warning; the latter is a hard blocker since the user
     * can insert a SIM.
     */
    private fun checkSimAvailability(): SimStatus {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return SimStatus.NO_TELEPHONY_HARDWARE
        }
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return SimStatus.NO_SIM
        return if (tm.simState == TelephonyManager.SIM_STATE_ABSENT) SimStatus.NO_SIM else SimStatus.OK
    }

    /**
     * Checks whether [AlarmService] is actually running in this process.
     * Used to detect stale [SettingsManager.isAlarmActive] flags after
     * the app was killed while the alarm was playing.
     *
     * Uses [AlarmService.isRunning] (an in-memory flag) instead of the
     * deprecated [android.app.ActivityManager.getRunningServices] which
     * scans all running services and can be slow on some devices.
     */
    private fun isAlarmServiceRunning(): Boolean = AlarmService.isRunning

    companion object {
        private const val TAG = "MainActivity"
    }
}
