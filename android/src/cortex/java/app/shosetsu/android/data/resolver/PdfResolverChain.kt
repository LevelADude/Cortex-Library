package app.shosetsu.android.data.resolver

import app.shosetsu.android.data.network.CortexHttpClient
import app.shosetsu.android.data.repo.DebugEventsRepository
import app.shosetsu.android.data.store.CortexDataStore
import app.shosetsu.android.domain.config.SourceConfigCodec
import app.shosetsu.android.domain.model.DebugLevel
import app.shosetsu.android.domain.model.SearchResult
import app.shosetsu.android.domain.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.net.URI

class PdfResolverChain(
    private val resolvers: List<PdfResolver>,
    private val debugEventsRepository: DebugEventsRepository? = null
) : PdfResolver {
    override suspend fun resolve(result: SearchResult): SearchResult {
        var current = result
        for (resolver in resolvers) {
            debugEventsRepository?.log(DebugLevel.Info, "resolver", "Resolver step start", sourceId = result.sourceId, details = "step=${resolver.javaClass.simpleName}")
            current = runCatching { resolver.resolve(current) }.getOrDefault(current)
            val resolved = current.pdfUrl?.contains(".pdf", ignoreCase = true) == true || current.pdfUrl?.contains("/pdf", ignoreCase = true) == true
            debugEventsRepository?.log(
                DebugLevel.Info,
                "resolver",
                "Resolver step end",
                sourceId = result.sourceId,
                details = "step=${resolver.javaClass.simpleName}, resolved=$resolved, pdfUrl=${current.pdfUrl.orEmpty()}"
            )
            if (resolved) return current
        }
        return current
    }
}

class DirectUrlResolver(
    private val verifier: PdfResolutionVerifier,
    private val sourceProvider: (String) -> Source?
) : PdfResolver {
    override suspend fun resolve(result: SearchResult): SearchResult {
        val pdf = result.pdfUrl ?: return result
        val source = sourceProvider(result.sourceId) ?: return result.copy(pdfUrl = null)
        val verified = verifier.verify(pdf, source.allowedPdfDomains)
        return verified?.let { result.copy(pdfUrl = it.finalUrl, fileSize = it.contentLength?.toString()) } ?: result.copy(pdfUrl = null)
    }
}

class ArxivResolver : PdfResolver {
    override suspend fun resolve(result: SearchResult): SearchResult {
        if (result.pdfUrl != null) return result
        val landing = result.landingUrl ?: return result
        if (!landing.contains("arxiv.org/abs/")) return result
        val id = landing.substringAfter("arxiv.org/abs/").substringBefore('?').substringBefore('#').substringBefore('v')
        return result.copy(pdfUrl = "https://arxiv.org/pdf/$id.pdf")
    }
}

class PmcResolver : PdfResolver {
    override suspend fun resolve(result: SearchResult): SearchResult {
        if (result.pdfUrl != null) return result
        val pmcid = result.identifiers?.get("PMCID") ?: return result
        return result.copy(
            landingUrl = result.landingUrl ?: "https://pmc.ncbi.nlm.nih.gov/articles/$pmcid/",
            pdfUrl = "https://pmc.ncbi.nlm.nih.gov/articles/$pmcid/pdf/"
        )
    }
}

class OpenAlexResolver : PdfResolver {
    override suspend fun resolve(result: SearchResult): SearchResult {
        if (result.pdfUrl != null) return result
        val landing = result.landingUrl ?: return result
        if (!landing.contains("openalex.org/W")) return result

        val req = Request.Builder().url(landing.replace("https://openalex.org", "https://api.openalex.org")).build()
        val body = withContext(Dispatchers.IO) {
            CortexHttpClient.instance.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string()
            }
        } ?: return result
        val parsed = app.shosetsu.android.data.connector.ApiSourceConnector.parseOpenAlexResults("{\"results\":[$body]}", result.sourceId)
        return parsed.firstOrNull()?.let {
            result.copy(pdfUrl = it.pdfUrl ?: result.pdfUrl, landingUrl = it.landingUrl ?: result.landingUrl)
        } ?: result
    }
}

class StandardEbooksResolver : PdfResolver {
    override suspend fun resolve(result: SearchResult): SearchResult {
        val landing = result.landingUrl ?: return result
        if (!landing.contains("standardebooks.org")) return result
        val html = HtmlLinkResolver.fetchSmallHtmlStatic(landing) ?: return result
        val doc = Jsoup.parse(html, landing)
        val pdf = doc.select("a[href$=.pdf], a[href*=/downloads/][href*=.pdf], a[download][href*=.pdf]")
            .mapNotNull { it.absUrl("href").ifBlank { it.attr("href") } }
            .firstOrNull()
        return if (pdf != null) result.copy(pdfUrl = pdf) else result
    }
}

class OpenStaxResolver : PdfResolver {
    override suspend fun resolve(result: SearchResult): SearchResult {
        val landing = result.landingUrl ?: return result
        if (!landing.contains("openstax.org")) return result
        val html = HtmlLinkResolver.fetchSmallHtmlStatic(landing) ?: return result
        val doc = Jsoup.parse(html, landing)
        val pdf = doc.select("a[href*=.pdf], a[href*='download'][href*=pdf]")
            .mapNotNull { it.absUrl("href").ifBlank { it.attr("href") } }
            .firstOrNull()
        return if (pdf != null) result.copy(pdfUrl = pdf) else result
    }
}

