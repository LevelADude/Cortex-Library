package app.shosetsu.android.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.window.layout.WindowMetricsCalculator
import androidx.lifecycle.lifecycleScope
import app.shosetsu.android.R
import app.shosetsu.android.BuildConfig
import app.shosetsu.android.common.SettingKey
import app.shosetsu.android.common.consts.*
import app.shosetsu.android.common.consts.BundleKeys.BUNDLE_QUERY
import app.shosetsu.android.common.consts.BundleKeys.BUNDLE_URL
import app.shosetsu.android.common.enums.AppThemes
import app.shosetsu.android.common.enums.NavigationStyle.LEGACY
import app.shosetsu.android.common.enums.NavigationStyle.MATERIAL
import app.shosetsu.android.common.ext.*
import app.shosetsu.android.databinding.ActivityMainBinding
import app.shosetsu.android.domain.repository.base.IBackupRepository
import app.shosetsu.android.ui.intro.IntroductionActivity
import app.shosetsu.android.ui.splash.MonogatariIntroSplash
import app.shosetsu.android.view.compose.DefaultDarkColorScheme
import app.shosetsu.android.view.compose.applySystemBars
import app.shosetsu.android.view.controller.base.CollapsedToolBarController
import app.shosetsu.android.view.controller.base.ExtendedFABController
import app.shosetsu.android.view.controller.base.HomeFragment
import app.shosetsu.android.view.controller.base.LiftOnScrollToolBarController
import app.shosetsu.android.viewmodel.abstracted.AMainViewModel
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.MaterialCalendar
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.snackbar.BaseTransientBottomBar.Duration
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.acra.ACRA
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI


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
 * 9 / June / 2019
 *
 * @author github.com/doomsdayrs
 */
class MainActivity : AppCompatActivity(), DIAware {
	companion object {
		/**
		 * I forgot what this does
		 */
		const val INTRO_CODE: Int = 1944
	}

	override val di: DI by closestDI()

	private lateinit var binding: ActivityMainBinding

	private var registered = false
	private var introOverlayFirstFrameReady = false
	private var keepSystemSplashVisible = true
	private var lastLoggedKeepCondition: Boolean? = null

