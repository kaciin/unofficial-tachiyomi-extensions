package eu.kanade.tachiyomi.multisrc.mangasproject

object MangasProjectConstants {

    // ============================== HEADERS ===============================    

    const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9," +
        "image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
    const val ACCEPT_JSON = "application/json, text/javascript, */*; q=0.01"
    const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
    const val ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,es;q=0.6,gl;q=0.5"
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; SM-A307GT Build/QP1A.190711.020) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/103.0.5060.71 Mobile Safari/537.36"

    // =========================== ERROR MESSAGES ===========================

    const val MANGA_REMOVED = "Mangá licenciado e removido pela fonte."
    const val TOKEN_NOT_FOUND = "Não foi possível obter o token de leitura."

    // ============================ PREFERENCES =============================

    const val PREFERRED_FORMAT_TITLE = "Formato de imagem favorito"
    const val PREFERRED_FORMAT_KEY = "preferred_format"
    val FORMAT_ENTRIES = arrayOf(
        ".AVIF (Host interno e estável)",
        ".WEBP/PNG/JPG (Host externo, pode dar erro com cloudflare)"
    )
    val FORMAT_LIST = arrayOf("avif", "webp")
}
