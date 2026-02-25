package app.shosetsu.android.data.connector

import android.util.Xml
import app.shosetsu.android.data.network.CortexHttpClient
import app.shosetsu.android.domain.config.ApiSourceConfig
import app.shosetsu.android.domain.config.SourceConfigCodec
import app.shosetsu.android.domain.model.ContentType
import app.shosetsu.android.domain.model.SearchResult
import app.shosetsu.android.domain.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.UUID

class ApiSourceConnector : SourceConnector {
    private val sourceLocks = mutableMapOf<String, Mutex>()
    private val lastCallBySource = mutableMapOf<String, Long>()

    override suspend fun search(source: Source, query: String, limit: Int): List<SearchResult> {
        val config = SourceConfigCodec.parseApi(source)
            ?: defaultApiConfigFor(source)
            ?: return emptyList()

        throttleSource(source.id)

        return when {
            isPmc(source) -> searchPmc(source, query, limit)
            else -> {
                val url = buildUrl(source, config, query, limit) ?: return emptyList()
                val requestBuilder = Request.Builder().url(url)
                if (!config.headerApiKeyName.isNullOrBlank() && !config.apiKey.isNullOrBlank()) {
                    requestBuilder.header(config.headerApiKeyName, config.apiKey)
                }
                val body = fetchBody(requestBuilder.build())
                parseBySource(source, body)
            }
        }
    }

    private suspend fun searchPmc(source: Source, query: String, limit: Int): List<SearchResult> {
        val searchUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?db=pmc&term=${java.net.URLEncoder.encode(query, Charsets.UTF_8.name())}&retmode=json&retmax=$limit"
        val esearchBody = fetchBody(Request.Builder().url(searchUrl).build())
        val ids = parsePmcIds(esearchBody)
        if (ids.isEmpty()) return emptyList()

        val summaryUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=pmc&id=${ids.joinToString(",")}&retmode=json"
        val summaryBody = fetchBody(Request.Builder().url(summaryUrl).build())
        return parsePmcResults(summaryBody, source.id)
    }

    private suspend fun fetchBody(request: Request): String = withContext(Dispatchers.IO) {
        CortexHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    private suspend fun throttleSource(sourceId: String) {
        val lock = sourceLocks.getOrPut(sourceId) { Mutex() }
        lock.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - (lastCallBySource[sourceId] ?: 0L)
            val minIntervalMs = 300L
            if (elapsed in 0 until minIntervalMs) delay(minIntervalMs - elapsed)
            lastCallBySource[sourceId] = System.currentTimeMillis()
        }
    }

    private fun buildUrl(source: Source, config: ApiSourceConfig, query: String, limit: Int): String? {
        val base = source.baseUrl.toHttpUrlOrNull() ?: return null
        val endpoint = config.endpointPath.trim().removePrefix("/")
        val builder = base.newBuilder().apply {
            if (endpoint.isNotBlank()) addPathSegments(endpoint)
        }

        when {
            isArxiv(source) -> {
                builder.addQueryParameter(config.queryParam, "all:$query")
                builder.addQueryParameter("start", "0")
                builder.addQueryParameter("max_results", limit.toString())
            }
            else -> {
                builder.addQueryParameter(config.queryParam, query)
                config.extraParams.forEach { (k, v) -> builder.addQueryParameter(k, v) }
                builder.addQueryParameter(config.limitParam, limit.toString())
            }
        }

        return builder.build().toString()
    }

    private fun defaultApiConfigFor(source: Source): ApiSourceConfig? = when {
        isOpenAlex(source) -> ApiSourceConfig(endpointPath = "/works", queryParam = "search")
        isArxiv(source) -> ApiSourceConfig(endpointPath = "/api/query", queryParam = "search_query", limitParam = "max_results")
        isOpenLibrary(source) -> ApiSourceConfig(endpointPath = "/search.json", queryParam = "q", limitParam = "limit")
        isDoab(source) -> ApiSourceConfig(endpointPath = "/rest/search", queryParam = "query", limitParam = "pageSize")
        isPmc(source) -> ApiSourceConfig(endpointPath = "")
        else -> null
    }

