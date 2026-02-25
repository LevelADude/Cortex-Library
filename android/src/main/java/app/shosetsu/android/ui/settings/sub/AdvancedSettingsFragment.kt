package app.shosetsu.android.ui.settings.sub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.shosetsu.android.R
import app.shosetsu.android.common.SettingKey.ACRAEnabled
import app.shosetsu.android.common.SettingKey.AppTheme
import app.shosetsu.android.common.SettingKey.AutoBookmarkFromQR
import app.shosetsu.android.common.SettingKey.ConcurrentMemoryExperiment
import app.shosetsu.android.common.SettingKey.CustomThemeBackground
import app.shosetsu.android.common.SettingKey.CustomThemeEnabled
import app.shosetsu.android.common.SettingKey.CustomThemeIconTint
import app.shosetsu.android.common.SettingKey.CustomThemeNavSelected
import app.shosetsu.android.common.SettingKey.CustomThemeTopBarContainer
import app.shosetsu.android.common.SettingKey.CustomThemeBottomBarContainer
import app.shosetsu.android.common.SettingKey.CustomThemeBottomBarSelected
import app.shosetsu.android.common.SettingKey.CustomThemeOnBackground
import app.shosetsu.android.common.SettingKey.CustomThemeOnPrimary
import app.shosetsu.android.common.SettingKey.CustomThemePrimary
import app.shosetsu.android.common.SettingKey.CustomThemeSurface
import app.shosetsu.android.common.SettingKey.ExposeTrueChapterDelete
import app.shosetsu.android.common.SettingKey.LogToFile
import app.shosetsu.android.common.SettingKey.RequireDoubleBackToExit
import app.shosetsu.android.common.SettingKey.SiteProtectionDelay
import app.shosetsu.android.common.SettingKey.UseShosetsuAgent
import app.shosetsu.android.common.SettingKey.UserAgent
import app.shosetsu.android.common.SettingKey.VerifyCheckSum
import app.shosetsu.android.common.consts.DEFAULT_USER_AGENT
import app.shosetsu.android.common.ext.launchIO
import app.shosetsu.android.common.ext.launchUI
import app.shosetsu.android.common.ext.logE
import app.shosetsu.android.common.ext.logI
import app.shosetsu.android.common.ext.logV
import app.shosetsu.android.common.ext.makeSnackBar
import app.shosetsu.android.common.ext.viewModel
import app.shosetsu.android.view.compose.ShosetsuCompose
import app.shosetsu.android.view.compose.setting.ButtonSettingContent
import app.shosetsu.android.view.compose.setting.DropdownSettingContent
import app.shosetsu.android.view.compose.setting.SliderSettingContent
import app.shosetsu.android.view.compose.setting.StringSettingContent
import app.shosetsu.android.view.compose.setting.SwitchSettingContent
import app.shosetsu.android.view.controller.ShosetsuFragment
import app.shosetsu.android.view.uimodels.StableHolder
import app.shosetsu.android.viewmodel.abstracted.settings.AAdvancedSettingsViewModel
import app.shosetsu.android.ui.settings.CustomThemeParser
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_LONG
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking


/*
 * This file is part of Shosetsu.
 *
 * Shosetsu is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Shosetsu is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Shosetsu.  If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * Shosetsu
 * 13 / 07 / 2019
 */
class AdvancedSettingsFragment : ShosetsuFragment() {
	val viewModel: AAdvancedSettingsViewModel by viewModel()
	override val viewTitleRes: Int = R.string.settings_advanced

	/**
	 * Execute a purge from the view model, prompt to retry if failed
	 */
	private fun purgeNovelCache() {
		viewModel.purgeUselessData().observe(
			catch = {
				logE("Failed to purge")
				makeSnackBar(
					R.string.fragment_settings_advanced_snackbar_purge_failure,
					LENGTH_LONG
				)
					?.setAction(R.string.retry) { purgeNovelCache() }
					?.show()
			}
		) {

			makeSnackBar(R.string.fragment_settings_advanced_snackbar_purge_success)
				?.show()
		}
	}


	private fun clearWebCookies() {
		logI("User wants to clear cookies")
		logV("Clearing cookies")
		CookieManager.getInstance().removeAllCookies {
			logV("Cookies cleared")
			makeSnackBar(R.string.settings_advanced_clear_cookies_complete)?.show()
		}
	}


