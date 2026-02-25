package app.shosetsu.android.data.connector

import app.shosetsu.android.domain.model.Source
import app.shosetsu.android.domain.model.SourceType

class ConnectorRegistry(
    private val api: ApiSourceConnector,
    private val scrape: ScrapeSourceConnector
) {
    fun forSource(source: Source): SourceConnector = when (source.type) {
        SourceType.Api -> api
        SourceType.GenericWeb -> scrape
    }
}
