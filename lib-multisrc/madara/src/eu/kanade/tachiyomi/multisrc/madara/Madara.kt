package eu.kanade.tachiyomi.multisrc.madara

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.post
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody

private const val PAGE_SIZE = 25

abstract class Madara : MadaraBase() {
    private enum class BrowseMode {
        Popular,
        Latest,
        Search,
    }

    override suspend fun getPopularManga(page: Int) = ajaxList(page, BrowseMode.Popular)
    override suspend fun getLatestUpdates(page: Int) = ajaxList(page, BrowseMode.Latest)
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = ajaxList(page, BrowseMode.Search, query, filters)
    override fun getHomeUrl() = "$baseUrl/$mangaSubString/?m_orderby=views"

<<<<<<< HEAD
    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "pt-BR", "es"),
        classLoader = this::class.java.classLoader!!,
    )

    /**
     * If enabled, will attempt to remove non-manga items in popular and latest.
     * The filter will not be used in search as the theme doesn't set the CSS class.
     * Can be disabled if the source incorrectly sets the entry types.
     */
    protected open val filterNonMangaItems = true

    /**
     * The CSS selector used to filter manga items in popular and latest
     * if `filterNonMangaItems` is set to `true`. Can be override if needed.
     * If the flag is set to `false`, it will be empty by default.
     */
    protected open val mangaEntrySelector: String by lazy {
        if (filterNonMangaItems) ".manga" else ""
    }

    /**
     * Automatically fetched genres from the source to be used in the filters.
     */
    protected open var genresList: List<Genre> = emptyList()

    /**
     * Whether genres have been fetched
     */
    private var genresFetched: Boolean = false

    /**
     * Guard to prevent concurrent genre fetches.
     */
    private var isFetchingGenres: Boolean = false

    /**
     * Inner variable to control how much tries the genres request was called.
     */
    private var fetchGenresAttempts: Int = 0

    /**
     * Disable it if you don't want the genres to be fetched.
     */
    protected open val fetchGenres: Boolean = true

    /**
     * The path used in the URL for the manga pages. Can be
     * changed if needed as some sites modify it to other words.
     */
    protected open val mangaSubString = "manga"

    /**
     * enable if the site use "madara_load_more" to load manga on the site
     * Typically has "load More" instead of next/previous page
     *
     * with LoadMoreStrategy.AutoDetect it tries to detect if site uses `madara_load_more`
     */
    protected open val useLoadMoreRequest = LoadMoreStrategy.AutoDetect

    enum class LoadMoreStrategy {
        AutoDetect,
        Always,
        Never,
    }

    /**
     * internal variable to save if site uses load_more or not
     */
    private var loadMoreRequestDetected = LoadMoreDetection.Pending

    private enum class LoadMoreDetection {
        Pending,
        True,
        False,
    }

    protected fun detectLoadMore(document: Document) {
        if (useLoadMoreRequest == LoadMoreStrategy.AutoDetect &&
            loadMoreRequestDetected == LoadMoreDetection.Pending
        ) {
            loadMoreRequestDetected = when (document.selectFirst("nav.navigation-ajax") != null) {
                true -> LoadMoreDetection.True
                false -> LoadMoreDetection.False
            }
        }
    }

    protected fun useLoadMoreRequest(): Boolean = when (useLoadMoreRequest) {
        LoadMoreStrategy.Always -> true
        LoadMoreStrategy.Never -> false
        else -> loadMoreRequestDetected == LoadMoreDetection.True
    }

    // Popular Manga

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select(popularMangaSelector())
            .map(::popularMangaFromElement)
        val hasNextPage = popularMangaNextPageSelector()?.let { document.selectFirst(it) } != null

        detectLoadMore(document)

        return MangasPage(entries, hasNextPage)
    }

    // exclude/filter bilibili manga from list
    protected open fun popularMangaSelector() = "div.page-item-detail:not(:has(a[href*='bilibilicomics.com']))$mangaEntrySelector , .manga__item"

    open val popularMangaUrlSelector = "div.post-title a"
    open val popularMangaUrlSelectorImg = "img"

    protected open fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            selectFirst(popularMangaUrlSelector)!!.let {
                manga.setUrlWithoutDomain(it.attr("abs:href"))
                manga.title = it.ownText()
            }

            selectFirst(popularMangaUrlSelectorImg)?.let {
                manga.thumbnail_url = processThumbnail(imageFromElement(it), true)
            }
        }

        return manga
    }

    override fun popularMangaRequest(page: Int): Request = if (useLoadMoreRequest()) {
        loadMoreRequest(page, popular = true)
    } else {
        GET("$baseUrl/$mangaSubString/${searchPage(page)}?m_orderby=views", headers)
    }

    protected open fun popularMangaNextPageSelector(): String? = if (useLoadMoreRequest()) {
        "body:not(:has(.no-posts))"
    } else {
        "div.nav-previous, nav.navigation-ajax, a.nextpostslink"
    }

    // Related Manga
    protected open fun relatedMangaSelector() = ".related-reading-wrap"

    override fun relatedMangaListParse(response: Response): List<SManga> {
        val document = response.asJsoup()
        return document.select(relatedMangaSelector())
            .mapNotNull { manga ->
                SManga.create().apply {
                    manga.selectFirst(".widget-title a")?.let {
                        setUrlWithoutDomain(it.attr("abs:href"))
                        title = it.ownText()
                    } ?: return@mapNotNull null
                    manga.selectFirst(".widget-thumbnail img")?.let {
                        thumbnail_url = processThumbnail(imageFromElement(it), true)
                    }
=======
    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data.genreRoutes()
        return FilterList(
            buildList {
                add(TextFilter(intl["author_filter_title"], "wp-manga-author"))
                add(TextFilter(intl["artist_filter_title"], "wp-manga-artist"))
                add(TextFilter(intl["year_filter_title"], "wp-manga-release"))
                add(StatusFilter(intl["status_filter_title"], statusFilterOptions))
                add(SortFilter(intl["order_by_filter_title"], orderByFilterOptions))
                add(AdultFilter(intl["adult_content_filter_title"], adultFilterOptions))
                if (genres.isNotEmpty()) {
                    add(Filter.Separator())
                    add(Filter.Header(intl["genre_filter_header"]))
                    add(GenreConditionFilter(intl["genre_condition_filter_title"], genreConditionFilterOptions))
                    add(GenreList(intl["genre_filter_title"], genres))
>>>>>>> upstream/main
                }
            },
        )
    }

    private suspend fun ajaxList(page: Int, mode: BrowseMode, query: String = "", filters: FilterList = FilterList()): MangasPage {
        val body = FormBody.Builder().apply {
            add("action", "madara_load_more")
            add("page", (page - 1).toString())
            add("template", "madara-core/content/content-archive")
            add("vars[paged]", "1")
            add("vars[template]", "archive")
            add("vars[posts_per_page]", PAGE_SIZE.toString())
            add("vars[post_type]", "wp-manga")
            add("vars[post_status]", "publish")
            add("vars[manga_archives_item_layout]", "big_thumbnail")
            if (filterNonMangaItems) {
                add("vars[meta_query][0][key]", "_wp_manga_chapter_type")
                add("vars[meta_query][0][value]", "manga")
            }
            when (mode) {
                BrowseMode.Popular -> sort("_wp_manga_views")
                BrowseMode.Latest -> sort("_latest_update")
                BrowseMode.Search -> addFilters(query, filters, if (filterNonMangaItems) 1 else 0)
            }
        }.build()
        val mangas = parseArchive(client.post("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, body).asJsoup())
        return MangasPage(mangas, mangas.size == PAGE_SIZE)
    }

    private fun FormBody.Builder.sort(key: String) {
        add("vars[orderby]", "meta_value_num")
        add("vars[meta_key]", key)
        add("vars[order]", "DESC")
    }

    private fun FormBody.Builder.addFilters(query: String, filters: FilterList, initialMetaQueryIndex: Int) {
        if (query.isNotBlank()) add("vars[s]", query)
        var metaQueryIndex = initialMetaQueryIndex
        var taxonomyQueryIndex = 0
        val genres = filters.firstInstanceOrNull<GenreList>()?.state?.filter { it.state }?.map { it.slug }.orEmpty()
        filters.forEach { filter ->
            when (filter) {
                is TextFilter -> if (filter.state.isNotBlank()) {
                    add("vars[tax_query][$taxonomyQueryIndex][taxonomy]", filter.taxonomy)
                    add("vars[tax_query][$taxonomyQueryIndex][field]", "name")
                    add("vars[tax_query][$taxonomyQueryIndex][terms]", filter.state)
                    taxonomyQueryIndex++
                }
                is StatusFilter -> filter.state.filter { it.state }.map { it.slug }.takeIf(List<String>::isNotEmpty)?.let { states ->
                    add("vars[meta_query][$metaQueryIndex][key]", "_wp_manga_status")
                    add("vars[meta_query][$metaQueryIndex][compare]", "IN")
                    states.forEachIndexed { i, state -> add("vars[meta_query][$metaQueryIndex][value][$i]", state) }
                    metaQueryIndex++
                }
                is SortFilter -> when (filter.key()) {
                    "latest" -> sort("_latest_update")
                    "alphabet" -> {
                        add("vars[orderby]", "post_title")
                        add("vars[order]", "ASC")
                    }
                    "rating" -> {
                        add("vars[meta_query][query_average_reviews][key]", "_manga_avarage_reviews")
                        add("vars[meta_query][query_average_reviews][compare]", "EXISTS")
                        add("vars[meta_query][query_total_reviews][key]", "_manga_total_votes")
                        add("vars[meta_query][query_total_reviews][compare]", "EXISTS")
                        add("vars[orderby][query_average_reviews]", "DESC")
                        add("vars[orderby][query_total_reviews]", "DESC")
                    }
                    "trending" -> sort("_wp_manga_week_views_value")
                    "views" -> sort("_wp_manga_views")
                    "new-manga" -> {
                        add("vars[orderby]", "date")
                        add("vars[order]", "DESC")
                    }
                }
                is AdultFilter -> if (filter.state != 0) {
                    add("vars[meta_query][$metaQueryIndex][key]", "manga_adult_content")
                    add("vars[meta_query][$metaQueryIndex][compare]", if (filter.state == 1) "not exists" else "exists")
                    metaQueryIndex++
                }
                is GenreConditionFilter -> if (filter.state == 1 && genres.isNotEmpty()) add("vars[tax_query][$taxonomyQueryIndex][operation]", "AND")
                is GenreList -> if (genres.isNotEmpty()) {
                    add("vars[tax_query][$taxonomyQueryIndex][taxonomy]", "wp-manga-genre")
                    add("vars[tax_query][$taxonomyQueryIndex][field]", "slug")
                    genres.forEachIndexed { i, slug -> add("vars[tax_query][$taxonomyQueryIndex][terms][$i]", slug) }
                }
<<<<<<< HEAD
            }

            manga.genre = genres.distinctBy(String::lowercase).joinToString()

            // add alternative name to manga description
            document.selectFirst(altNameSelector)?.ownText()?.let {
                if (it.isBlank().not() && it.notUpdating()) {
                    manga.description = when {
                        manga.description.isNullOrBlank() -> "$altName $it"
                        else -> "${manga.description}\n\n$altName $it"
                    }
                }
            }
        }

        return manga
    }

    // Manga Details Selector
    open val mangaDetailsSelectorTitle = "div.post-title h3, div.post-title h1, #manga-title > h1"
    open val mangaDetailsSelectorAuthor = "div.author-content > a, div.manga-authors > a"
    open val mangaDetailsSelectorArtist = "div.artist-content > a"
    open val mangaDetailsSelectorStatus = "div.summary-content, div.summary-heading:contains(Status) + div"
    open val mangaDetailsSelectorDescription = "div.description-summary div.summary__content, div.summary_content div.post-content_item > h5 + div, div.summary_content div.manga-excerpt"
    open val mangaDetailsSelectorThumbnail = "div.summary_image img"
    open val mangaDetailsSelectorGenre = "div.genres-content a"
    open val mangaDetailsSelectorTag = "div.tags-content a"

    open val seriesTypeSelector = ".post-content_item:contains(Type) .summary-content"
    open val altNameSelector = ".post-content_item:contains(Alt) .summary-content"
    open val altName = intl["alt_names_heading"]

    fun String.notUpdating(): Boolean = this.contains(updatingRegex).not()

    private fun String.containsIn(array: Array<String>): Boolean = array.any { it.equals(this, ignoreCase = true) }

    protected open fun imageFromElement(element: Element): String? = when {
        element.hasAttr("data-src") -> element.attr("abs:data-src")
        element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
        element.hasAttr("srcset") -> element.attr("abs:srcset").getSrcSetImage()
        element.hasAttr("data-cfsrc") -> element.attr("abs:data-cfsrc")
        element.hasAttr("data-manga-src") -> element.attr("abs:data-manga-src")
        else -> element.attr("abs:src")
    }

    /**
     *  Get the best image quality available from srcset
     */
    protected open fun String.getSrcSetImage(): String? {
        val images = this.split(",")
            .map { it.trim().split(WHITESPACE_REGEX, limit = 2) }
            .filter { it.isNotEmpty() && URL_REGEX.matches(it[0]) }

        val imagesWithDescriptor = images
            .filter { it.size == 2 }
            .mapNotNull { candidate ->
                IMAGE_DESCRIPTOR_REGEX.find(candidate[1])?.let { match ->
                    Pair(candidate[0], match.groupValues[1].toFloat())
                }
            }

        // Prefer images with descriptors as to get the highest resolution
        if (imagesWithDescriptor.isNotEmpty()) {
            return imagesWithDescriptor.maxByOrNull { it.second }?.first
        }

        // Fallback to lexicographical comparison of image URLs
        return images.maxOfOrNull { it.first() }
    }

    /**
     *  Apply any additional processing to the thumbnail URL if needed.
     */
    protected open fun processThumbnail(url: String?, fromSearch: Boolean = false): String? = url

    /**
     * Set it to true if the source uses the new AJAX endpoint to
     * fetch the manga chapters instead of the old admin-ajax.php one.
     */
    protected open val useNewChapterEndpoint: Boolean = false

    /**
     * Internal attribute to control if it should always use the
     * new chapter endpoint after a first check if useNewChapterEndpoint is
     * set to false. Using a separate variable to still allow the other
     * one to be overridable manually in each source.
     */
    private var oldChapterEndpointDisabled: Boolean = false

    protected open fun oldXhrChaptersRequest(mangaId: String): Request {
        val form = FormBody.Builder()
            .add("action", "manga_get_chapters")
            .add("manga", mangaId)
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, form)
    }

    protected open fun xhrChaptersRequest(mangaUrl: String): Request = POST("$mangaUrl/ajax/chapters", xhrHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        launchIO { countViews(document) }

        val chaptersWrapper = document.select("div[id^=manga-chapters-holder]")

        var chapterElements = document.select(chapterListSelector())

        if (chapterElements.isEmpty() && !chaptersWrapper.isNullOrEmpty()) {
            val mangaUrl = document.location().removeSuffix("/")
            val mangaId = chaptersWrapper.attr("data-id")

            var xhrRequest = if (useNewChapterEndpoint || oldChapterEndpointDisabled) {
                xhrChaptersRequest(mangaUrl)
            } else {
                oldXhrChaptersRequest(mangaId)
            }
            var xhrResponse = client.newCall(xhrRequest).execute()

            // Newer Madara versions throws HTTP 400 when using the old endpoint.
            if (!useNewChapterEndpoint && xhrResponse.code == 400) {
                xhrResponse.close()
                // Set it to true so following calls will be made directly to the new endpoint.
                oldChapterEndpointDisabled = true

                xhrRequest = xhrChaptersRequest(mangaUrl)
                xhrResponse = client.newCall(xhrRequest).execute()
            }

            chapterElements = xhrResponse.asJsoup().select(chapterListSelector())
            xhrResponse.close()
        }

        return chapterElements.map(::chapterFromElement)
    }

    protected open fun chapterListSelector() = "li.wp-manga-chapter"

    protected open fun chapterDateSelector() = "span.chapter-release-date"

    open val chapterUrlSelector = "a"

    // can cause some issue for some site. blocked by cloudflare when opening the chapter pages
    open val chapterUrlSuffix = "?style=list"

    protected open fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        with(element) {
            selectFirst(chapterUrlSelector)!!.let { urlElement ->
                chapter.url = urlElement.attr("abs:href").let {
                    it.substringBefore("?style=paged") + if (!it.endsWith(chapterUrlSuffix)) chapterUrlSuffix else ""
                }
                chapter.name = urlElement.text()
            }
            // Dates can be part of a "new" graphic or plain text
            // Added "title" alternative
            chapter.date_upload = selectFirst("img:not(.thumb)")?.attr("alt")?.let { parseRelativeDate(it) }
                ?: selectFirst("span a")?.attr("title")?.let { parseRelativeDate(it) }
                ?: parseChapterDate(selectFirst(chapterDateSelector())?.text())
        }

        return chapter
    }

    open fun parseChapterDate(date: String?): Long {
        date ?: return 0

        fun SimpleDateFormat.tryParse(string: String): Long = try {
            parse(string)?.time ?: 0
        } catch (_: ParseException) {
            0
        }

        return when {
            // Handle 'yesterday' and 'today', using midnight
            WS_YESTERDAY.startsWith(date) -> {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, -1) // yesterday
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }

            WS_TODAY.startsWith(date) -> {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }

            WS_TWO_DAYS.startsWith(date) -> {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, -2) // day before yesterday
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }

            WS_AGO.endsWith(date) -> {
                parseRelativeDate(date)
            }

            WS_HACE.startsOrEndsWith(date) -> {
                parseRelativeDate(date)
            }

            // Handle "jour" with a number before it
            date.contains(Regex("""\b\d+ jour""")) -> {
                parseRelativeDate(date)
            }

            date.contains(Regex("""\d(st|nd|rd|th)""")) -> {
                // Clean date (e.g. 5th December 2019 to 5 December 2019) before parsing it
                date.split(" ").map {
                    if (it.contains(Regex("""\d\D\D"""))) {
                        it.replace(Regex("""\D"""), "")
                    } else {
                        it
                    }
                }
                    .let { dateFormat.tryParse(it.joinToString(" ")) }
            }

            else -> dateFormat.tryParse(date)
        }
    }

    // Parses dates in this form:
    // 21 horas ago
    protected open fun parseRelativeDate(date: String): Long {
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            WS_DAYS.anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            WS_HOURS.anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            WS_MINS.anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            WS_SECS.anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            WS_WEEKS.anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
            WS_MONTHS.anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            WS_YEARS.anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            else -> 0
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    open val pageListParseSelector = "div.page-break, li.blocks-gallery-item, .reading-content .text-left:not(:has(.blocks-gallery-item)) img"

    open val chapterProtectorSelector = "#chapter-protector-data"
    open val chapterProtectorPasswordPrefix = "wpmangaprotectornonce='"
    open val chapterProtectorDataPrefix = "chapter_data='"

    override fun pageListParse(response: Response): List<Page> = pageListParse(response.asJsoup())

    protected open fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }

        val chapterProtector = document.selectFirst(chapterProtectorSelector)
            ?: return document.select(pageListParseSelector).mapIndexed { index, element ->
                val imageUrl = element.selectFirst("img")?.let { imageFromElement(it) }
                Page(index, document.location(), imageUrl)
            }
        val chapterProtectorHtml = chapterProtector.attr("src")
            .takeIf { it.startsWith("data:text/javascript;base64,") }
            ?.substringAfter("data:text/javascript;base64,")
            ?.let { Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8) }
            ?: chapterProtector.html()
        val password = chapterProtectorHtml
            .substringAfter(chapterProtectorPasswordPrefix)
            .substringBefore("';")
        val chapterData = json.parseToJsonElement(
            chapterProtectorHtml
                .substringAfter(chapterProtectorDataPrefix)
                .substringBefore("';")
                .replace("\\/", "/"),
        ).jsonObject

        val unsaltedCiphertext = Base64.decode(chapterData["ct"]!!.jsonPrimitive.content, Base64.DEFAULT)
        val salt = chapterData["s"]!!.jsonPrimitive.content.decodeHex()
        val ciphertext = salted + salt + unsaltedCiphertext

        val rawImgArray = CryptoAES.decrypt(Base64.encodeToString(ciphertext, Base64.DEFAULT), password)
        val imgArrayString = json.parseToJsonElement(rawImgArray).jsonPrimitive.content
        val imgArray = json.parseToJsonElement(imgArrayString).jsonArray

        return imgArray.mapIndexed { idx, it ->
            Page(idx, document.location(), it.jsonPrimitive.content)
        }
    }

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers.newBuilder().set("Referer", page.url).build())

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    /**
     * Set it to false if you want to disable the extension reporting the view count
     * back to the source website through admin-ajax.php.
     */
    protected open val sendViewCount: Boolean = true

    protected open fun countViewsRequest(document: Document): Request? {
        val wpMangaData = document.selectFirst("script#wp-manga-js-extra")
            ?.data() ?: return null

        val wpMangaInfo = wpMangaData
            .substringAfter("var manga = ")
            .substringBeforeLast(";")

        val wpManga = json.parseToJsonElement(wpMangaInfo).jsonObject

        if (wpManga["enable_manga_view"]?.jsonPrimitive?.content == "1") {
            val formBuilder = FormBody.Builder()
                .add("action", "manga_views")
                .add("manga", wpManga["manga_id"]!!.jsonPrimitive.content)

            if (wpManga["chapter_slug"] != null) {
                formBuilder.add("chapter", wpManga["chapter_slug"]!!.jsonPrimitive.content)
            }

            val formBody = formBuilder.build()

            val newHeaders = headersBuilder()
                .set("Referer", document.location())
                .build()

            return POST("$baseUrl/wp-admin/admin-ajax.php", newHeaders, formBody)
        }

        return null
    }

    /**
     * Send the view count request to the Madara endpoint.
     *
     * @param document The response document with the wp-manga data
     */
    protected fun countViews(document: Document) {
        if (!sendViewCount) {
            return
        }

        try {
            val request = countViewsRequest(document) ?: return
            client.newCall(request).execute().close()
        } catch (_: Exception) { }
    }

    /**
     * Fetch the genres from the source to be used in the filters.
     */
    protected fun fetchGenres() {
        if (fetchGenres && fetchGenresAttempts < 3 && !genresFetched && !isFetchingGenres) {
            isFetchingGenres = true
            try {
                val fetchedGenres = client.newCall(genresRequest()).execute()
                    .use { parseGenres(it.asJsoup()) }

                if (fetchedGenres.isNotEmpty()) {
                    genresList = fetchedGenres
                    genresFetched = true
                }
            } catch (_: Exception) {
            } finally {
                fetchGenresAttempts++
                isFetchingGenres = false
=======
                else -> Unit
>>>>>>> upstream/main
            }
        }
    }