	private fun themeSelected(theme: app.shosetsu.android.common.enums.AppThemes) {
		launchUI {
			activity?.onBackPressedDispatcher?.onBackPressed()
			makeSnackBar(
				R.string.fragment_settings_advanced_snackbar_ui_change,
				Snackbar.LENGTH_INDEFINITE
			)?.setAction(R.string.apply) {
				launchIO {
					viewModel.settingsRepo.setInt(AppTheme, theme.key)
				}
			}?.show()
		}
	}

	private fun killCycleWorkers() {
		viewModel.killCycleWorkers()
		makeSnackBar(
			R.string.settings_advanced_snackbar_cycle_kill_success,
			LENGTH_LONG
		)?.apply {
			setAction(R.string.restart) {
				viewModel.startCycleWorkers()
				makeSnackBar(R.string.settings_advanced_cycle_start_success)?.show()
			}
		}?.show()
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedViewState: Bundle?
	): View = ComposeView(requireContext()).apply {
		setViewTitle()
		setContent {
			ShosetsuCompose {
				AdvancedSettingsContent(
					viewModel,
					onThemeSelected = ::themeSelected,
					onPurgeNovelCache = ::purgeNovelCache,
					onPurgeChapterCache = {},
					onKillCycleWorkers = ::killCycleWorkers,
					onClearCookies = ::clearWebCookies,
					onForceRepoSync = {
						viewModel.forceRepoSync()
					}
				)
			}
		}
	}
}


