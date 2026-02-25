package app.shosetsu.android.data.resolver

import app.shosetsu.android.data.network.CortexHttpClient
import app.shosetsu.android.domain.config.SourceConfigCodec
import app.shosetsu.android.domain.model.SearchResult
import app.shosetsu.android.domain.model.Source
import app.shosetsu.android.domain.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.net.URI

class PdfResolverChain(
    private val resolvers: List<PdfResolver>
) : PdfResolver {
    override suspend fun resolve(result: SearchResult): SearchResult {
        var current = result
        for (resolver in resolvers) {
            current = runCatching { resolver.resolve(current) }.getOrDefault(current)
            if (current.pdfUrl?.contains(".pdf", ignoreCase = true) == true || current.pdfUrl?.contains("/pdf", ignoreCase = true) == true) return current
        }
        return current
    }
}

class DirectUrlResolver : PdfResolver {
    override suspend fun resolve(result: SearchResult): SearchResult {
        val pdf = result.pdfUrl ?: return result
        return if (pdf.contains(".pdf", ignoreCase = true) || pdf.contains("/pdf", ignoreCase = true)) result else result.copy(pdfUrl = null)
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

class HtmlLinkResolver(
    private val sourceProvider: (String) -> Source?
) : PdfResolver {
    override suspend fun resolve(result: SearchResult): SearchResult {
        if (result.pdfUrl != null) return result
        val source = sourceProvider(result.sourceId) ?: return result
        if (source.type != SourceType.GenericWeb) return result
        val config = SourceConfigCodec.parseScrape(source) ?: return result
        if (!config.enablePdfResolution) return result

        val landing = result.landingUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return result
        if (!isAllowedDomain(landing, config.allowedPdfDomains)) return result

        val html = fetchSmallHtml(landing) ?: return result
        val found = extractPdfFromHtml(html, landing)
        if (found == null || !isAllowedDomain(found, config.allowedPdfDomains)) return result

        val validated = verifyPdfContentType(found)
        return validated?.let { result.copy(pdfUrl = it) } ?: result
    }

    private fun isAllowedDomain(url: String, domains: List<String>): Boolean {
        if (domains.isEmpty()) return false
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        return domains.any { host == it || host.endsWith(".$it") }
    }

    private suspend fun fetchSmallHtml(url: String): String? = withContext(Dispatchers.IO) {
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

    private suspend fun verifyPdfContentType(url: String): String? = withContext(Dispatchers.IO) {
        val headRequest = Request.Builder().url(url).head().build()
        val headOk = runCatching {
            CortexHttpClient.instance.newCall(headRequest).execute().use { response ->
                response.isSuccessful && response.header("Content-Type").orEmpty().contains("application/pdf", ignoreCase = true)
            }
        }.getOrDefault(false)
        if (headOk) return@withContext url

        val getRequest = Request.Builder().url(url).header("Range", "bytes=0-2048").build()
        val getOk = runCatching {
            CortexHttpClient.instance.newCall(getRequest).execute().use { response ->
                response.isSuccessful && response.header("Content-Type").orEmpty().contains("application/pdf", ignoreCase = true)
            }
        }.getOrDefault(false)
        if (getOk) url else null
    }

    companion object {
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
