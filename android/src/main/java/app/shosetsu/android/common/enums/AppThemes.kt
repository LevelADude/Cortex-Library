package app.shosetsu.android.common.enums

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
 * 22 / 11 / 2020
 */
enum class AppThemes(val key: Int) {
	LIGHT(1),
	DARK(2),
	FOLLOW_SYSTEM(3),
	EMERALD_MANUSCRIPT(4),
	MIDNIGHT_INK(5),
	@Deprecated("Use MIDNIGHT_INK")
	MIDNIGHT_INK_GOLD(5);

	companion object {
		fun fromKey(key: Int): AppThemes = when (key) {
			0 -> EMERALD_MANUSCRIPT // Legacy theme id migration path.
			else -> values().find { it.key == key } ?: FOLLOW_SYSTEM
		}

		val selectionOrder = listOf(
			FOLLOW_SYSTEM,
			LIGHT,
			DARK,
			EMERALD_MANUSCRIPT,
			MIDNIGHT_INK,
		)
	}
}