@Composable
fun AdvancedSettingsContent(
	viewModel: AAdvancedSettingsViewModel,
	onThemeSelected: (app.shosetsu.android.common.enums.AppThemes) -> Unit,
	onPurgeNovelCache: () -> Unit,
	onPurgeChapterCache: () -> Unit,
	onKillCycleWorkers: () -> Unit,
	onForceRepoSync: () -> Unit,
	onClearCookies: () -> Unit
) {
	val useShosetsuAgent by viewModel.settingsRepo.getBooleanFlow(UseShosetsuAgent)
		.collectAsState()
	val advancedActionButtonColors = ButtonDefaults.buttonColors(
		containerColor = MaterialTheme.colorScheme.primaryContainer,
		contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
	)

	Surface(
		modifier = Modifier.fillMaxSize(),
		color = MaterialTheme.colorScheme.background,
	) {
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				top = 16.dp,
				bottom = 64.dp
			),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
		item {
			val storedChoice by viewModel.settingsRepo.getIntFlow(AppTheme).collectAsState()
			val themes = app.shosetsu.android.common.enums.AppThemes.selectionOrder
			val selectionIndex = themes.indexOf(app.shosetsu.android.common.enums.AppThemes.fromKey(storedChoice)).coerceAtLeast(0)

			DropdownSettingContent(
				title = stringResource(R.string.theme),
				description = stringResource(R.string.settings_advanced_theme_desc),
				choices = stringArrayResource(R.array.application_themes).toList().toImmutableList(),
				modifier = Modifier.fillMaxWidth(),
				selection = selectionIndex,
				onSelection = { index -> onThemeSelected(themes[index]) }
			)
		}

		item {
			CustomThemeSettingsSection(viewModel)
		}

		item {
			ButtonSettingContent(
				title = stringResource(R.string.remove_novel_cache),
				description = stringResource(R.string.settings_advanced_purge_novel_cache),
				buttonText = stringResource(R.string.settings_advanced_purge_button),
				buttonColors = advancedActionButtonColors,
				modifier = Modifier
					.fillMaxWidth(),
				onClick = onPurgeNovelCache
			)
		}

		item {
			SwitchSettingContent(
				title = stringResource(R.string.settings_advanced_verify_checksum_title),
				description = stringResource(R.string.settings_advanced_verify_checksum_desc),
				modifier = Modifier
					.fillMaxWidth(),
				repo = viewModel.settingsRepo,
				key = VerifyCheckSum
			)
		}

		item {
			SwitchSettingContent(
				title = stringResource(R.string.settings_advanced_require_double_back_title),
				description = stringResource(R.string.settings_advanced_require_double_back_desc),
				modifier = Modifier
					.fillMaxWidth(),
				repo = viewModel.settingsRepo,
				key = RequireDoubleBackToExit
			)
		}

		item {
			ButtonSettingContent(
				title = stringResource(R.string.settings_advanced_kill_cycle_workers_title),
				description = stringResource(R.string.settings_advanced_kill_cycle_workers_desc),
				buttonText = stringResource(R.string.settings_advanced_kill_cycle_workers_button),
				buttonColors = advancedActionButtonColors,
				modifier = Modifier
					.fillMaxWidth(),
				onClick = onKillCycleWorkers
			)
		}

		item {
			ButtonSettingContent(
				title = stringResource(R.string.settings_advanced_force_repo_update_title),
				description = stringResource(R.string.settings_advanced_force_repo_update_desc),
				buttonText = stringResource(R.string.force),
				buttonColors = advancedActionButtonColors,
				modifier = Modifier
					.fillMaxWidth(),
				onClick = onForceRepoSync
			)
		}

		item {
			ButtonSettingContent(
				title = stringResource(R.string.settings_advanced_clear_cookies_title),
				description = stringResource(R.string.settings_advanced_clear_cookies_desc),
				buttonText = stringResource(R.string.settings_advanced_clear_cookies_button),
				buttonColors = advancedActionButtonColors,
				modifier = Modifier
					.fillMaxWidth(),
				onClick = onClearCookies
			)
		}

		item {
			SwitchSettingContent(
				title = stringResource(R.string.settings_advanced_true_chapter_delete_title),
				description = stringResource(R.string.settings_advanced_true_chapter_delete_desc),
				modifier = Modifier
					.fillMaxWidth(),
				repo = viewModel.settingsRepo,
				key = ExposeTrueChapterDelete
			)
		}

		item {
			SwitchSettingContent(
				title = stringResource(R.string.settings_advanced_log_title),
				description = stringResource(R.string.settings_advanced_log_desc),
				modifier = Modifier
					.fillMaxWidth(),
				repo = viewModel.settingsRepo,
				key = LogToFile
			)
		}

		item {
			SwitchSettingContent(
				title = stringResource(R.string.settings_advanced_auto_bookmark_title),
				description = stringResource(R.string.settings_advanced_auto_bookmark_desc),
				modifier = Modifier
					.fillMaxWidth(),
				repo = viewModel.settingsRepo,
				key = AutoBookmarkFromQR
			)
		}

		item {
			SwitchSettingContent(
				title = stringResource(R.string.intro_acra),
				description = stringResource(R.string.settings_advanced_enable_acra),
				modifier = Modifier
					.fillMaxWidth(),
				repo = viewModel.settingsRepo,
				key = ACRAEnabled
			)
		}

		item {
			SliderSettingContent(
				title = stringResource(R.string.settings_advanced_site_protection_title),
				description = stringResource(R.string.settings_advanced_site_protection_desc),
				valueRange = remember { StableHolder(300..5000) },
				parseValue = {
					"$it ms"
				},
				repo = viewModel.settingsRepo,
				key = SiteProtectionDelay,
				haveSteps = false,
			)
		}

		item {
			SwitchSettingContent(
				title = "Concurrent memory experiment",
				description =
				"""
					Enable if you experience random crashes during reading, this might help.
					Please tell developers you use this, as we are testing this.
					Requires restart.
				""".trimIndent(),
				modifier = Modifier
					.fillMaxWidth(),
				repo = viewModel.settingsRepo,
				key = ConcurrentMemoryExperiment
			)
		}

		item {
			SwitchSettingContent(
				title = stringResource(R.string.settings_advanced_sua_title),
				description = stringResource(R.string.settings_advanced_sua_desc),
				repo = viewModel.settingsRepo,
				modifier = Modifier
					.fillMaxWidth(),
				key = UseShosetsuAgent
			)
		}

		item {
			Column(
				modifier = Modifier
					.alpha(if (useShosetsuAgent) .5f else 1f),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				StringSettingContent(
					title = stringResource(R.string.settings_advanced_ua_title),
					description = stringResource(R.string.settings_advanced_ua_desc),
					repo = viewModel.settingsRepo,
					modifier = Modifier
						.fillMaxWidth(),
					key = UserAgent,
					enabled = !useShosetsuAgent
				)
				IconButton(
					onClick = {
						runBlocking {
							viewModel.settingsRepo.setString(UserAgent, DEFAULT_USER_AGENT)
						}
					},
					enabled = !useShosetsuAgent
				) {
					Icon(Icons.Default.Refresh, stringResource(R.string.reset))
				}
			}
		}
		}
	}
}

