package eu.kanade.tachiyomi.multisrc.mangasproject

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

abstract class MangasProject(
    override val name: String,
    override val baseUrl: String,
    override val lang: String
) : ConfigurableSource, HttpSource() {

    override val supportsLatest = true

    // Sometimes the site is slow.
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(::AndroidHttpClientInterceptor)
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)
        .add("User-Agent", MangasProjectConstants.USER_AGENT)

    // Use internal headers to allow "Open in WebView" to work.
    private fun sourceHeadersBuilder(): Headers.Builder = headersBuilder()
        .add("Accept", MangasProjectConstants.ACCEPT_JSON)
        .add("X-Requested-With", "XMLHttpRequest")

    protected val sourceHeaders: Headers by lazy { sourceHeadersBuilder().build() }

    private val json: Json by injectLazy()

    protected open val licensedCheck: Boolean = false

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/home/most_read?page=$page&type=", sourceHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<MangasProjectMostReadDto>()

        val popularMangas = result.mostRead.map(::popularMangaFromObject)

        val hasNextPage = response.request.url.queryParameter("page")!!.toInt() < 10

        return MangasPage(popularMangas, hasNextPage)
    }

    private fun popularMangaFromObject(serie: MangasProjectSerieDto) = SManga.create().apply {
        title = serie.serieName
        thumbnail_url = serie.cover
        url = serie.link
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/home/releases?page=$page&type=", sourceHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<MangasProjectReleasesDto>()

        val latestMangas = result.releases.map(::latestMangaFromObject)

        val hasNextPage = response.request.url.queryParameter("page")!!.toInt() < 5

        return MangasPage(latestMangas, hasNextPage)
    }

    private fun latestMangaFromObject(serie: MangasProjectSerieDto) = SManga.create().apply {
        title = serie.name
        thumbnail_url = serie.image
        url = serie.link
    }

    // =============================== Search ===============================

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/manga/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/manga/$id"
        return MangasPage(listOf(details), false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH) && query.matches(ID_SEARCH_PATTERN)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(searchMangaByIdRequest(id))
                .asObservableSuccess()
                .map { response -> searchMangaByIdParse(response, id) }
        } else super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder()
            .add("search", query)
            .build()

        val newHeaders = sourceHeadersBuilder()
            .add("Content-Length", form.contentLength().toString())
            .add("Content-Type", form.contentType().toString())
            .build()

        return POST("$baseUrl/lib/search/series.json", newHeaders, form)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<MangasProjectSearchDto>()

        // If "series" have boolean false value, then it doesn't have results.
        if (result.series is JsonPrimitive)
            return MangasPage(emptyList(), false)

        val searchMangas = json.decodeFromJsonElement<List<MangasProjectSerieDto>>(result.series)
            .map(::searchMangaFromObject)

        return MangasPage(searchMangas, false)
    }

    private fun searchMangaFromObject(serie: MangasProjectSerieDto) = SManga.create().apply {
        title = serie.name
        thumbnail_url = serie.cover
        url = serie.link
    }

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        val seriesData = document.selectFirst("#series-data")

        val isCompleted = seriesData.selectFirst("span.series-author i.complete-series") != null

        // Check if the manga was removed by the publisher.
        val seriesBlocked = document.selectFirst("div.series-blocked-img:has(img[src$=blocked.svg])")

        val seriesAuthors = document.select("div#series-data span.series-author").text()
            .substringAfter("Completo")
            .substringBefore("+")
            .split("&")
            .groupBy(
                { it.contains("(Arte)") },
                {
                    it.replace(" (Arte)", "")
                        .trim()
                        .split(", ")
                        .reversed()
                        .joinToString(" ")
                }
            )

        return SManga.create().apply {
            thumbnail_url = seriesData.select("div.series-img > div.cover > img").attr("src")
            description = seriesData.select("span.series-desc > span").text()

            status = parseStatus(seriesBlocked, isCompleted)
            author = seriesAuthors[false]?.joinToString(", ") ?: author
            artist = seriesAuthors[true]?.joinToString(", ") ?: author
            genre = seriesData.select("div#series-data ul.tags li")
                .joinToString { it.text() }
        }
    }

    private fun parseStatus(seriesBlocked: Element?, isCompleted: Boolean) = when {
        seriesBlocked != null && licensedCheck -> SManga.LICENSED
        isCompleted -> SManga.COMPLETED
        else -> SManga.ONGOING
    }

    // ============================ Chapter List ============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        if (manga.status != SManga.LICENSED)
            return super.fetchChapterList(manga)

        return Observable.error(Exception(MangasProjectConstants.MANGA_REMOVED))
    }

    private fun chapterListRequestPaginated(mangaUrl: String, id: String, page: Int): Request {
        val newHeaders = sourceHeadersBuilder()
            .set("Referer", baseUrl + mangaUrl)
            .build()

        return GET("$baseUrl/series/chapters_list.json?page=$page&id_serie=$id", newHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val licensedMessage = document.selectFirst("div.series-blocked-img:has(img[src$=blocked.svg])")

        if (licensedMessage != null && licensedCheck) {
            // If the manga is licensed and has been removed from the source,
            // the extension will not fetch the chapters, even if they are returned
            // by the API. This is just to mimic the website behavior.
            throw Exception(MangasProjectConstants.MANGA_REMOVED)
        }

        val mangaUrl = response.request.url.toString().replace(baseUrl, "")
        val mangaId = mangaUrl.substringAfterLast("/")
        var page = 1

        var chapterListRequest = chapterListRequestPaginated(mangaUrl, mangaId, page)
        var chapterListResult = client.newCall(chapterListRequest).execute()
            .parseAs<MangasProjectChapterListDto>()

        if (chapterListResult.chapters is JsonPrimitive)
            return emptyList()

        val chapters = json.decodeFromJsonElement<List<MangasProjectChapterDto>>(chapterListResult.chapters)
            .flatMap(::chaptersFromObject)
            .toMutableList()

        // If the result has less than the default per page, return right away
        // to prevent extra API calls to get the chapters that does not exist.
        if (chapters.size < 30) {
            return chapters
        }

        // Otherwise, call the next pages of the API endpoint.
        chapterListRequest = chapterListRequestPaginated(mangaUrl, mangaId, ++page)
        chapterListResult = client.newCall(chapterListRequest).execute().parseAs()

        while (chapterListResult.chapters is JsonArray) {
            chapters += json.decodeFromJsonElement<List<MangasProjectChapterDto>>(chapterListResult.chapters)
                .flatMap(::chaptersFromObject)
                .toMutableList()

            chapterListRequest = chapterListRequestPaginated(mangaUrl, mangaId, ++page)
            chapterListResult = client.newCall(chapterListRequest).execute().parseAs()
        }

        return chapters
    }

    private fun chaptersFromObject(chapter: MangasProjectChapterDto): List<SChapter> {
        return chapter.releases.values.map { release ->
            SChapter.create().apply {
                name = "Cap. ${chapter.number}" +
                    (if (chapter.name.isEmpty()) "" else " - ${chapter.name}")
                date_upload = chapter.dateCreated.substringBefore("T").toDate()
                scanlator = release.scanlators
                    .mapNotNull { scan -> scan.name.ifEmpty { null } }
                    .sorted()
                    .joinToString()
                url = release.link
                chapter_number = chapter.number.toFloatOrNull() ?: -1f
            }
        }
    }

    // ============================= Page List ==============================

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = headersBuilder()
            .add("Accept", MangasProjectConstants.ACCEPT)
            .add("Accept-Language", MangasProjectConstants.ACCEPT_LANGUAGE)
            .set("Referer", "$baseUrl/home")
            .build()

        return GET(baseUrl + chapter.url, newHeaders)
    }

    private fun pageListApiRequest(chapterUrl: String, key: String): Request {
        val newHeaders = sourceHeadersBuilder()
            .set("Referer", chapterUrl)
            .build()

        val id = chapterUrl
            .substringBeforeLast("/")
            .substringAfterLast("/")

        return GET("$baseUrl/leitor/pages/$id.json?key=$key", newHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pagesKey = MangasProjectUtils.getPagesKey(document)
            ?: throw Exception(MangasProjectConstants.TOKEN_NOT_FOUND)
        val chapterUrl = getChapterUrl(response)

        val apiRequest = pageListApiRequest(chapterUrl, pagesKey)
        val apiResponse = client.newCall(apiRequest).execute()
            .parseAs<MangasProjectReaderDto>()

        val format = preferences.getString(
            MangasProjectConstants.PREFERRED_FORMAT_KEY,
            MangasProjectConstants.FORMAT_LIST.last()
        )

        return apiResponse.images
            .mapIndexed { i, imageObject ->
                val image = if (format == "webp") {
                    imageObject.legacy
                } else imageObject.avif

                Page(i, chapterUrl, image)
            }
    }

    open fun getChapterUrl(response: Response): String {
        return response.request.url.toString()
    }

    // =============================== Images ===============================
    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .removeAll("Referer")
            .set("Accept", MangasProjectConstants.ACCEPT_IMAGE)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {

        val preferredFormat = ListPreference(screen.context).apply {
            title = MangasProjectConstants.PREFERRED_FORMAT_TITLE
            key = MangasProjectConstants.PREFERRED_FORMAT_KEY
            entries = MangasProjectConstants.FORMAT_ENTRIES
            entryValues = MangasProjectConstants.FORMAT_LIST
            setDefaultValue(MangasProjectConstants.FORMAT_LIST.last())
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }
        screen.addPreference(preferredFormat)
    }

    // ============================= Utilities ==============================

    private inline fun <reified T> Response.parseAs(): T {
        val responseBody = body?.string().orEmpty()

        val errorResult = json.decodeFromString<MangasProjectErrorDto>(responseBody)

        if (errorResult.message.isNullOrEmpty().not()) {
            throw Exception(errorResult.message)
        }

        return json.decodeFromString(responseBody)
    }

    private fun String.toDate(): Long {
        return try {
            DATE_FORMATTER.parse(this)?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }

    private fun AndroidHttpClientInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url: String = request.url.toString()
        if ("/manga/" !in url && "/ler/" !in url) {
            return chain.proceed(request)
        }
        val responseType = "text/html; charset=UTF-8".toMediaTypeOrNull()
        val body = MangasProjectUtils.AndroidHttpClientGET(url)
        val responseBody = body.toResponseBody(responseType)
        return Response.Builder()
            .code(200)
            .protocol(Protocol.HTTP_2)
            .request(request)
            .message("OK")
            .body(responseBody)
            .build()
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        }
        const val PREFIX_ID_SEARCH = "id:"
        private val ID_SEARCH_PATTERN = "^id:(\\S+)/(\\d+)$".toRegex()
    }
}