@Serializable
data class ResolverCacheEntry(val finalUrl: String, val contentType: String?, val contentLength: Long?, val fileName: String?, val storedAt: Long)

class ResolverCache(private val dataStore: CortexDataStore) {
    private val json = SourceConfigCodec.json
    private val ttlMs = 24 * 60 * 60 * 1000L

    suspend fun get(landingUrl: String): ResolverCacheEntry? {
        val map = readMap()
        val entry = map[landingUrl] ?: return null
        return if (System.currentTimeMillis() - entry.storedAt <= ttlMs) entry else null
    }

    suspend fun put(landingUrl: String, entry: ResolverCacheEntry) {
        val map = readMap().toMutableMap()
        map[landingUrl] = entry
        dataStore.saveResolverCacheJson(json.encodeToString(map))
    }

    private suspend fun readMap(): Map<String, ResolverCacheEntry> {
        val raw = dataStore.resolverCacheJson().first() ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, ResolverCacheEntry>>(raw) }.getOrDefault(emptyMap())
    }
}

data class VerifiedPdfResult(val finalUrl: String, val contentType: String?, val contentLength: Long?, val fileName: String?)

class PdfResolutionVerifier {
    suspend fun verify(url: String, allowedDomains: List<String>): VerifiedPdfResult? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).head().build()
        val response = runCatching { CortexHttpClient.instance.newCall(req).execute() }.getOrNull()
        response?.use {
            val resolved = it.request.url.toString()
            if (!isAllowedDomain(resolved, allowedDomains)) return@withContext null
            if (it.isSuccessful && it.header("Content-Type").orEmpty().contains("application/pdf", true)) {
                return@withContext VerifiedPdfResult(resolved, it.header("Content-Type"), it.header("Content-Length")?.toLongOrNull(), parseFilenameFromContentDisposition(it.header("Content-Disposition")))
            }
        }

        val fallback = Request.Builder().url(url).header("Range", "bytes=0-2048").build()
        val getRes = runCatching { CortexHttpClient.instance.newCall(fallback).execute() }.getOrNull() ?: return@withContext null
        getRes.use {
            val resolved = it.request.url.toString()
            if (!isAllowedDomain(resolved, allowedDomains)) return@withContext null
            if (it.isSuccessful && it.header("Content-Type").orEmpty().contains("application/pdf", true)) {
                return@withContext VerifiedPdfResult(resolved, it.header("Content-Type"), it.header("Content-Length")?.toLongOrNull(), parseFilenameFromContentDisposition(it.header("Content-Disposition")))
            }
        }
        null
    }

    fun parseFilenameFromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val utf = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)
        if (!utf.isNullOrBlank()) return java.net.URLDecoder.decode(utf, Charsets.UTF_8.name())
        return Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)
    }

    fun isAllowedDomain(url: String, domains: List<String>): Boolean {
        if (domains.isEmpty()) return false
        val host = runCatching { URI(url).host }.getOrNull().orEmpty().lowercase()
        return domains.any { domain ->
            val normalized = domain.lowercase()
            host == normalized || host.endsWith(".$normalized")
        }
    }
}

class HtmlLinkResolver(
    private val sourceProvider: (String) -> Source?,
    private val verifier: PdfResolutionVerifier,
    private val cache: ResolverCache
) : PdfResolver {
    override suspend fun resolve(result: SearchResult): SearchResult {
        if (result.pdfUrl != null) return result
        val source = sourceProvider(result.sourceId) ?: return result
        val config = SourceConfigCodec.parseScrape(source) ?: return result
        if (!config.enablePdfResolution) return result

        val landing = result.landingUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return result
        if (!verifier.isAllowedDomain(landing, config.allowedPdfDomains)) return result

        cache.get(landing)?.let { entry ->
            return result.copy(pdfUrl = entry.finalUrl, fileSize = entry.contentLength?.toString())
        }

        val html = fetchSmallHtml(landing) ?: return result
        val found = extractPdfFromHtml(html, landing) ?: return result
        val validated = verifier.verify(found, config.allowedPdfDomains) ?: return result
        cache.put(landing, ResolverCacheEntry(validated.finalUrl, validated.contentType, validated.contentLength, validated.fileName, System.currentTimeMillis()))
        return result.copy(pdfUrl = validated.finalUrl, fileSize = validated.contentLength?.toString())
    }

    private suspend fun fetchSmallHtml(url: String): String? = fetchSmallHtmlStatic(url)

    companion object {
        suspend fun fetchSmallHtmlStatic(url: String): String? = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            CortexHttpClient.instance.newCall(request).execute().use { response: Response ->
                if (!response.isSuccessful) return@withContext null
                val contentType = response.header("Content-Type").orEmpty()
                if (!contentType.contains("text/html", true)) return@withContext null
                response.body?.source()?.use { source ->
                    val bytes = source.readByteArray(128 * 1024)
                    bytes.toString(Charsets.UTF_8)
                }
            }
        }

        fun extractPdfFromHtml(html: String, baseUrl: String): String? {
            val doc = Jsoup.parse(html, baseUrl)
            val anchorPdf = doc.select("a[href]")
                .mapNotNull { it.absUrl("href").ifBlank { it.attr("href") } }
                .firstOrNull { it.contains(".pdf", ignoreCase = true) }
            if (anchorPdf != null) return anchorPdf
            val metaPdf = doc.select("meta[content]")
                .mapNotNull { it.attr("content") }
                .firstOrNull { it.contains(".pdf", ignoreCase = true) }
            return metaPdf
        }
    }
}
