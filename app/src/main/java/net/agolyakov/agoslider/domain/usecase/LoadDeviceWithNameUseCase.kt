package net.agolyakov.agoslider.domain.usecase

import net.agolyakov.agoslider.data.model.ble.AgoSliderDevice
import net.agolyakov.agoslider.domain.repository.PreferencesRepository
import javax.inject.Inject

class LoadDeviceWithNameUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(device: AgoSliderDevice): AgoSliderDevice {
        val friendlyName = preferencesRepository.getFriendlyName(device.macAddress)
        return device.copy(friendlyName = friendlyName)
    }
}