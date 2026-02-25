package app.shosetsu.android.ui.more

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import app.shosetsu.android.R
import app.shosetsu.android.common.ext.ComposeView
import app.shosetsu.android.common.ext.makeSnackBar
import app.shosetsu.android.common.ext.navigateSafely
import app.shosetsu.android.common.ext.setShosetsuTransition
import app.shosetsu.android.view.compose.ShosetsuCompose
import app.shosetsu.android.view.controller.ShosetsuFragment
import app.shosetsu.android.view.controller.base.CollapsedToolBarController
import app.shosetsu.android.view.controller.base.HomeFragment

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
 * shosetsu
 * 12 / 09 / 2020
 *
 * Option for download queue
 */
class MoreFragment
	: ShosetsuFragment(), CollapsedToolBarController, HomeFragment {

	override val viewTitleRes: Int = R.string.more

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedViewState: Bundle?
	): View {
		setViewTitle()
		return ComposeView {
			MoreView(
				makeSnackBar = {
					makeSnackBar(it)?.show()
				},
				navigateSafely = { id, options ->
					findNavController().navigateSafely(id, null, options)
				}
			)
		}
	}
}

@Composable
fun MoreView(
	makeSnackBar: (Int) -> Unit,
	navigateSafely: (Int, NavOptions) -> Unit
) {
	ShosetsuCompose {
		MoreContent(
			showStyleBar = {
				navigateSafely(R.id.action_moreController_to_stylesFragment, navOptions { launchSingleTop = true; setShosetsuTransition() })
			}
		) { it, singleTop ->
			navigateSafely(
				it,
				navOptions {
					launchSingleTop = singleTop
					setShosetsuTransition()
				}
			)
		}
	}
}

@Composable
fun MoreItemContent(
	@StringRes title: Int,
	@DrawableRes drawableRes: Int,
	onClick: () -> Unit
) {
	Box(
		modifier = Modifier
			.clickable(onClick = onClick)
			.fillMaxWidth(),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically
		) {
			Icon(
				painterResource(drawableRes),
				null,
				modifier = Modifier
					.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 24.dp)
					.size(24.dp),
				tint = MaterialTheme.colorScheme.primary
			)
			Text(stringResource(title), color = MaterialTheme.colorScheme.onSurface)
		}
	}
}

@Preview
@Composable
fun PreviewMoreContent() {
	MoreContent({}) { _, _ -> }
}

@Composable
fun MoreContent(
	showStyleBar: () -> Unit,
	pushController: (Int, singleTop: Boolean) -> Unit
) {
	Surface(color = MaterialTheme.colorScheme.background) {
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(bottom = 80.dp)
		) {
			item {
				BoxWithConstraints(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 12.dp, bottom = 8.dp),
					contentAlignment = Alignment.Center,
				) {
					val showIcon = maxWidth >= 320.dp
					Column(
						modifier = Modifier.fillMaxWidth(),
						horizontalAlignment = Alignment.CenterHorizontally,
					) {
						if (showIcon) {
							Image(
								painter = painterResource(R.drawable.monogatari_icon),
								contentDescription = stringResource(R.string.app_name),
								modifier = Modifier.size(120.dp),
								contentScale = ContentScale.Fit,
							)
						}
						Image(
							painter = painterResource(R.drawable.monogatari_wordmark),
							contentDescription = stringResource(R.string.app_name),
							modifier = Modifier
								.padding(top = if (showIcon) 10.dp else 0.dp)
								.width(250.dp),
							contentScale = ContentScale.Fit
						)
					}
				}
			}
			item {
				Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
			}
			item {
				MoreItemContent(R.string.downloads, R.drawable.download) {
					pushController(R.id.action_moreController_to_downloadsController, true)
				}
			}

			item {
				MoreItemContent(R.string.backup, R.drawable.restore) {
					pushController(R.id.action_moreController_to_backupSettings, true)
				}
			}

			item {
				MoreItemContent(R.string.repositories, R.drawable.add_shopping_cart) {
					pushController(R.id.action_moreController_to_repositoryController, true)
				}
			}

			item {
				MoreItemContent(R.string.add_extension, R.drawable.add_circle_outline) {
					pushController(R.id.action_moreController_to_addExtensionWizardController, true)
				}
			}

			item {
				MoreItemContent(R.string.categories, R.drawable.ic_baseline_label_24) {
					pushController(R.id.action_moreController_to_categoriesController, true)
				}
			}

			item {
				MoreItemContent(R.string.styles, R.drawable.ic_baseline_style_24) {
					showStyleBar()
				}
			}

			item {
				MoreItemContent(R.string.qr_code_scan, R.drawable.ic_baseline_link_24) {
					pushController(R.id.action_moreController_to_addShareController, true)
				}
			}

			item {
				MoreItemContent(
					R.string.fragment_more_dest_analytics,
					R.drawable.baseline_analytics_24
				) {
					pushController(R.id.action_moreController_to_analyticsFragment, true)
				}
			}

			item {
				MoreItemContent(
					R.string.fragment_more_dest_history,
					R.drawable.baseline_history_edu_24
				) {
					pushController(R.id.action_moreController_to_historyFragment, true)
				}
			}

			item {
				MoreItemContent(R.string.settings, R.drawable.settings) {
					pushController(R.id.action_moreController_to_settingsController, false)
				}
			}

			item {
				MoreItemContent(R.string.about, R.drawable.info_outline) {
					pushController(R.id.action_moreController_to_aboutController, false)
				}
			}
		}
	}
}
