package eu.kanade.tachiyomi.extension.es.emperorscan

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Source
class EmperorScan(
    override val id: Long,
    override val name: String,
    override val lang: String,
    override val baseUrl: String,
) : HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(6) { it.host == baseUrlHost }
        .build()

    private val preferences: SharedPreferences = getPreferences()

    private val removePremium get() =
        preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/manga/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        // Mapeamos los elementos del catálogo con los selectores actualizados del HTML
        val mangas = document.select("ul.grid > li").map { element ->
            SManga.create().apply {
                val anchor = element.selectFirst("a[href*='/manga/']")
                setUrlWithoutDomain(anchor?.attr("href") ?: "")

                // El título se extrae de forma segura desde el atributo 'aria-label' o el texto del header
                title = anchor?.attr("aria-label")?.trim() ?: element.select("h2").text().trim()

                // Obtenemos el thumbnail usando el atributo 'src' de la imagen interna
                thumbnail_url = element.selectFirst("img")?.attr("abs:src") ?: ""
            }
        }

        // CORRECCIÓN DEFINITIVA DE PAGINACIÓN:
        // Identificamos el valor de la página que la extensión solicitó a la red
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1

        // Buscamos el bloque de texto con el formato "Página 1 de 25"
        val paginationText = document.select("p:contains(Página)").text()

        val maxPage = try {
            // Buscamos dígitos numéricos después de la palabra "de "
            val regex = """Página\s+\d+\s+de\s+(\d+)""".toRegex(RegexOption.IGNORE_CASE)
            val matchResult = regex.find(paginationText)
            matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 1
        } catch (e: Exception) {
            1
        }

        // Determinamos si hay una página consecutiva basándonos en el total absoluto del sitio
        val hasNextPage = currentPage < maxPage

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/manga/".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "latest") // Conservamos el filtro de novedades
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga/".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")?.text() ?: ""
            thumbnail_url = document.selectFirst("div.bg-surface-2 img, div.hposter__card img")?.absUrl("src")
            description = document.select("p.col-span-2.max-w-3xl").text()
                .replace("HAZ CLICK AQUÍ PARA UNIRTE A NUESTRO DISCORD", "", ignoreCase = true)
                .trim()
            status = when (document.select("span.text-muted:contains(En curso), span:contains(Publicándose)").size > 0) {
                true -> SManga.ONGOING
                false -> SManga.UNKNOWN
            }

            val genres = document.select("a.chip").map { it.text().trim() }
            genre = genres.filterNot { item ->
                removePremium && (item.contains("Vip", ignoreCase = true) || item.contains("Premium", ignoreCase = true))
            }.joinToString(", ")
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaUrl = manga.url.removeSuffix("/")
        return GET("$baseUrl$mangaUrl/capitulos.json", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val jsonString = response.body.string()
        val chapters = mutableListOf<SChapter>()

        try {
            val jsonObject = JSONObject(jsonString)
            val itemsArray = jsonObject.getJSONArray("items")

            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.getJSONObject(i)

                val isVip = item.optString("access", "").contains("vip", ignoreCase = true) ||
                    item.optBoolean("locked", false)

                if (removePremium && isVip) continue

                val chapter = SChapter.create().apply {
                    val chapterSlug = item.getString("slug")
                    url = "${response.request.url.encodedPath.replace("/capitulos.json", "")}/$chapterSlug"
                    name = item.getString("label").trim()

                    val dateText = item.optString("published_label", "")
                    date_upload = parseChapterDate(dateText)
                }
                chapters.add(chapter)
            }
        } catch (e: Exception) {
            return emptyList()
        }

        return chapters
    }

    private fun parseChapterDate(dateText: String): Long {
        if (dateText.isEmpty()) return 0L
        return try {
            val parsed = ZonedDateTime.parse(dateText, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            parsed.toInstant().toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.reader-area img, div.read-container img, main img[src*=/img/]").mapIndexed { index, element ->
            val imageUrl = element.attr("data-src").ifEmpty { element.attr("src") }
            Page(index, "", element.absUrl(imageUrl))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_PREMIUM_CHAPTERS
            title = "Filtrar capítulos VIP"
            summary = "Oculta automáticamente los capítulos VIP del feed"
            setDefaultValue(REMOVE_PREMIUM_CHAPTERS_DEFAULT)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Para aplicar los cambios, actualiza la lista de capítulos", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }
    }

    companion object {
        private const val REMOVE_PREMIUM_CHAPTERS = "removePremiumChapters"
        private const val REMOVE_PREMIUM_CHAPTERS_DEFAULT = true
    }
}
