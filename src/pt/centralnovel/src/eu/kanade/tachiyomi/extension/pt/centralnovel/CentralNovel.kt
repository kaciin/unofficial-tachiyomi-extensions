package eu.kanade.tachiyomi.extension.pt.centralnovel

import android.app.Application
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.text.Layout.Alignment
import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.dataimage.DataImageInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.claudemirovsky.noveltomanga.DefaultThemes
import org.claudemirovsky.noveltomanga.NovelToManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class CentralNovel : ConfigurableSource, ParsedHttpSource() {

    override val name = "Central Novel"

    override val baseUrl = "https://centralnovel.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient
        .newBuilder()
        .addInterceptor(DataImageInterceptor())
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", USER_AGENT)

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val noveltomanga by lazy {
        NovelToManga().apply {
            fontSize = preferences.getInt(PREF_FONTSIZE_KEY, 30).toFloat()
            margin = preferences.getInt(PREF_MARGIN_KEY, 30).toFloat()
            theme = DefaultThemes.valueOf(preferences.getString(PREF_THEME_KEY, "DARK")!!)
            val centered = preferences.getBoolean(PREF_CENTERED_KEY, false)
            alignment = if (centered) Alignment.ALIGN_CENTER else Alignment.ALIGN_NORMAL
        }
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "div.serieslist.pop div.imgseries > a.series"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val imgElement = element.selectFirst("img")
        title = imgElement.attr("title")
        thumbnail_url = imgElement.attr("src").substringBefore("=") + "=151,215"
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun popularMangaNextPageSelector(): String? = null

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/series/?page=$page&order=update", headers)

    override fun latestUpdatesSelector() = "div.listupd a.tip"

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = "div.hpage a.r:contains(Próximo)"

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SEARCH)) {
            val slug = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$baseUrl/series/$slug"))
                .asObservableSuccess()
                .map { response ->
                    searchMangaBySlugParse(response, slug)
                }
        } else {
            val params = CNFilters.getSearchParameters(filters)
            client.newCall(searchMangaRequest(page, query, params))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response)
                }
        }
    }

    private fun searchMangaBySlugParse(response: Response, slug: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/series/$slug"
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("not used")

    private fun searchMangaRequest(page: Int, query: String, filters: CNFilters.FilterSearchParams): Request {
        val url = "$baseUrl/series/".toHttpUrl().newBuilder()
        url.addQueryParameter("page", page.toString())
        if (query.isNotBlank())
            url.addQueryParameter("s", query)
        if (filters.status.isNotBlank())
            url.addQueryParameter("status", filters.status)
        if (filters.order.isNotBlank())
            url.addQueryParameter("order", filters.order)
        if (filters.genres.size > 0)
            filters.genres.forEach { url.addQueryParameter("genre[]", it) }
        if (filters.types.size > 0)
            filters.types.forEach { url.addQueryParameter("types[]", it) }
        return GET(url.build().toString(), headers)
    }

    override fun getFilterList(): FilterList = CNFilters.filterList

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val img = document.selectFirst("div.thumb > img")
        val info = document.selectFirst("div.ninfo > div.info-content")
        title = img.attr("title")
        thumbnail_url = img.attr("src")
        author = info.getInfo("Autor:")
        genre = info.select("div.genxed > a").joinToString(", ") { it.text() }
        status = info.getInfo("Status:")!!.toStatus()
        var desc = document.selectFirst("div.entry-content").text() + "\n"
        document.selectFirst("div.ninfo > span.alter").let {
            desc += "\nTítulos Alternativos: ${it.text()}"
        }
        info.getInfo("Tipo:", false)?.let { desc += "\n$it" }
        info.getInfo("Lançamento::", false)?.let { desc += "\n$it" }
        description = desc
    }

    // ============================== Chapters ==============================
    override fun chapterListSelector() = "div.eplister li > a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val num = element.selectFirst("div.epl-num").text()
        chapter_number = runCatching {
            num.substringAfter("Cap. ").substringBefore(" ").toFloat()
        }.getOrNull() ?: 0F
        name = num + " " + element.selectFirst("div.epl-title").text()
        date_upload = element.selectFirst("div.epl-date").text().toDate()
        setUrlWithoutDomain(element.attr("href"))
    }

    // =============================== Pages ================================

    override fun pageListParse(document: Document): List<Page> {
        val content = document.selectFirst("div.epcontent")
        val imgs = content.select("img")
        // if it has images, then show it
        if (imgs.size > 0) {
            return imgs.mapIndexed { page, img ->
                Page(page, document.location(), img.attr("src"))
            }
        }
        // else, turn the novel-text into images
        val elements = content.let {
            val spans = it.select("span")
            if (spans.size > 0) {
                spans
            } else {
                it.select("p")
            }
        }
        val lines = elements.map { it.text() }

        val bitmaps = noveltomanga.getMangaPages(lines)

        val encodedUrls = bitmaps.toBase64Urls()

        return encodedUrls
            .mapIndexed { page, encodedImageUrl ->
                Page(page, "", encodedImageUrl)
            }
    }

    override fun imageUrlParse(document: Document) = ""

    // ============================== Settings ==============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val themePref = ListPreference(screen.context).apply {
            key = PREF_THEME_KEY
            title = PREF_THEME_TITLE
            entries = PREF_THEME_ENTRIES
            entryValues = PREF_THEME_VALUES
            setDefaultValue("DARK")
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                noveltomanga.theme = DefaultThemes.valueOf(entry)
                preferences.edit().putString(key, entry).commit()
            }
        }

        val centeredPref = SwitchPreferenceCompat(screen.context).apply {
            key = PREF_CENTERED_KEY
            title = PREF_CENTERED_TITLE
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                noveltomanga.alignment = when (checkValue) {
                    true -> Alignment.ALIGN_CENTER
                    else -> Alignment.ALIGN_NORMAL
                }
                preferences.edit().putBoolean(key, checkValue).commit()
            }
        }

        val fontSizePref = SeekBarPreference(screen.context).apply {
            key = PREF_FONTSIZE_KEY
            title = PREF_FONTSIZE_TITLE
            setDefaultValue(30)
            setMin(10)
            setMax(100)
            setShowSeekBarValue(true)

            setOnPreferenceChangeListener { _, newValue ->
                val num = newValue as Int
                noveltomanga.fontSize = num.toFloat()
                preferences.edit().putInt(key, num).commit()
            }
        }

        val marginWidthPref = SeekBarPreference(screen.context).apply {
            key = PREF_MARGIN_KEY
            title = PREF_MARGIN_TITLE
            setDefaultValue(30)
            setMin(20)
            setMax(500)
            setShowSeekBarValue(true)

            setOnPreferenceChangeListener { _, newValue ->
                val num = newValue as Int
                noveltomanga.margin = num.toFloat()
                preferences.edit().putInt(key, num).commit()
            }
        }

        screen.addPreference(themePref)
        screen.addPreference(centeredPref)
        screen.addPreference(fontSizePref)
        screen.addPreference(marginWidthPref)
    }

    // ============================= Utilities ==============================

    private fun List<Bitmap>.toBase64Urls(): List<String> {
        return map { bitmap ->
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val byteArray = outputStream.toByteArray()
            val encodedImage = Base64.encodeToString(byteArray, Base64.DEFAULT)
            "https://127.0.0.1/?image/jpeg;base64,$encodedImage"
        }
    }

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER.parse(trim())?.time }
            .getOrNull() ?: 0L
    }

    private fun String.toStatus() = when (this) {
        "Em andamento" -> SManga.ONGOING
        "Completo" -> SManga.COMPLETED
        "Hiato" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun Element.getInfo(selector: String, trim: Boolean = true): String? {
        val info = selectFirst("div.spe > span:contains($selector)")
            ?.text()
            ?: return null
        return if (trim) {
            info.substringAfter(selector).trim()
        } else {
            info
        }
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR"))
        }

        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/103.0.5060.71 Mobile Safari/537.36"

        const val PREFIX_SEARCH = "slug:"

        private const val PREF_CENTERED_KEY = "centered_pref"
        private const val PREF_CENTERED_TITLE = "Centralizar texto"

        private const val PREF_FONTSIZE_KEY = "fontsize_pref"
        private const val PREF_FONTSIZE_TITLE = "Tamanho da fonte"

        private const val PREF_MARGIN_KEY = "margin_pref"
        private const val PREF_MARGIN_TITLE = "Largura da margem"

        private const val PREF_THEME_KEY = "theme_pref"
        private const val PREF_THEME_TITLE = "Tema preferido"
        private val PREF_THEME_ENTRIES = arrayOf("Preto", "Escuro", "Claro")
        private val PREF_THEME_VALUES = enumValues<DefaultThemes>()
            .map { it.name }
            .toTypedArray()
    }
}
