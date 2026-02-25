package app.shosetsu.android.viewmodel.impl

import androidx.lifecycle.viewModelScope
import app.shosetsu.android.common.SettingKey
import app.shosetsu.android.common.enums.AppThemes
import app.shosetsu.android.common.ext.launchIO
import app.shosetsu.android.domain.repository.base.ISettingsRepository
import app.shosetsu.android.viewmodel.abstracted.AIntroViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class IntroViewModel(
	private val settingsRepo: ISettingsRepository
) : AIntroViewModel() {
	override val isLicenseRead: MutableStateFlow<Boolean> = MutableStateFlow(false)

	override fun setLicenseRead() {
		isLicenseRead.value = true
	}

	override val isACRAEnabled: StateFlow<Boolean> =
		settingsRepo.getBooleanFlow(SettingKey.ACRAEnabled)
			.stateIn(viewModelScope, SharingStarted.Lazily, false)

	override val appTheme: StateFlow<AppThemes> =
		settingsRepo.getIntFlow(SettingKey.AppTheme)
			.map(AppThemes::fromKey)
			.stateIn(viewModelScope, SharingStarted.Lazily, AppThemes.fromKey(SettingKey.AppTheme.default))

	override fun setACRAEnabled(boolean: Boolean) {
		launchIO {
			settingsRepo.setBoolean(SettingKey.ACRAEnabled, boolean)
		}
	}

	override fun setAppTheme(theme: AppThemes) {
		launchIO {
			settingsRepo.setInt(SettingKey.AppTheme, theme.key)
		}
	}

	override var isFinished: Boolean = false

	override fun setFinished() {
		launchIO {
			isFinished = true
			settingsRepo.setBoolean(SettingKey.FirstTime, false)
		}
	}
}