    private fun parseBySource(source: Source, body: String): List<SearchResult> = when {
        isArxiv(source) -> parseArxivResults(body, source.id)
        isOpenAlex(source) -> parseOpenAlexResults(body, source.id)
        isOpenLibrary(source) -> parseOpenLibraryResults(body, source.id)
        isDoab(source) -> parseDoabResults(body, source.id)
        else -> emptyList()
    }

    companion object {
        private fun isOpenAlex(source: Source): Boolean =
            source.id.contains("open_alex", ignoreCase = true) || source.name.contains("openalex", ignoreCase = true)

        private fun isArxiv(source: Source): Boolean =
            source.id.contains("arxiv", ignoreCase = true) || source.name.contains("arxiv", ignoreCase = true)

        private fun isOpenLibrary(source: Source): Boolean =
            source.id.contains("open_library", ignoreCase = true) || source.name.contains("open library", ignoreCase = true)

        private fun isPmc(source: Source): Boolean =
            source.id.contains("pmc", ignoreCase = true) || source.name.contains("pubmed central", ignoreCase = true)

        private fun isDoab(source: Source): Boolean =
            source.id.contains("doab", ignoreCase = true) || source.name.contains("doab", ignoreCase = true)

        fun parseOpenAlexResults(jsonBody: String, sourceId: String): List<SearchResult> {
            val root = runCatching { SourceConfigCodec.json.parseToJsonElement(jsonBody).jsonObject }.getOrNull() ?: return emptyList()
            val results = root["results"] as? JsonArray ?: return emptyList()
            return results.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val title = obj["display_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val year = obj["publication_year"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val authors = extractAuthors(obj["authorships"])
                val primaryLanding = obj["primary_location"]?.jsonObject?.get("landing_page_url")?.jsonPrimitive?.contentOrNull
                val openAlexId = obj["id"]?.jsonPrimitive?.contentOrNull
                val pdfFromPrimary = obj["primary_location"]?.jsonObject?.get("pdf_url")?.jsonPrimitive?.contentOrNull
                val bestOa = obj["best_oa_location"]?.jsonObject?.get("pdf_url")?.jsonPrimitive?.contentOrNull
                val doi = obj["doi"]?.jsonPrimitive?.contentOrNull?.removePrefix("https://doi.org/")
                SearchResult(
                    id = UUID.randomUUID().toString(),
                    sourceId = sourceId,
                    title = title,
                    authors = authors,
                    year = year,
                    snippet = obj["abstract_inverted_index"]?.let { "Abstract available" } ?: "No abstract snippet",
                    pdfUrl = pdfFromPrimary ?: bestOa,
                    landingUrl = primaryLanding ?: openAlexId,
                    contentType = ContentType.Paper,
                    publisherOrVenue = obj["primary_location"]?.jsonObject?.get("source")?.jsonObject?.get("display_name")?.jsonPrimitive?.contentOrNull,
                    identifiers = doi?.let { mapOf("DOI" to it) },
                    openAccess = obj["open_access"]?.jsonObject?.get("is_oa")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull(),
                    language = obj["language"]?.jsonPrimitive?.contentOrNull
                )
            }
        }

        fun parseArxivResults(xmlBody: String, sourceId: String): List<SearchResult> {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(StringReader(xmlBody))
            }
            val results = mutableListOf<SearchResult>()
            var event = parser.eventType
            var entry: MutableMap<String, String>? = null
            var authors = mutableListOf<String>()
            var inAuthor = false

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "entry" -> {
                                entry = mutableMapOf()
                                authors = mutableListOf()
                            }
                            "author" -> inAuthor = true
                            "name" -> if (inAuthor) parser.nextText().trim().takeIf { it.isNotBlank() }?.let(authors::add)
                            "title" -> entry?.put("title", parser.nextText().trim().replace("\n", " "))
                            "summary" -> entry?.put("summary", parser.nextText().trim().replace("\n", " "))
                            "published" -> entry?.put("published", parser.nextText().trim())
                            "id" -> entry?.put("id", parser.nextText().trim())
                            "link" -> {
                                val title = parser.getAttributeValue(null, "title")
                                val href = parser.getAttributeValue(null, "href")
                                val rel = parser.getAttributeValue(null, "rel")
                                if (title.equals("pdf", ignoreCase = true) || rel.equals("related", ignoreCase = true) && href?.contains("/pdf/") == true) {
                                    entry?.put("pdf", href)
                                }
                                if (rel.equals("alternate", ignoreCase = true)) {
                                    entry?.put("landing", href)
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (parser.name == "author") inAuthor = false
                        if (parser.name == "entry") {
                            val current = entry
                            if (current != null) {
                                val title = current["title"].orEmpty()
                                if (title.isNotBlank()) {
                                    val landing = current["landing"] ?: current["id"]
                                    val pdf = current["pdf"] ?: current["id"]?.let(::toArxivPdfFromId)
                                    val arxivId = current["id"]?.substringAfterLast("/")?.substringBefore('v')
                                    results += SearchResult(
                                        id = UUID.randomUUID().toString(),
                                        sourceId = sourceId,
                                        title = title,
                                        authors = authors.joinToString(", "),
                                        year = current["published"]?.take(4).orEmpty(),
                                        snippet = current["summary"] ?: "",
                                        landingUrl = landing,
                                        pdfUrl = pdf,
                                        contentType = ContentType.Paper,
                                        identifiers = arxivId?.let { mapOf("arXivId" to it) },
                                        openAccess = true
                                    )
                                }
                            }
                            entry = null
                            authors = mutableListOf()
                        }
                    }
                }
                event = parser.next()
            }
            return results
        }

        private fun toArxivPdfFromId(arxivIdUrl: String): String? {
            val id = arxivIdUrl.substringAfterLast('/').substringBefore('v').takeIf { it.isNotBlank() } ?: return null
            return "https://arxiv.org/pdf/$id.pdf"
        }

        fun parseOpenLibraryResults(jsonBody: String, sourceId: String): List<SearchResult> {
            // TODO: If authenticated user support is added, evaluate OpenLibrary/Archive borrow flows.
            val root = runCatching { SourceConfigCodec.json.parseToJsonElement(jsonBody).jsonObject }.getOrNull() ?: return emptyList()
            val docs = root["docs"] as? JsonArray ?: return emptyList()
            return docs.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val key = obj["key"]?.jsonPrimitive?.contentOrNull
                val landing = key?.let { "https://openlibrary.org$it" }
                val year = obj["first_publish_year"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val authors = (obj["author_name"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }?.joinToString(", ").orEmpty()
                val identifiers = mutableMapOf<String, String>()
                (obj["isbn"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull?.let { identifiers["ISBN"] = it }
                (obj["oclc"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull?.let { identifiers["OCLC"] = it }
                SearchResult(
                    id = UUID.randomUUID().toString(),
                    sourceId = sourceId,
                    title = title,
                    authors = authors,
                    year = year,
                    snippet = "Open Library result",
                    landingUrl = landing,
                    pdfUrl = null,
                    contentType = ContentType.Book,
                    publisherOrVenue = obj["publisher"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull,
                    identifiers = identifiers.takeIf { it.isNotEmpty() },
                    language = obj["language"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
                )
            }
        }

        fun parsePmcIds(jsonBody: String): List<String> {
            val root = runCatching { SourceConfigCodec.json.parseToJsonElement(jsonBody).jsonObject }.getOrNull() ?: return emptyList()
            return root["esearchresult"]?.jsonObject?.get("idlist")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        }

        fun parsePmcResults(jsonBody: String, sourceId: String): List<SearchResult> {
            val root = runCatching { SourceConfigCodec.json.parseToJsonElement(jsonBody).jsonObject }.getOrNull() ?: return emptyList()
            val result = root["result"]?.jsonObject ?: return emptyList()
            val ids = result["uids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: return emptyList()
            return ids.mapNotNull { uid ->
                val obj = result[uid]?.jsonObject ?: return@mapNotNull null
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val pmcid = obj["pmcid"]?.jsonPrimitive?.contentOrNull
                val authors = obj["authors"]?.jsonArray?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }?.joinToString(", ").orEmpty()
                val year = obj["pubdate"]?.jsonPrimitive?.contentOrNull?.take(4).orEmpty()
                SearchResult(
                    id = UUID.randomUUID().toString(),
                    sourceId = sourceId,
                    title = title,
                    authors = authors,
                    year = year,
                    snippet = obj["elocationid"]?.jsonPrimitive?.contentOrNull ?: "PubMed Central result",
                    landingUrl = pmcid?.let { "https://pmc.ncbi.nlm.nih.gov/articles/$it/" },
                    pdfUrl = pmcid?.let { "https://pmc.ncbi.nlm.nih.gov/articles/$it/pdf/" }, // TODO: validate edge cases for every PMC article variant.
                    contentType = ContentType.Paper,
                    publisherOrVenue = obj["fulljournalname"]?.jsonPrimitive?.contentOrNull,
                    identifiers = buildMap {
                        pmcid?.let { put("PMCID", it) }
                        obj["articleids"]?.jsonArray
                            ?.mapNotNull { it.jsonObject }
                            ?.forEach { idObj ->
                                val idType = idObj["idtype"]?.jsonPrimitive?.contentOrNull
                                val value = idObj["value"]?.jsonPrimitive?.contentOrNull
                                if (!idType.isNullOrBlank() && !value.isNullOrBlank()) put(idType.uppercase(), value)
                            }
                    }.takeIf { it.isNotEmpty() },
                    openAccess = true
                )
            }
        }

        fun parseDoabResults(jsonBody: String, sourceId: String): List<SearchResult> {
            val element = runCatching { SourceConfigCodec.json.parseToJsonElement(jsonBody) }.getOrNull() ?: return emptyList()
            val candidates = when (element) {
                is JsonArray -> element
                is JsonObject -> {
                    (element["results"] as? JsonArray)
                        ?: (element["items"] as? JsonArray)
                        ?: (element["data"] as? JsonArray)
                        ?: JsonArray(emptyList())
                }
                else -> JsonArray(emptyList())
            }

            return candidates.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val title = obj["title"]?.jsonPrimitive?.contentOrNull
                    ?: obj["name"]?.jsonPrimitive?.contentOrNull
                    ?: return@mapNotNull null
                val pdfUrl = obj.findNestedString("pdf")
                    ?: obj.findNestedString("pdf_url")
                    ?: obj.findNestedString("fulltext")
                val landing = obj.findNestedString("url")
                    ?: obj.findNestedString("landing_page")
                    ?: obj.findNestedString("link")
                SearchResult(
                    id = UUID.randomUUID().toString(),
                    sourceId = sourceId,
                    title = title,
                    authors = obj.findNestedString("author") ?: obj.findNestedString("authors") ?: "",
                    year = obj.findNestedString("year") ?: "",
                    snippet = obj.findNestedString("description") ?: "DOAB result",
                    pdfUrl = pdfUrl,
                    landingUrl = landing,
                    contentType = ContentType.Book,
                    publisherOrVenue = obj.findNestedString("publisher"),
                    identifiers = buildMap {
                        obj.findNestedString("isbn")?.let { put("ISBN", it) }
                        obj.findNestedString("doi")?.let { put("DOI", it.removePrefix("https://doi.org/")) }
                    }.takeIf { it.isNotEmpty() },
                    openAccess = true,
                    language = obj.findNestedString("language")
                )
            }
        }

        private fun JsonObject.findNestedString(key: String): String? {
            this[key]?.jsonPrimitive?.contentOrNull?.let { return it }
            values.forEach { value ->
                when (value) {
                    is JsonObject -> value.findNestedString(key)?.let { return it }
                    is JsonArray -> value.forEach { element ->
                        if (element is JsonObject) {
                            element.findNestedString(key)?.let { return it }
                        }
                    }
                }
            }
            return null
        }

        private fun extractAuthors(authorships: JsonElement?): String {
            val array = authorships as? JsonArray ?: return ""
            return array.mapNotNull { auth ->
                (auth as? JsonObject)
                    ?.get("author")
                    ?.jsonObject
                    ?.get("display_name")
                    ?.jsonPrimitive
                    ?.contentOrNull
            }.joinToString(", ")
        }
    }
}
