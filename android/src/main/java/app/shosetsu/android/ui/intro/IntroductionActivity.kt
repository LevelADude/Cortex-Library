package app.shosetsu.android.ui.intro

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.shosetsu.android.R
import app.shosetsu.android.common.enums.AppThemes
import app.shosetsu.android.common.ext.viewModelDi
import app.shosetsu.android.view.compose.MonogatariTheme
import app.shosetsu.android.viewmodel.abstracted.AIntroViewModel
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI

class IntroductionActivity : AppCompatActivity(), DIAware {
	override val di: DI by closestDI()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			IntroRoot(exit = ::finish)
		}
	}
}

private enum class IntroStep { Welcome, Permissions, Personalization, Done }

@Composable
private fun IntroRoot(viewModel: AIntroViewModel = viewModelDi(), exit: () -> Unit) {
	val appTheme by viewModel.appTheme.collectAsState()
	var selectedTheme by remember { mutableStateOf(appTheme) }
	MonogatariTheme(theme = selectedTheme) {
		IntroView(
			viewModel = viewModel,
			selectedTheme = selectedTheme,
			onThemeSelected = {
				selectedTheme = it
				viewModel.setAppTheme(it)
			},
			exit = exit,
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroView(
	viewModel: AIntroViewModel,
	selectedTheme: AppThemes,
	onThemeSelected: (AppThemes) -> Unit,
	exit: () -> Unit,
) {
	var stepIndex by rememberSaveable { mutableStateOf(0) }
	val step = IntroStep.entries[stepIndex]
	val canRequestNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
	val context = LocalContext.current
	var permissionGranted by remember { mutableStateOf(!canRequestNotifications) }
	var permissionDenied by remember { mutableStateOf(false) }
	val isACRAEnabled by viewModel.isACRAEnabled.collectAsState()
	val isLastStep = step == IntroStep.Done
	val targetProgress = (stepIndex + 1).toFloat() / IntroStep.entries.size.toFloat()
	val animatedProgress by animateFloatAsState(
		targetValue = targetProgress,
		animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
		label = "intro-progress",
	)

	val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
		permissionGranted = granted
		permissionDenied = !granted
	}

	BackHandler { if (stepIndex == 0) exit() else stepIndex -= 1 }

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(stringResource(R.string.intro_title_greet)) },
				actions = {
					if (!isLastStep) {
						TextButton(onClick = {
							viewModel.setAppTheme(selectedTheme)
							viewModel.setFinished()
							exit()
						}) { Text(stringResource(R.string.skip_label)) }
					}
				},
			)
		},
		bottomBar = {
			Column(modifier = Modifier.padding(16.dp)) {
				LinearProgressIndicator(progress = animatedProgress, modifier = Modifier.fillMaxWidth())
				Spacer(modifier = Modifier.height(12.dp))
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
					OutlinedButton(onClick = { if (stepIndex > 0) stepIndex -= 1 }, enabled = stepIndex > 0, modifier = Modifier.weight(1f)) {
						Text(stringResource(android.R.string.cancel))
					}
					Button(onClick = {
						if (isLastStep) {
							viewModel.setAppTheme(selectedTheme)
							viewModel.setFinished()
							exit()
						} else {
							stepIndex += 1
						}
					}, modifier = Modifier.weight(1f)) {
						Text(stringResource(if (isLastStep) R.string.intro_cta_done else R.string.intro_cta_continue))
					}
				}
			}
		},
	) { padding ->
		Crossfade(
			targetState = stepIndex,
			modifier = Modifier.padding(padding),
			label = "IntroCrossfade",
		) { idx ->
			when (IntroStep.entries[idx]) {
				IntroStep.Welcome -> WelcomeStep()
				IntroStep.Permissions -> PermissionStep(
					granted = permissionGranted,
					denied = permissionDenied,
					onRequest = { if (canRequestNotifications) notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
					onOpenSettings = {
						val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
							data = Uri.fromParts("package", context.packageName, null)
						}
						try {
							context.startActivity(intent)
						} catch (_: ActivityNotFoundException) {
							context.startActivity(Intent(Settings.ACTION_SETTINGS))
						}
					},
				)
				IntroStep.Personalization -> PersonalizationStep(
					selectedTheme = selectedTheme,
					onThemeSelected = onThemeSelected,
					acraEnabled = isACRAEnabled,
					onAcraChanged = viewModel::setACRAEnabled,
				)
				IntroStep.Done -> DoneStep()
			}
		}
	}
}

@Composable
private fun IntroStepContainer(content: @Composable ColumnScope.() -> Unit) {
	Column(
		modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.Center,
		content = content,
	)
}