@Composable
private fun CustomThemeSettingsSection(viewModel: AAdvancedSettingsViewModel) {
	val customThemeEnabled by viewModel.settingsRepo.getBooleanFlow(CustomThemeEnabled).collectAsState()
	val background by viewModel.settingsRepo.getStringFlow(CustomThemeBackground).collectAsState()
	val surface by viewModel.settingsRepo.getStringFlow(CustomThemeSurface).collectAsState()
	val primary by viewModel.settingsRepo.getStringFlow(CustomThemePrimary).collectAsState()
	val onPrimary by viewModel.settingsRepo.getStringFlow(CustomThemeOnPrimary).collectAsState()
	val onBackground by viewModel.settingsRepo.getStringFlow(CustomThemeOnBackground).collectAsState()
	val iconTint by viewModel.settingsRepo.getStringFlow(CustomThemeIconTint).collectAsState()
	val navSelected by viewModel.settingsRepo.getStringFlow(CustomThemeNavSelected).collectAsState()
	val topBarContainer by viewModel.settingsRepo.getStringFlow(CustomThemeTopBarContainer).collectAsState()
	val bottomBarContainer by viewModel.settingsRepo.getStringFlow(CustomThemeBottomBarContainer).collectAsState()
	val bottomBarSelected by viewModel.settingsRepo.getStringFlow(CustomThemeBottomBarSelected).collectAsState()

	var backgroundError by remember { mutableStateOf<String?>(null) }
	var surfaceError by remember { mutableStateOf<String?>(null) }
	var primaryError by remember { mutableStateOf<String?>(null) }
	var onPrimaryError by remember { mutableStateOf<String?>(null) }
	var onBackgroundError by remember { mutableStateOf<String?>(null) }
	var iconTintError by remember { mutableStateOf<String?>(null) }
	var navSelectedError by remember { mutableStateOf<String?>(null) }
	var topBarContainerError by remember { mutableStateOf<String?>(null) }
	var bottomBarContainerError by remember { mutableStateOf<String?>(null) }
	var bottomBarSelectedError by remember { mutableStateOf<String?>(null) }

	SwitchSettingContent(
		title = stringResource(R.string.settings_advanced_custom_theme_toggle),
		description = stringResource(R.string.settings_advanced_custom_theme_desc),
		modifier = Modifier.fillMaxWidth(),
		repo = viewModel.settingsRepo,
		key = CustomThemeEnabled,
	)

	if (!customThemeEnabled) return

	Column(modifier = Modifier.fillMaxWidth()) {
		CustomThemeColorField(
			title = stringResource(R.string.settings_advanced_custom_theme_background),
			value = background,
			error = backgroundError,
			onValueChange = { value ->
				backgroundError = persistCustomColor(viewModel, CustomThemeBackground, value, required = true)
			},
		)
		CustomThemeColorField(
			title = stringResource(R.string.settings_advanced_custom_theme_surface),
			value = surface,
			error = surfaceError,
			onValueChange = { value ->
				surfaceError = persistCustomColor(viewModel, CustomThemeSurface, value, required = true)
			},
		)
		CustomThemeColorField(
			title = stringResource(R.string.settings_advanced_custom_theme_primary),
			value = primary,
			error = primaryError,
			onValueChange = { value ->
				primaryError = persistCustomColor(viewModel, CustomThemePrimary, value, required = true)
			},
		)
		CustomThemeColorField(
			title = stringResource(R.string.settings_advanced_custom_theme_on_primary),
			value = onPrimary,
			error = onPrimaryError,
			onValueChange = { value ->
				onPrimaryError = persistCustomColor(viewModel, CustomThemeOnPrimary, value, required = true)
			},
		)
		CustomThemeColorField(
			title = stringResource(R.string.settings_advanced_custom_theme_on_background),
			value = onBackground,
			error = onBackgroundError,
			onValueChange = { value ->
				onBackgroundError = persistCustomColor(viewModel, CustomThemeOnBackground, value, required = true)
			},
		)
		CustomThemeColorField(
			title = stringResource(R.string.settings_advanced_custom_theme_icon_tint),
			value = iconTint,
			error = iconTintError,
			onValueChange = { value ->
				iconTintError = persistCustomColor(viewModel, CustomThemeIconTint, value, required = false)
			},
		)
		CustomThemeColorField(
			title = stringResource(R.string.settings_advanced_custom_theme_nav_selected),
			value = navSelected,
			error = navSelectedError,
			onValueChange = { value ->
				navSelectedError = persistCustomColor(viewModel, CustomThemeNavSelected, value, required = false)
			},
		)
		CustomThemeColorField(
			title = stringResource(R.string.settings_advanced_custom_theme_top_bar_container),
			value = topBarContainer,
			error = topBarContainerError,
			onValueChange = { value ->
				topBarContainerError = persistCustomColor(viewModel, CustomThemeTopBarContainer, value, required = false)
			},
		)
		CustomThemeColorField(
			title = stringResource(R.string.settings_advanced_custom_theme_bottom_bar_container),
			value = bottomBarContainer,
			error = bottomBarContainerError,
			onValueChange = { value ->
				bottomBarContainerError = persistCustomColor(viewModel, CustomThemeBottomBarContainer, value, required = false)
			},
		)
		CustomThemeColorField(
			title = stringResource(R.string.settings_advanced_custom_theme_bottom_bar_selected),
			value = bottomBarSelected,
			error = bottomBarSelectedError,
			onValueChange = { value ->
				bottomBarSelectedError = persistCustomColor(viewModel, CustomThemeBottomBarSelected, value, required = false)
			},
		)

		Button(
			onClick = {
				launchIO {
					viewModel.settingsRepo.setString(CustomThemeBackground, "")
					viewModel.settingsRepo.setString(CustomThemeSurface, "")
					viewModel.settingsRepo.setString(CustomThemePrimary, "")
					viewModel.settingsRepo.setString(CustomThemeOnPrimary, "")
					viewModel.settingsRepo.setString(CustomThemeOnBackground, "")
					viewModel.settingsRepo.setString(CustomThemeIconTint, "")
					viewModel.settingsRepo.setString(CustomThemeNavSelected, "")
					viewModel.settingsRepo.setString(CustomThemeTopBarContainer, "")
					viewModel.settingsRepo.setString(CustomThemeBottomBarContainer, "")
					viewModel.settingsRepo.setString(CustomThemeBottomBarSelected, "")
				}
			},
			modifier = Modifier
				.padding(horizontal = 16.dp)
				.fillMaxWidth(),
		) {
			Text(text = stringResource(R.string.reset))
		}
	}
}

