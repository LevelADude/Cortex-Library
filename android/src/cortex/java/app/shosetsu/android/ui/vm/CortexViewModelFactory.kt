package app.shosetsu.android.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CortexViewModelFactory<T : ViewModel>(
    private val builder: () -> T
) : ViewModelProvider.Factory {
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = builder() as VM
}
