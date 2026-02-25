package app.shosetsu.android.domain.repository.impl

import android.database.sqlite.SQLiteException
import app.shosetsu.android.common.ext.onIO
import app.shosetsu.android.datasource.local.database.base.IDBExtRepoDataSource
import app.shosetsu.android.datasource.local.database.base.IDBInstalledExtensionsDataSource
import app.shosetsu.android.datasource.local.database.base.IDBRepositoryExtensionsDataSource
import app.shosetsu.android.datasource.remote.base.IRemoteExtensionDataSource
import app.shosetsu.android.domain.model.local.*
import app.shosetsu.android.domain.repository.base.IExtensionsRepository
import app.shosetsu.lib.exceptions.HTTPException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/*
 * This file is part of shosetsu.
 *
 * shosetsu is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * shosetsu is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with shosetsu.  If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * shosetsu
 * 24 / 04 / 2020
 *
 * @author github.com/doomsdayrs
 */
class ExtensionsRepository(
	private val installedDBSource: IDBInstalledExtensionsDataSource,
	private val repoDBSource: IDBRepositoryExtensionsDataSource,
	private val remoteSource: IRemoteExtensionDataSource,
	private val _repoDBSource: IDBExtRepoDataSource
) : IExtensionsRepository {
	@Throws(SQLiteException::class)
	@OptIn(ExperimentalCoroutinesApi::class)
	override fun loadBrowseExtensions(): Flow<List<BrowseExtensionEntity>> {
		return combine(repoDBSource.loadExtensionsFlow(), installedDBSource.loadExtensionsFlow()) { repoList, installedList ->
			val installedById = installedList.associateBy { it.id }
			val groupedById = repoList.groupBy { it.id }.toMutableMap()
			installedList.forEach { installed ->
				if (!groupedById.containsKey(installed.id)) {
					groupedById[installed.id] = emptyList()
				}
			}

			groupedById.entries.map { (extId, matchingExtensions) ->
				val installedExt = installedById[extId]
				val firstExt = matchingExtensions.firstOrNull()
				BrowseExtensionEntity(
					id = extId,
					name = installedExt?.name ?: firstExt?.name.orEmpty(),
					imageURL = installedExt?.imageURL ?: firstExt?.imageURL.orEmpty(),
					lang = installedExt?.lang ?: firstExt?.lang.orEmpty(),
					installOptions = if (installedExt == null) {
						matchingExtensions.mapNotNull { genericExt ->
							val repo = _repoDBSource.loadRepository(genericExt.repoID)
							if (repo != null && repo.isEnabled) {
								ExtensionInstallOptionEntity(genericExt.repoID, repo.name, genericExt.version)
							} else null
						}.sortedBy { it.repoId }.sortedBy { it.version }
					} else null,
					isInstalled = installedExt != null,
					installedVersion = installedExt?.version,
					installedRepo = installedExt?.repoID ?: -1,
					isUpdateAvailable = if (installedExt != null) {
						val repoVersion = matchingExtensions.find { it.repoID == installedExt.repoID }?.version
						repoVersion?.let { installedExt.version < it } ?: false
					} else false,
					updateVersion = matchingExtensions.find { it.repoID == installedExt?.repoID }?.version,
					isInstalling = false,
				)
			}
		}.distinctUntilChanged().onIO()
	}

	override fun loadExtensionsFLow(): Flow<List<InstalledExtensionEntity>> =
		installedDBSource.loadExtensionsFlow().onIO()

	override fun getInstalledExtensionFlow(id: Int): Flow<InstalledExtensionEntity?> =
		installedDBSource.loadExtensionLive(id).onIO()

	@Throws(SQLiteException::class)
	override suspend fun getExtension(repoId: Int, extId: Int): GenericExtensionEntity? =
		onIO { repoDBSource.loadExtension(repoId, extId) }

	@Throws(SQLiteException::class)
	override suspend fun getInstalledExtension(id: Int): InstalledExtensionEntity? =
		onIO { installedDBSource.loadExtension(id) }

	@Throws(SQLiteException::class)
	override suspend fun getRepositoryExtensions(repoID: Int): List<GenericExtensionEntity> =
		onIO { repoDBSource.getExtensions(repoID) }

	@Throws(SQLiteException::class)
	override suspend fun loadRepositoryExtensions(): List<GenericExtensionEntity> =
		onIO { repoDBSource.loadExtensions() }

	@Throws(SQLiteException::class)
	override suspend fun uninstall(extensionEntity: InstalledExtensionEntity) {
		onIO { installedDBSource.deleteExtension(extensionEntity) }
	}

	@Throws(SQLiteException::class)
	override suspend fun updateRepositoryExtension(extensionEntity: GenericExtensionEntity) {
		onIO { repoDBSource.updateExtension(extensionEntity) }
	}

	@Throws(SQLiteException::class)
	override suspend fun updateInstalledExtension(extensionEntity: InstalledExtensionEntity) {
		onIO { installedDBSource.updateExtension(extensionEntity) }
	}

	@Throws(SQLiteException::class)
	override suspend fun delete(extensionEntity: GenericExtensionEntity) {
		onIO {
			installedDBSource.loadExtension(extensionEntity.id)?.let {
				installedDBSource.deleteExtension(it)
			}

			repoDBSource.deleteExtension(extensionEntity)
		}
	}

	@Throws(SQLiteException::class)
	override suspend fun insert(extensionEntity: GenericExtensionEntity): Long =
		onIO { repoDBSource.insert(extensionEntity) }

	@Throws(SQLiteException::class)
	override suspend fun insert(extensionEntity: InstalledExtensionEntity): Long =
		onIO { installedDBSource.insert(extensionEntity) }

	@Throws(
		HTTPException::class,
		SocketTimeoutException::class,
		UnknownHostException::class,
	)
	override suspend fun downloadExtension(
		repositoryEntity: RepositoryEntity,
		extension: GenericExtensionEntity
	): ByteArray =
		onIO { remoteSource.downloadExtension(repositoryEntity, extension) }

	@Throws(SQLiteException::class)
	override suspend fun isExtensionInstalled(extensionEntity: GenericExtensionEntity): Boolean =
		onIO { installedDBSource.loadExtension(extensionEntity.id) != null }
}