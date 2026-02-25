package app.shosetsu.android.ui.common

import androidx.paging.LoadState

fun shouldShowEmptyState(refreshState: LoadState, itemCount: Int): Boolean =
	refreshState is LoadState.NotLoading && itemCount == 0