@Composable
private fun CustomThemeColorField(
	title: String,
	value: String,
	error: String?,
	onValueChange: (String) -> Unit,
) {
	val parsedColor = remember(value) { CustomThemeParser.parseColor(value) }
	val swatchColor = parsedColor ?: Color(0xFF9E9E9E)
	var showPicker by remember { mutableStateOf(false) }

	Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
		Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			OutlinedTextField(
				value = value,
				onValueChange = onValueChange,
				isError = error != null,
				placeholder = { Text(text = "#081328 or 8,19,40") },
				modifier = Modifier.weight(1f),
				singleLine = true,
			)
			Box(
				modifier = Modifier
					.size(40.dp)
					.background(swatchColor, shape = MaterialTheme.shapes.small)
					.border(
						width = if (parsedColor == null) 2.dp else 1.dp,
						color = if (parsedColor == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
						shape = MaterialTheme.shapes.small,
					)
					.clickable { showPicker = true },
			)
		}
		if (error != null) {
			Text(text = error, color = MaterialTheme.colorScheme.error)
		}
	}

	if (showPicker) {
		ColorPickerDialog(
			initialColor = parsedColor ?: Color.White,
			onDismiss = { showPicker = false },
			onConfirm = {
				onValueChange(it.toArgbHexString())
				showPicker = false
			},
		)
	}
}

@Composable
private fun ColorPickerDialog(
	initialColor: Color,
	onDismiss: () -> Unit,
	onConfirm: (Color) -> Unit,
) {
	var hue by remember(initialColor) { mutableStateOf(initialColor.toHue()) }
	var saturation by remember(initialColor) { mutableStateOf(initialColor.toSaturation()) }
	var value by remember(initialColor) { mutableStateOf(initialColor.toValue()) }
	val selectedColor = remember(hue, saturation, value) { Color.hsv(hue, saturation, value) }

	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = { onConfirm(selectedColor) }) { Text(stringResource(android.R.string.ok)) }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
		},
		title = { Text(stringResource(R.string.settings_advanced_custom_theme_picker_title)) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				HsvSaturationValuePicker(
					hue = hue,
					saturation = saturation,
					value = value,
					onChange = { newSaturation, newValue ->
						saturation = newSaturation
						value = newValue
					},
				)
				HueSlider(
					hue = hue,
					onChange = { hue = it },
				)
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(32.dp)
						.background(selectedColor, shape = MaterialTheme.shapes.small)
						.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
				)
				Text(selectedColor.toArgbHexString())
			}
		},
	)
}