@Composable
private fun WelcomeStep() = IntroStepContainer {
	Text(stringResource(R.string.intro_what_is_app), style = MaterialTheme.typography.headlineMedium)
	Spacer(modifier = Modifier.height(12.dp))
	Text(stringResource(R.string.intro_what_is_app_desc_new), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun PermissionStep(granted: Boolean, denied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) = IntroStepContainer {
	Icon(Icons.Outlined.Notifications, contentDescription = null, modifier = Modifier.align(Alignment.CenterHorizontally))
	Spacer(modifier = Modifier.height(12.dp))
	Text(stringResource(R.string.intro_perm_title), style = MaterialTheme.typography.headlineSmall)
	Spacer(modifier = Modifier.height(8.dp))
	Text(stringResource(R.string.intro_perm_notif_desc), style = MaterialTheme.typography.bodyLarge)
	Spacer(modifier = Modifier.height(16.dp))
	Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.intro_permission_allow)) }
	if (granted) {
		Spacer(modifier = Modifier.height(8.dp))
		AssistChip(onClick = {}, label = { Text(stringResource(R.string.intro_permission_granted)) })
	}
	if (denied) {
		Spacer(modifier = Modifier.height(8.dp))
		Text(stringResource(R.string.intro_permission_denied_help), color = MaterialTheme.colorScheme.error)
		TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.intro_open_settings)) }
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalizationStep(
	selectedTheme: AppThemes,
	onThemeSelected: (AppThemes) -> Unit,
	acraEnabled: Boolean,
	onAcraChanged: (Boolean) -> Unit,
) = IntroStepContainer {
	val themeChoices = AppThemes.selectionOrder
	Icon(Icons.Outlined.Notifications, contentDescription = null)
	Spacer(modifier = Modifier.height(12.dp))
	Text(stringResource(R.string.intro_personalize_title), style = MaterialTheme.typography.headlineSmall)
	Spacer(modifier = Modifier.height(12.dp))
	FlowRow(
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		modifier = Modifier.padding(vertical = 4.dp)
	) {
		themeChoices.forEach { theme ->
			ThemeChoiceCard(
				theme = theme,
				selected = selectedTheme == theme,
				onClick = { onThemeSelected(theme) },
			)
		}
	}
	Spacer(modifier = Modifier.height(16.dp))
	Card(shape = RoundedCornerShape(16.dp)) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(16.dp),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(stringResource(R.string.intro_acra), fontWeight = FontWeight.SemiBold)
				Text(stringResource(R.string.intro_acra_desc), style = MaterialTheme.typography.bodySmall)
			}
			Switch(checked = acraEnabled, onCheckedChange = onAcraChanged)
		}
	}
}

@Composable
private fun ThemeChoiceCard(theme: AppThemes, selected: Boolean, onClick: () -> Unit) {
	MonogatariTheme(theme = theme) {
		Card(
			modifier = Modifier
				.width(160.dp)
				.padding(bottom = 8.dp)
				.selectable(selected = selected, onClick = onClick),
			shape = RoundedCornerShape(16.dp),
			colors = CardDefaults.cardColors(
				containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
			),
		) {
			Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					RadioButton(selected = selected, onClick = onClick)
					Text(theme.displayName(), style = MaterialTheme.typography.labelLarge)
				}
				ThemeMiniPreview()
			}
		}
	}
}

@Composable
private fun ThemeMiniPreview() {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
			.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
			.padding(8.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(10.dp)
				.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
		)
		Box(
			modifier = Modifier
				.fillMaxWidth(0.85f)
				.height(8.dp)
				.background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
		)
		Box(
			modifier = Modifier
				.size(width = 52.dp, height = 14.dp)
				.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(7.dp)),
		)
	}
}

private fun AppThemes.displayName(): String = when (this) {
	AppThemes.FOLLOW_SYSTEM -> "Follow System"
	AppThemes.LIGHT -> "Light"
	AppThemes.DARK -> "Dark"
	AppThemes.EMERALD_MANUSCRIPT -> "Emerald Manuscript"
	AppThemes.MIDNIGHT_INK,
	AppThemes.MIDNIGHT_INK_GOLD -> "Midnight Ink"
}

@Composable
private fun DoneStep() = IntroStepContainer {
	Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.align(Alignment.CenterHorizontally))
	Spacer(modifier = Modifier.height(12.dp))
	Text(stringResource(R.string.intro_happy_end), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
	Spacer(modifier = Modifier.height(8.dp))
	Text(stringResource(R.string.intro_happy_end_desc), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
}
