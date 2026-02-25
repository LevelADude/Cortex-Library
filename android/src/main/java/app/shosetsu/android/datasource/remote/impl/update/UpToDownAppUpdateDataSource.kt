package app.shosetsu.android.datasource.remote.impl.update

import android.annotation.SuppressLint
import app.shosetsu.android.common.EmptyResponseBodyException
import app.shosetsu.android.common.ext.quickie
import app.shosetsu.android.common.utils.archURL
import app.shosetsu.android.datasource.remote.base.IRemoteAppUpdateDataSource
import app.shosetsu.android.domain.model.local.AppUpdateEntity
import app.shosetsu.lib.exceptions.HTTPException
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.io.IOException
import java.io.InputStream

class UpToDownAppUpdateDataSource(
	private val okHttpClient: OkHttpClient
) : IRemoteAppUpdateDataSource,
	IRemoteAppUpdateDataSource.Downloadable {
	companion object {
		const val UPTODOWN_SHOSETSU_PAGE = "https://shosetsu.en.uptodown.com/android"
		const val UPTODOWN_SHOSETSU_DOWNLOAD = "$UPTODOWN_SHOSETSU_PAGE/download"
	}

	@SuppressLint("StopShip")
	@Throws(HTTPException::class, IOException::class)
	override suspend fun loadAppUpdate(): AppUpdateEntity {
		val str = okHttpClient.quickie(UPTODOWN_SHOSETSU_PAGE).body!!.string()
		val document = Jsoup.parse(str)
		val version = document.selectFirst("p.version")!!.text()

		return AppUpdateEntity(
			version,
			url = UPTODOWN_SHOSETSU_DOWNLOAD,
			notes = emptyList()
		)
	}

	@SuppressLint("StopShip")
	@Throws(HTTPException::class, IOException::class)
	override suspend fun downloadAppUpdate(update: AppUpdateEntity): InputStream {
		val str = okHttpClient.quickie(UPTODOWN_SHOSETSU_DOWNLOAD).body!!.string()
		val document = Jsoup.parse(str)
		val url = document
			.selectFirst("button.button.download")!!
			.attr("data-url")

		okHttpClient.quickie(url).let { response ->
			if (response.isSuccessful) {
				return response.body?.byteStream()
					?: throw EmptyResponseBodyException(update.archURL())
			} else throw HTTPException(response.code)
		}
	}
}