	private var actionBarDrawerToggle: ActionBarDrawerToggle? = null
	private var activeTheme: AppThemes = AppThemes.FOLLOW_SYSTEM
	private val appSettings by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }
	private val customThemePreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
		if (key?.startsWith("custom_theme_") == true && ::binding.isInitialized) {
			applyMaterialNavigationTheme()
		}
	}

	private val viewModel: AMainViewModel by viewModel()

	private val splashResultLauncher =
		registerForActivityResult(StartActivityForResult()) {
			if (it.resultCode == Activity.RESULT_OK) {
				viewModel.toggleShowIntro()
			}
		}

	private val navHostFragment: NavHostFragment
		get() = supportFragmentManager.findFragmentById(R.id.controller_container) as NavHostFragment

	private val navController: NavController
		get() = navHostFragment.navController

	private val navChildFragmentManager
		get() = navHostFragment.childFragmentManager

	private val materialTopLevelDestinations = setOf(
		R.id.libraryController,
		R.id.updatesController,
		R.id.browseController,
		R.id.moreController,
	)

	private var materialDestinationListener: NavController.OnDestinationChangedListener? = null

	private val broadcastReceiver by lazy {
		object : BroadcastReceiver() {
			override fun onReceive(context: Context?, intent: Intent?) {
				intent?.let {
					handleIntentAction(it)
				} ?: logE("Null intent recieved")
			}
		}
	}

	/**
	 * Destroy the main activity
	 */
	override fun onDestroy() {
		if (registered)
			unregisterReceiver(broadcastReceiver)
		appSettings.unregisterOnSharedPreferenceChangeListener(customThemePreferenceListener)
		super.onDestroy()
	}

	/**
	 * Create the main activity
	 */
	override fun onCreate(savedInstanceState: Bundle?) {
		Log.d("Splash", "MainActivity.onCreate")
		val splashScreen = installSplashScreen()
		splashScreen.setOnExitAnimationListener { provider ->
			provider.view.animate()
				.alpha(0f)
				.setInterpolator(AccelerateDecelerateInterpolator())
				.setDuration(260L)
				.withEndAction { provider.remove() }
				.start()
		}
		splashScreen.setKeepOnScreenCondition {
			val keepSplash = keepSystemSplashVisible && !introOverlayFirstFrameReady
			if (lastLoggedKeepCondition != keepSplash) {
				lastLoggedKeepCondition = keepSplash
				Log.d(
					"Splash",
					"setKeepOnScreenCondition checked -> keepSplash=$keepSplash, keepSystemSplashVisible=$keepSystemSplashVisible, introOverlayFirstFrameReady=$introOverlayFirstFrameReady"
				)
			}
			keepSplash
		}
		lifecycleScope.launch {
			delay(1200)
			keepSystemSplashVisible = false
			Log.d("Splash", "System splash fail-safe timeout reached -> releasing keep condition")
		}

		onBackPressedDispatcher.addCallback(this) {
			logI("Back pressed")
			val backStackSize = navController.backQueue.size
			logD("Back stack size: $backStackSize")
			when {
				binding.drawerLayout.isDrawerOpen(GravityCompat.START) ->
					binding.drawerLayout.closeDrawer(GravityCompat.START)

				backStackSize > 2 -> {
					navController.navigateUp()
				}

				shouldProtectBack() -> protectedBackWait()

				backStackSize == 2 -> this@MainActivity.finish()
			}
		}

		runBlocking {
			activeTheme = viewModel.appThemeLiveData.first()
			setTheme(activeTheme)
		}
		applyDynamicColorsIfNeeded(activeTheme)
		viewModel.appThemeLiveData.collectLA(this, catch = {
			makeSnackBar(
				getString(
					R.string.activity_main_error_theme,
					it.message ?: "Unknown error"
				)
			).setAction(R.string.report) { _ ->
				ACRA.errorReporter.handleSilentException(it)
			}.show()
		}) {
			activeTheme = it
			setTheme(it)
			applyDynamicColorsIfNeeded(it)
			applySystemBars(resolveSystemBarsColor(), forceLightSystemBarIcons = isDefaultDarkTheme(resolveThemeInt(R.attr.appThemeVariant)))
			if (::binding.isInitialized) applyMaterialNavigationTheme()
		}
		this.requestPerms()
		super.onCreate(savedInstanceState)
		applySystemBars(resolveSystemBarsColor(), forceLightSystemBarIcons = isDefaultDarkTheme(resolveThemeInt(R.attr.appThemeVariant)))

		// Do not let the launcher create a new activity http://stackoverflow.com/questions/16283079
		if (!isTaskRoot) {
			logI("Broadcasting intent ${intent.action} ${intent.categories}")
			sendBroadcast(Intent(intent))
			finish()
			return
		}
		registerReceiver(broadcastReceiver, IntentFilter().apply {
			addAction(ACTION_OPEN_UPDATES)
			addAction(ACTION_OPEN_LIBRARY)
			addAction(ACTION_OPEN_CATALOGUE)
			addAction(ACTION_OPEN_SEARCH)
			addAction(ACTION_OPEN_APP_UPDATE)
			addAction(ACTION_DOWNLOAD_COMPLETE)
			addAction(ACTION_VIEW)
			addCategory(CATEGORY_BROWSABLE)
		})
		registered = true

		runBlocking {
			// Settings setup
			if (viewModel.showIntro())
				splashResultLauncher.launch(
					Intent(
						this@MainActivity,
						IntroductionActivity::class.java
					)
				)
		}
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		showMonogatariIntroOverlay()
		computeWindowSizeClasses()
		setupNavigationController()
		applyMaterialNavigationTheme()
		appSettings.registerOnSharedPreferenceChangeListener(customThemePreferenceListener)
		handleIntentAction(intent)
		setupProcesses()
	}

	private fun showMonogatariIntroOverlay() {
		val root = findViewById<ViewGroup>(android.R.id.content)
		Log.d("Splash", "Compose intro overlay shown")
		val overlay = ComposeView(this).apply {
			setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
			isClickable = true
			isFocusable = true
			isFocusableInTouchMode = true
			setContent {
				MonogatariIntroSplash {
					Log.d("Splash", "Compose intro overlay onFinished called")
					root.post { root.removeView(this) }
				}
			}
		}
		overlay.doOnPreDraw {
			introOverlayFirstFrameReady = true
			Log.d("Splash", "Compose intro overlay first frame ready")
		}
		root.addView(overlay, ViewGroup.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.MATCH_PARENT
		))
	}


	private var isTablet = false

	/**
	 * Observe configuration changed
	 */
	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)
		computeWindowSizeClasses()
	}

	/**
	 * Compute the dimensions of the display and morph the UI to match
	 */
	private fun computeWindowSizeClasses() {
		val metrics = WindowMetricsCalculator.getOrCreate()
			.computeCurrentWindowMetrics(this)

		val metricsWidth = metrics.bounds.width() / resources.displayMetrics.density

		isTablet = metricsWidth > 600
		logD("Is tablet?: $isTablet $metricsWidth")

		binding.navRail.removeHeaderView()
		binding.coordinator.removeView(binding.efab)
		binding.coordinator.addView(binding.efab)

		if (viewModel.navigationStyle.value == MATERIAL) {
			binding.navRail.isVisible = isTablet
			binding.navBottom.isVisible = !isTablet

			if (isTablet) {
				binding.coordinator.removeView(binding.efab)
				binding.navRail.addHeaderView(binding.efab)
				binding.efab.shrink()
			}
		}

		setupView()
	}

	/**
	 * Get the current navigation bar view
	 *
	 * If [isTablet] true, then the nav rail will be provided, else the bottom nav
	 */
	private fun getMaterialNav(): NavigationBarView {
		return if (isTablet)
			binding.navRail
		else binding.navBottom
	}

	/**
	 * Re-sync the action bar drawer toggle
	 */
	override fun onPostCreate(savedInstanceState: Bundle?) {
		super.onPostCreate(savedInstanceState)

		actionBarDrawerToggle?.syncState()
	}

	/**
	 * If true, the app is preventing the user from leaving the app accidentally
	 */
	private var inProtectingBack = false

	private fun protectedBackWait() {
		launchIO {
			inProtectingBack = true
			val snackBar =
				makeSnackBar(R.string.double_back_message, Snackbar.LENGTH_INDEFINITE).apply {
					setOnDismissed { _, _ ->
						inProtectingBack = false
					}
				}
			snackBar.show()
			delay(2000)
			snackBar.dismiss()
		}
	}

	private fun shouldProtectBack(): Boolean =
		navController.backQueue.size == 2 &&
				viewModel.requireDoubleBackToExit.value &&
				!inProtectingBack


	private fun applyMaterialNavigationTheme() {
		val appThemeVariant = resolveThemeInt(R.attr.appThemeVariant)
		val isDefaultDarkTheme = isDefaultDarkTheme(appThemeVariant)
		val customNavigationTheme = resolveCustomNavigationTheme()
		val (background, selectedColor, unselectedColor, indicatorColor, onSurfaceColor) = when (appThemeVariant) {
			AppThemes.EMERALD_MANUSCRIPT.key -> NavigationThemePalette(
				background = AndroidColor.parseColor("#114B47"),
				selected = AndroidColor.parseColor("#E6F4E8"),
				unselected = AndroidColor.parseColor("#B8D7C0"),
				indicator = AndroidColor.parseColor("#2D6A4F"),
				onSurface = AndroidColor.parseColor("#E6F4E8"),
			)

			AppThemes.MIDNIGHT_INK_GOLD.key -> NavigationThemePalette(
				background = AndroidColor.parseColor("#0F172A"),
				selected = AndroidColor.parseColor("#E0C777"),
				unselected = AndroidColor.parseColor("#E3DBCD"),
				indicator = AndroidColor.parseColor("#3C2F13"),
				onSurface = AndroidColor.parseColor("#F5EFE6"),
			)

			AppThemes.DARK.key,
			AppThemes.FOLLOW_SYSTEM.key,
			0 -> if (isDefaultDarkTheme) {
				NavigationThemePalette(
					background = DefaultDarkColorScheme.surface.toArgb(),
					selected = DefaultDarkColorScheme.primary.toArgb(),
					unselected = DefaultDarkColorScheme.onSurfaceVariant.toArgb(),
					indicator = DefaultDarkColorScheme.primary.copy(alpha = 0.16f).toArgb(),
					onSurface = DefaultDarkColorScheme.onSurface.toArgb(),
				)
			} else {
				NavigationThemePalette(
					background = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0),
					selected = resolveThemePrimaryColor(),
					unselected = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0),
					indicator = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer, 0),
					onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0),
				)
			}

			else -> NavigationThemePalette(
				background = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0),
				selected = resolveThemePrimaryColor(),
				unselected = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0),
				indicator = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer, 0),
				onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0),
			)
		}
		val appliedSelectedColor = customNavigationTheme.bottomBarSelected ?: selectedColor
		val appliedIndicatorColor = customNavigationTheme.bottomBarSelected ?: indicatorColor
		val appliedNavigationBackground = customNavigationTheme.bottomBarContainer ?: background
		val appliedTopBarBackground = customNavigationTheme.topBarContainer ?: background

		val itemColorStateList = ColorStateList(
			arrayOf(
				intArrayOf(android.R.attr.state_checked),
				intArrayOf(-android.R.attr.state_checked),
			),
			intArrayOf(appliedSelectedColor, unselectedColor),
		)

		listOf(binding.navBottom, binding.navRail).forEach { nav ->
			nav.setBackgroundColor(appliedNavigationBackground)
			nav.itemIconTintList = itemColorStateList
			nav.itemTextColor = itemColorStateList
			nav.itemActiveIndicatorColor = ColorStateList.valueOf(appliedIndicatorColor)
		}

		binding.toolbar.setBackgroundColor(appliedTopBarBackground)
		binding.elevatedAppBarLayout.setBackgroundColor(appliedTopBarBackground)
		binding.toolbar.setTitleTextColor(onSurfaceColor)
		binding.toolbar.setSubtitleTextColor(onSurfaceColor)
		binding.toolbar.navigationIcon?.setTint(onSurfaceColor)
		binding.toolbar.overflowIcon?.setTint(onSurfaceColor)

		if (isDefaultDarkTheme) {
			binding.efab.backgroundTintList = ColorStateList.valueOf(DefaultDarkColorScheme.primaryContainer.toArgb())
			binding.efab.setTextColor(DefaultDarkColorScheme.onPrimaryContainer.toArgb())
			binding.efab.iconTint = ColorStateList.valueOf(DefaultDarkColorScheme.onPrimaryContainer.toArgb())
		} else {
			val onPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary, 0)
			binding.efab.backgroundTintList = ColorStateList.valueOf(resolveThemePrimaryColor())
			binding.efab.setTextColor(onPrimary)
			binding.efab.iconTint = ColorStateList.valueOf(onPrimary)
		}

		if (BuildConfig.DEBUG && isDefaultDarkTheme) {
			logD(
				"Default dark theme snapshot -> " +
					"background=${resolveThemeColor(android.R.attr.colorBackground)}, " +
					"surface=${resolveThemeColor(com.google.android.material.R.attr.colorSurface)}, " +
					"primary=${resolveThemePrimaryColor()}, " +
					"onSurface=${resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)}, " +
					"outline=${resolveThemeColor(com.google.android.material.R.attr.colorOutline)}"
			)
		}
	}

	private data class CustomNavigationTheme(
		val topBarContainer: Int?,
		val bottomBarContainer: Int?,
		val bottomBarSelected: Int?,
	)

	private fun resolveCustomNavigationTheme(): CustomNavigationTheme {
		if (!appSettings.getBoolean(SettingKey.CustomThemeEnabled.name, false)) {
			return CustomNavigationTheme(null, null, null)
		}

		val topBarContainer = appSettings
			.getString(SettingKey.CustomThemeTopBarContainer.name, SettingKey.CustomThemeTopBarContainer.default)
			.orEmpty()
			.let(app.shosetsu.android.ui.settings.CustomThemeParser::parseColor)
			?.toArgb()

		val bottomBarContainer = appSettings
			.getString(SettingKey.CustomThemeBottomBarContainer.name, SettingKey.CustomThemeBottomBarContainer.default)
			.orEmpty()
			.let(app.shosetsu.android.ui.settings.CustomThemeParser::parseColor)
			?.toArgb()

		val bottomBarSelected = appSettings
			.getString(SettingKey.CustomThemeBottomBarSelected.name, SettingKey.CustomThemeBottomBarSelected.default)
			.orEmpty()
			.let(app.shosetsu.android.ui.settings.CustomThemeParser::parseColor)
			?.toArgb()
			?: appSettings
				.getString(SettingKey.CustomThemeNavSelected.name, SettingKey.CustomThemeNavSelected.default)
				.orEmpty()
				.let(app.shosetsu.android.ui.settings.CustomThemeParser::parseColor)
				?.toArgb()

		return CustomNavigationTheme(
			topBarContainer = topBarContainer,
			bottomBarContainer = bottomBarContainer,
			bottomBarSelected = bottomBarSelected,
		)
	}

	private fun resolveSystemBarsColor(): Color {
		val appThemeVariant = resolveThemeInt(R.attr.appThemeVariant)
		return if (isDefaultDarkTheme(appThemeVariant)) {
			DefaultDarkColorScheme.background
		} else {
			Color(resolveThemeColor(com.google.android.material.R.attr.colorSurface))
		}
	}

	private fun isDefaultDarkTheme(appThemeVariant: Int): Boolean {
		if (appThemeVariant == AppThemes.EMERALD_MANUSCRIPT.key || appThemeVariant == AppThemes.MIDNIGHT_INK_GOLD.key || appThemeVariant == AppThemes.LIGHT.key) {
			return false
		}

		if (activeTheme == AppThemes.DARK) return true

		return activeTheme == AppThemes.FOLLOW_SYSTEM &&
			(resources.configuration.uiMode and UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES
	}

	private data class NavigationThemePalette(
		val background: Int,
		val selected: Int,
		val unselected: Int,
		val indicator: Int,
		val onSurface: Int,
	)


	@ColorInt
	private fun resolveThemeColor(@AttrRes attr: Int): Int {
		val tv = TypedValue()
		val resolved = theme.resolveAttribute(attr, tv, true)
		check(resolved) { "Theme attribute not found: $attr" }
		return if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
	}

	@ColorInt
	private fun resolveThemePrimaryColor(): Int {
		val names = listOf("color" + "Primary", "colo" + "rAccent")
		val sources = listOf("android", packageName, "com.google.android.material")

		for (source in sources) {
			for (name in names) {
				val attrId = resources.getIdentifier(name, "attr", source)
				if (attrId != 0 && theme.resolveAttribute(attrId, TypedValue(), true)) {
					return resolveThemeColor(attrId)
				}
			}
		}

		error("Unable to resolve theme primary color")
	}

	private fun resolveThemeInt(@AttrRes attr: Int): Int {
		val typedValue = TypedValue()
		return if (theme.resolveAttribute(attr, typedValue, true)) typedValue.data else 0
	}

	private fun applyDynamicColorsIfNeeded(theme: AppThemes) {
		if (theme == AppThemes.EMERALD_MANUSCRIPT || theme == AppThemes.MIDNIGHT_INK_GOLD) return
		DynamicColors.applyToActivityIfAvailable(this)
	}

	private fun setupView() {
		//Sets the toolbar
		setSupportActionBar(binding.toolbar)

		binding.toolbar.setNavigationOnClickListener {
			logV("Navigation item clicked")
			if (navController.backQueue.size == 2) {
				if (viewModel.navigationStyle.value == LEGACY) {
					binding.drawerLayout.openDrawer(GravityCompat.START)
				} else onBackPressedDispatcher.onBackPressed()
			} else onBackPressedDispatcher.onBackPressed()
		}

		when (viewModel.navigationStyle.value) {
			MATERIAL -> {
				getMaterialNav().isVisible = true
				binding.navDrawer.isVisible = false
				setupMaterialNavigation()
			}
			LEGACY -> {
				getMaterialNav().isVisible = false
				binding.navDrawer.isVisible = true
				setupLegacyNavigation()
			}
		}
	}

	/**
	 * Setup the navigation drawer
	 */
	private fun setupLegacyNavigation() {
		supportActionBar?.setDisplayHomeAsUpEnabled(true)

		actionBarDrawerToggle = ActionBarDrawerToggle(
			this,
			binding.drawerLayout,
			binding.toolbar,
			R.string.navigation_drawer_open,
			R.string.navigation_drawer_close
		)

		actionBarDrawerToggle?.setToolbarNavigationClickListener {
			onBackPressedDispatcher.onBackPressed()
		}

		@Suppress("ReplaceNotNullAssertionWithElvisReturn")
		binding.drawerLayout.addDrawerListener(actionBarDrawerToggle!!)

		setupActionBarWithNavController(navController, binding.drawerLayout)
		binding.navDrawer.setupWithNavController(navController)
	}

	/**
	 * Setup the bottom navigation
	 */
	private fun setupMaterialNavigation() {
		binding.drawerLayout.setDrawerLockMode(
			DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
			binding.navDrawer
		)

		val materialNav = getMaterialNav()
		materialNav.setOnItemSelectedListener { item ->
			val destinationId = item.itemId
			if (navController.currentDestination?.id == destinationId) return@setOnItemSelectedListener true

			navController.navigate(destinationId, null, navOptions {
				popUpTo(navController.graph.startDestinationId) {
					saveState = true
				}
				launchSingleTop = true
				restoreState = true
				setShosetsuTransition()
			})
			true
		}

		materialDestinationListener?.let(navController::removeOnDestinationChangedListener)
		val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
			val topLevelDestinationId = destination.hierarchy
				.firstOrNull { it.id in materialTopLevelDestinations }
				?.id
			if (topLevelDestinationId != null && materialNav.selectedItemId != topLevelDestinationId) {
				materialNav.menu.findItem(topLevelDestinationId)?.isChecked = true
			}
		}
		materialDestinationListener = listener
		navController.addOnDestinationChangedListener(listener)
	}

	/**
	 * Listen to when fragment changes occur.
	 *
	 * This is used to to sync each fragment view and the activity view together.
	 */
	inner class FragmentLifecycleListener : FragmentManager.FragmentLifecycleCallbacks() {
		/**
		 * When called, sync the activity view
		 */
		override fun onFragmentViewCreated(
			fm: FragmentManager,
			f: Fragment,
			v: View,
			savedInstanceState: Bundle?
		) {
			logV("Fragment: ${f::class.simpleName}")
			syncActivityViewWithFragment(f)
		}
	}

	private fun setupNavigationController() {
		navChildFragmentManager.registerFragmentLifecycleCallbacks(
			FragmentLifecycleListener(),
			true
		)
		syncActivityViewWithFragment(navChildFragmentManager.fragments.lastOrNull())
	}

	internal fun handleIntentAction(intent: Intent) {
		logD("Intent received was ${intent.action}")
		when (intent.action) {
			ACTION_OPEN_CATALOGUE -> navController.navigate(
				R.id.browseController,
				null,
				navOptions { setShosetsuTransition() }
			)
			ACTION_OPEN_UPDATES -> navController.navigate(R.id.updatesController,
				null,
				navOptions { setShosetsuTransition() }
			)
			ACTION_OPEN_LIBRARY -> navController.navigate(R.id.libraryController,
				null,
				navOptions { setShosetsuTransition() }
			)
			ACTION_SEARCH -> {
				navController.navigate(
					R.id.searchController, bundleOf(
						BUNDLE_QUERY to (intent.getStringExtra(SearchManager.QUERY) ?: "")
					), navOptions { setShosetsuTransition() }

				)
			}
			ACTION_OPEN_SEARCH -> {
				navController.navigate(
					R.id.searchController, bundleOf(
						BUNDLE_QUERY to (intent.getStringExtra(SearchManager.QUERY) ?: "")
					),
					navOptions { setShosetsuTransition() }

				)
			}
			ACTION_OPEN_APP_UPDATE -> {
				handleAppUpdate()
			}
			ACTION_VIEW -> {
				if (intent.data != null) {
					if (intent.data!!.scheme != null) {
						launchIO {
							delay(100)

							launchUI {
								navController.navigate(
									R.id.action_libraryController_to_moreController,
									null,
									navOptions { setShosetsuTransition() }
								)
							}
							delay(100)
							launchUI {
								navController.navigate(
									R.id.action_moreController_to_addShareController,
									bundleOf(
										BUNDLE_URL to intent.data!!.scheme + "://" + intent.data!!.host
									)
								)
							}
						}
					} else {
						logE("Scheme was null")
					}
				} else {
					logE("View action data null")
				}
			}
			ACTION_MAIN -> {
			}
			else -> navController.navigate(R.id.libraryController,
				null,
				navOptions { setShosetsuTransition() }
			)
		}
	}

	private fun setupProcesses() {
		viewModel.startAppUpdateCheck().collectLA(this, catch = {
			makeSnackBar(
				getString(
					R.string.activity_main_error_update_check,
					it.message ?: "Unknown error"
				)
			).setAction(R.string.report) { _ ->
				ACRA.errorReporter.handleSilentException(it)
			}.show()
		}) { result ->
			if (result != null)
				AlertDialog.Builder(this).apply {
					setTitle(R.string.update_app_now_question)
					setMessage(
						"${result.version}\t${result.versionCode}\n" + result.notes.joinToString(
							"\n"
						)
					)
					setPositiveButton(R.string.update) { it, _ ->
						handleAppUpdate()
						it.dismiss()
					}
					setNegativeButton(R.string.update_not_interested) { it, _ ->
						it.dismiss()
					}
					setOnDismissListener { dialogInterface ->
						dialogInterface.dismiss()
					}
				}.let {
					launchUI { it.show() }
				}
		}
		viewModel.backupProgressState.collectLatestLA(this, catch = {
			logE("Backup failed", it)
			ACRA.errorReporter.handleException(it)
			binding.backupWarning.isVisible = false
		}) {
			when (it) {
				IBackupRepository.BackupProgress.IN_PROGRESS -> {
					binding.backupWarning.isVisible = true
				}
				IBackupRepository.BackupProgress.NOT_STARTED -> {
					binding.backupWarning.isVisible = false
				}
				IBackupRepository.BackupProgress.COMPLETE -> {
					binding.backupWarning.isVisible = false
				}
				IBackupRepository.BackupProgress.FAILURE -> {
					binding.backupWarning.isVisible = false
				}
			}
		}
	}

	private fun handleAppUpdate() {
		viewModel.handleAppUpdate().firstLa(this, catch = {
			makeSnackBar(
				getString(
					R.string.activity_main_error_handle_update,
					it.message ?: "Unknown error"
				)
			).setAction(R.string.report) { _ ->
				ACRA.errorReporter.handleSilentException(it)
			}.show()
		}) {
			if (it != null)
				when (it) {
					AMainViewModel.AppUpdateAction.SelfUpdate -> {
						makeSnackBar(R.string.activity_main_app_update_download)
					}
					is AMainViewModel.AppUpdateAction.UserUpdate -> {
						openInBrowser(it.updateURL, it.pkg)
					}
				}
		}
	}

	private val eFabMaintainer by lazy {
		object : ExtendedFABController.EFabMaintainer {
			override fun hide() {
				if (!isTablet)
					binding.efab.hide()
			}

			override fun show() {
				binding.efab.show()
			}

			override fun setOnClickListener(onClick: ((View) -> Unit)?) {
				binding.efab.setOnClickListener(onClick)
			}

			override fun shrink() {
				binding.efab.shrink()
			}

			override fun extend() {
				if (!isTablet)
					binding.efab.extend()
			}

			override fun setText(textRes: Int) {
				if (!isTablet)
					binding.efab.setText(textRes)
				ViewCompat.setTooltipText(binding.efab, getString(textRes))
			}

			override fun setIconResource(iconRes: Int) {
				binding.efab.setIconResource(iconRes)
			}

		}
	}

	/**
	 * Show navigation components
	 */
	private fun hideNavigation() {
		when (viewModel.navigationStyle.value) {
			LEGACY -> {
				logI("Sync activity view with controller for legacy")
				actionBarDrawerToggle?.isDrawerIndicatorEnabled = false
				binding.drawerLayout.setDrawerLockMode(
					DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
					binding.navDrawer
				)
			}
			MATERIAL -> {
				supportActionBar?.setDisplayHomeAsUpEnabled(true)
				if (!isTablet)
					binding.navBottom.isVisible = false
			}
		}
	}

	/**
	 * Hide navigation components
	 */
	private fun showNavigation() {
		when (viewModel.navigationStyle.value) {
			LEGACY -> {
				logI("Sync activity view with controller for legacy")
				actionBarDrawerToggle?.isDrawerIndicatorEnabled = true
				binding.drawerLayout.setDrawerLockMode(
					DrawerLayout.LOCK_MODE_UNLOCKED,
					binding.navDrawer
				)
			}
			MATERIAL -> {
				supportActionBar?.setDisplayHomeAsUpEnabled(false)
				if (!isTablet) {
					binding.navBottom.translationY = 0f
					binding.navBottom.isVisible = true
				}
			}
		}
	}

	@SuppressLint("ObjectAnimatorBinding")
	internal fun syncActivityViewWithFragment(to: Fragment?) {
		// Ignore dialog fragments and material calendars, they are meant to be dialog like.
		if (to is DialogFragment || to is MaterialCalendar<*>) {
			return
		}

		binding.elevatedAppBarLayout.setExpanded(true)

		// Show hamburg means this is home
		if (to is HomeFragment) {
			showNavigation()
		} else hideNavigation()

		Log.d(logID(), "Resetting FAB listeners")

		eFabMaintainer.hide()
		binding.efab.text = null
		eFabMaintainer.setOnClickListener(null)
		binding.navRail.headerView?.isVisible = false

		if (to is ExtendedFABController) {
			to.manipulateFAB(eFabMaintainer)
			to.showFAB(eFabMaintainer)
			binding.navRail.headerView?.isVisible = true
		}

		// Change the elevation for the app bar layout
		when (to) {
			is CollapsedToolBarController -> {
				binding.elevatedAppBarLayout.drop()
			}
			is LiftOnScrollToolBarController -> {
				binding.elevatedAppBarLayout.elevate(true)
			}
			else -> {
				binding.elevatedAppBarLayout.elevate(false)
			}
		}
	}

	/**
	 * Make a snack bar
	 *
	 * @param stringRes String resource id
	 * @param length Length of the snack
	 */
	fun makeSnackBar(
		@StringRes stringRes: Int,
		@Duration length: Int = Snackbar.LENGTH_SHORT
	): Snackbar =
		makeSnackBar(getString(stringRes), length)

	/**
	 * Make a snack bar
	 *
	 * @param string Content of snack
	 * @param length Length of the snack
	 */
	fun makeSnackBar(
		string: String,
		@Duration length: Int = Snackbar.LENGTH_SHORT
	): Snackbar =
		Snackbar.make(binding.coordinator, string, length).apply {
			when {
				binding.efab.isVisible -> anchorView = binding.efab
				binding.navBottom.isVisible -> anchorView = binding.navBottom
			}
		}
}

fun Context.resolveThemeColor(@AttrRes attr: Int, @ColorInt fallback: Int = AndroidColor.MAGENTA): Int {
	val typedValue = TypedValue()
	if (!theme.resolveAttribute(attr, typedValue, true)) return fallback

	return when {
		typedValue.resourceId != 0 -> runCatching { getColor(typedValue.resourceId) }.getOrElse { fallback }
		typedValue.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> typedValue.data
		else -> fallback
	}
}