<<<<<<< HEAD
    /**
     * The request to the search page (or another one) that have the genres list.
     */
    protected open fun genresRequest(): Request = GET("$baseUrl/?s=genre&post_type=wp-manga", headers)

    /**
     * Get the genres from the search page document.
     *
     * @param document The search page document
     */
    protected open fun parseGenres(document: Document): List<Genre> = document.selectFirst("div.checkbox-group")
        ?.select("div.checkbox")
        .orEmpty()
        .map { li ->
            Genre(
                li.selectFirst("label")!!.text(),
                li.selectFirst("input[type=checkbox]")!!.`val`(),
            )
        }

    protected val salted = "Salted__".toByteArray(Charsets.UTF_8)

    private val scope = CoroutineScope(Dispatchers.IO)

    protected fun launchIO(block: () -> Unit) = scope.launch { block() }

    companion object {
        const val URL_SEARCH_PREFIX = "slug:"
        val URL_REGEX = """^(https?://[^\s/$.?#].[^\s]*)${'$'}""".toRegex()
        val WHITESPACE_REGEX = """\s+""".toRegex()
        val IMAGE_DESCRIPTOR_REGEX = """^(\d+|\d+\.\d+)([wx])$""".toRegex()
        val updatingRegex = "Updating|Atualizando".toRegex(RegexOption.IGNORE_CASE)

        // Static WordSets to avoid repeated allocations during parsing
        private val WS_YESTERDAY = WordSet("yesterday", "يوم واحد")
        private val WS_TODAY = WordSet("today")
        private val WS_TWO_DAYS = WordSet("يومين")
        private val WS_AGO = WordSet("ago", "atrás", "atras", "önce", "قبل", "trước")
        private val WS_HACE = WordSet("hace", "năm", "tháng", "tuần", "ngày", "giờ", "phút", "giây")
        private val WS_DAYS = WordSet("hari", "gün", "jour", "día", "dia", "dias", "day", "วัน", "ngày", "giorni", "أيام", "天")
        private val WS_HOURS = WordSet("jam", "saat", "heure", "hora", "horas", "hour", "ชั่วโมง", "giờ", "ore", "ساعة", "小时")
        private val WS_MINS = WordSet("menit", "dakika", "min", "minute", "minuto", "minutos", "นาที", "دقائق", "phút")
        private val WS_SECS = WordSet("detik", "segundo", "segundos", "second", "วินาที", "giây")
        private val WS_WEEKS = WordSet("week", "semana", "semanas", "tuần")
        private val WS_MONTHS = WordSet("month", "mes", "tháng")
        private val WS_YEARS = WordSet("year", "año", "năm")
    }
}

