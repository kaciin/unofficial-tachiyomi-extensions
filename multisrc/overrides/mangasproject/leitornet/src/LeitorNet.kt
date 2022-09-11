package eu.kanade.tachiyomi.extension.pt.leitornet

import eu.kanade.tachiyomi.multisrc.mangasproject.MangasProject
import okhttp3.Response
class LeitorNet : MangasProject("Leitor.net", "https://leitor.net", "pt-BR") {

    // Use the old generated id when the source did have the name "mangásPROJECT" and
    // did have mangas in their catalogue. Now they "only have webtoons" and
    // became a different website, but they still use the same structure.
    // Existing mangas and other titles in the library still work.
    override val id: Long = 2225174659569980836

    override fun getChapterUrl(response: Response): String {
        return super.getChapterUrl(response)
            .replace("https://mangalivre.net", baseUrl)
            .replace("/ler/", "/manga/")
            .replace("/online/", "/")
    }
}
