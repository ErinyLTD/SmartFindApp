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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsManager
) : ViewModel() {

    data class UiState(
        val isServiceEnabled: Boolean = false,
        val isAlarmActive: Boolean = false,
        val triggerKeyword: String = "",
        val phoneNumbers: List<PhoneNumber> = emptyList(),
        val lastCheckTimestamp: Long = 0,
        val serviceUnlockCount: Int = 0
    )

    data class PhoneNumber(
        val number: String,
        val name: String?
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refreshState()
    }

    fun refreshState() {
        _uiState.update {
            it.copy(
                isServiceEnabled = settings.isServiceEnabled(),
                isAlarmActive = settings.isAlarmActive(),
                triggerKeyword = settings.getTriggerKeyword(),
                phoneNumbers = settings.getPhoneNumbers().map { number ->
                    PhoneNumber(number, settings.getContactName(number))
                },
                lastCheckTimestamp = settings.getLastCheckedSmsTimestamp(),
                serviceUnlockCount = settings.getServiceUnlockCount()
            )
        }
    }

    fun setServiceEnabled(enabled: Boolean) {
        settings.setServiceEnabled(enabled)
        if (enabled) {
            settings.resetServiceUnlockCount()
        }
        refreshState()
    }

    fun setTriggerKeyword(keyword: String) {
        settings.setTriggerKeyword(keyword)
        refreshState()
    }

    fun addPhoneNumber(number: String, name: String?) {
        settings.addPhoneNumber(number, name)
        
        // Start the 6-month confirmation timer if this is the first number
        if (settings.getNumbersConfirmedTimestamp() == 0L) {
            settings.setNumbersConfirmedTimestamp(System.currentTimeMillis())
        }
        
        refreshState()
    }

    fun removePhoneNumber(number: String) {
        settings.removePhoneNumber(number)
        
        // Disable service if no numbers left
        if (settings.getPhoneNumbers().isEmpty()) {
            settings.setServiceEnabled(false)
        }
        
        refreshState()
    }
    
    fun setAlarmActive(active: Boolean) {
        settings.setAlarmActive(active)
        refreshState()
    }
    
    fun updateLastCheckTime() {
         _uiState.update {
            it.copy(lastCheckTimestamp = settings.getLastCheckedSmsTimestamp())
        }
    }

    fun confirmNumbers() {
        settings.setNumbersConfirmedTimestamp(System.currentTimeMillis())
    }
    
    fun isNumbersConfirmationDue(): Boolean {
        return settings.isNumbersConfirmationDue()
    }

    fun isFirstRunComplete(): Boolean {
        return settings.isFirstRunComplete()
    }
}