class WordSet(private vararg val words: String) {
    fun anyWordIn(dateString: String): Boolean = words.any { dateString.contains(it, ignoreCase = true) }
    fun startsWith(dateString: String): Boolean = words.any { dateString.startsWith(it, ignoreCase = true) }
    fun endsWith(dateString: String): Boolean = words.any { dateString.endsWith(it, ignoreCase = true) }
    fun startsOrEndsWith(dateString: String): Boolean = words.any { dateString.startsWith(it, ignoreCase = true) || dateString.endsWith(it, ignoreCase = true) }
}
=======
    override suspend fun fetchRelatedMangaList(id: String, genres: List<GenreRoute>): List<SManga> {
        val body = FormBody.Builder().apply {
            add("action", "madara_load_more")
            add("page", "0")
            add("template", "madara-core/content/content-archive")
            add("vars[posts_per_page]", PAGE_SIZE.toString())
            add("vars[template]", "archive")
            add("vars[post_type]", "wp-manga")
            add("vars[post_status]", "publish")
            add("vars[orderby]", "rand")
            add("vars[sidebar]", "right")
            add("vars[manga_archives_item_layout]", "big_thumbnail")
            add("vars[post__not_in][0]", id)
            add("vars[tax_query][0][taxonomy]", "wp-manga-genre")
            add("vars[tax_query][0][field]", "slug")
            genres.forEachIndexed { i, genre -> add("vars[tax_query][0][terms][$i]", genre.slug) }
        }.build()
        return parseArchive(client.post("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, body).asJsoup())
    }
}
>>>>>>> upstream/main