@Composable
private fun HsvSaturationValuePicker(
	hue: Float,
	saturation: Float,
	value: Float,
	onChange: (Float, Float) -> Unit,
) {
	val hueColor = remember(hue) { Color.hsv(hue, 1f, 1f) }
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(180.dp)
			.background(
				brush = Brush.horizontalGradient(listOf(Color.White, hueColor)),
				shape = MaterialTheme.shapes.small,
			)
			.drawWithContent {
				drawContent()
				drawRect(
					brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
				)
			}
			.pointerInput(hue) {
				detectDragGestures(
					onDragStart = { offset ->
						val width = size.width.coerceAtLeast(1).toFloat()
						val height = size.height.coerceAtLeast(1).toFloat()
						onChange((offset.x / width).coerceIn(0f, 1f), 1f - (offset.y / height).coerceIn(0f, 1f))
					},
					onDrag = { change, _ ->
						val width = size.width.coerceAtLeast(1).toFloat()
						val height = size.height.coerceAtLeast(1).toFloat()
						onChange((change.position.x / width).coerceIn(0f, 1f), 1f - (change.position.y / height).coerceIn(0f, 1f))
					},
				)
			},
	) {
		Canvas(modifier = Modifier.fillMaxSize()) {
			val x = saturation * size.width
			val y = (1f - value) * size.height
			drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(x, y), style = Stroke(width = 2.dp.toPx()))
		}
	}
}

@Composable
private fun HueSlider(hue: Float, onChange: (Float) -> Unit) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(24.dp)
			.background(
				brush = Brush.horizontalGradient(
					listOf(
						Color.Red,
						Color.Yellow,
						Color.Green,
						Color.Cyan,
						Color.Blue,
						Color.Magenta,
						Color.Red,
					),
				),
				shape = MaterialTheme.shapes.small,
			)
			.pointerInput(Unit) {
				detectDragGestures(
					onDragStart = { offset ->
						val width = size.width.coerceAtLeast(1).toFloat()
						onChange((offset.x / width).coerceIn(0f, 1f) * 360f)
					},
					onDrag = { change, _ ->
						val width = size.width.coerceAtLeast(1).toFloat()
						onChange((change.position.x / width).coerceIn(0f, 1f) * 360f)
					},
				)
			},
	) {
		Canvas(modifier = Modifier.fillMaxSize()) {
			val x = (hue / 360f).coerceIn(0f, 1f) * size.width
			drawLine(
				color = Color.White,
				start = Offset(x, 0f),
				end = Offset(x, size.height),
				strokeWidth = 3.dp.toPx(),
			)
		}
	}
}

private fun Color.toHue(): Float {
	val hsv = FloatArray(3)
	android.graphics.Color.colorToHSV(this.toArgb(), hsv)
	return hsv[0]
}

private fun Color.toSaturation(): Float {
	val hsv = FloatArray(3)
	android.graphics.Color.colorToHSV(this.toArgb(), hsv)
	return hsv[1]
}

private fun Color.toValue(): Float {
	val hsv = FloatArray(3)
	android.graphics.Color.colorToHSV(this.toArgb(), hsv)
	return hsv[2]
}

private fun Int.toArgbHexString(): String = String.format("#%08X", this)

private fun Color.toArgbHexString(): String = toArgb().toArgbHexString()

private fun persistCustomColor(
	viewModel: AAdvancedSettingsViewModel,
	key: app.shosetsu.android.common.StringKey,
	value: String,
	required: Boolean,
): String? {
	val trimmed = value.trim()
	if (trimmed.isEmpty()) {
		if (required) return "Required. Use #AARRGGBB or 255,128,0"
		launchIO { viewModel.settingsRepo.setString(key, "") }
		return null
	}

	val normalized = CustomThemeParser.normalize(trimmed)
	if (normalized == null) {
		return "Invalid color. Use #RRGGBB, #AARRGGBB, or RGB"
	}
	launchIO { viewModel.settingsRepo.setString(key, normalized) }
	return null
}
