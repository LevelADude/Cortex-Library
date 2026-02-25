package app.shosetsu.android.ui.common

import androidx.paging.LoadState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultEmptyStateDeciderTest {

	@Test
	fun `shows empty state only when refresh completed with zero items`() {
		assertTrue(shouldShowEmptyState(LoadState.NotLoading(endOfPaginationReached = true), 0))
		assertFalse(shouldShowEmptyState(LoadState.Loading, 0))
		assertFalse(shouldShowEmptyState(LoadState.NotLoading(endOfPaginationReached = true), 2))
		assertFalse(
			shouldShowEmptyState(
				LoadState.Error(IllegalStateException("Runtime failure")),
				0
			)
		)
	}
}
