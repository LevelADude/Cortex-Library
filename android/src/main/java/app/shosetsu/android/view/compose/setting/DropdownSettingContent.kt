package app.shosetsu.android.view.compose.setting

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.shosetsu.android.common.SettingKey
import app.shosetsu.android.common.ext.launchIO
import app.shosetsu.android.domain.repository.base.ISettingsRepository
import kotlinx.collections.immutable.ImmutableList

@Composable
fun DropdownSettingContent(
	title: String,
	description: String,
	choices: ImmutableList<String>,
	modifier: Modifier = Modifier,
	repo: ISettingsRepository,
	key: SettingKey<Int>
) {
	val choice by repo.getIntFlow(key).collectAsState()

	DropdownSettingContent(title, description, choice, choices, modifier) {
		launchIO { repo.setInt(key, it) }
	}

}

@Composable
fun DropdownSettingContent(
	title: String,
	description: String,
	choices: ImmutableList<String>,
	modifier: Modifier = Modifier,
	repo: ISettingsRepository,
	key: SettingKey<String>,
	stringToInt: (value: String) -> Int,
	intToString: (value: Int) -> String
) {
	val choice by repo.getStringFlow(key).collectAsState()

	DropdownSettingContent(title, description, stringToInt(choice), choices, modifier) {
		launchIO { repo.setString(key, intToString(it)) }
	}

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSettingContent(
	title: String,
	description: String,
	selection: Int,
	choices: ImmutableList<String>,
	modifier: Modifier = Modifier,
	onSelection: (newValue: Int) -> Unit
) {
	var expanded by remember { mutableStateOf(false) }
	val safeSelection = selection.coerceIn(choices.indices)

	GenericRightSettingLayout(title, description, modifier, onClick = { expanded = !expanded }) {
		ExposedDropdownMenuBox(
			expanded = expanded,
			onExpandedChange = { expanded = !expanded },
		) {
			OutlinedTextField(
				value = choices[safeSelection],
				onValueChange = {},
				readOnly = true,
				singleLine = true,
				modifier = Modifier
					.menuAnchor()
					.fillMaxWidth(),
				textStyle = MaterialTheme.typography.bodyMedium,
				trailingIcon = {
					ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
				},
				colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
			)
			ExposedDropdownMenu(
				expanded = expanded,
				onDismissRequest = { expanded = false },
			) {
				choices.forEachIndexed { index, entry ->
					DropdownMenuItem(
						text = { Text(text = entry) },
						onClick = {
							onSelection(index)
							expanded = false
						}
					)
				}
			}
		}
	}
}
