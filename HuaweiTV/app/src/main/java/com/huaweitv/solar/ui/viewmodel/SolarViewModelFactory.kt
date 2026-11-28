package com.huaweitv.solar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.huaweitv.solar.data.coffee.CoffeePromptManager
import com.huaweitv.solar.data.repository.SettingsRepository
import com.huaweitv.solar.data.repository.SolarRepository

class SolarViewModelFactory(
    private val solarRepository: SolarRepository,
    private val settingsRepository: SettingsRepository,
    private val coffeePromptManager: CoffeePromptManager? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SolarViewModel::class.java)) {
            return SolarViewModel(solarRepository, settingsRepository, coffeePromptManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
