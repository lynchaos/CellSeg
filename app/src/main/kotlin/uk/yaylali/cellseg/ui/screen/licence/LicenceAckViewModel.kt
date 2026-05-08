package uk.yaylali.cellseg.ui.screen.licence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import uk.yaylali.cellseg.data.datastore.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class LicenceAckViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    fun acknowledge() {
        viewModelScope.launch {
            settingsRepository.acknowledgeLicence()
            settingsRepository.completeOnboarding()
        }
    }
}
