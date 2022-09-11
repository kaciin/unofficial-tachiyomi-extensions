package eu.kanade.tachiyomi.multisrc.mangasproject

import org.jsoup.nodes.Document
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object MangasProjectUtils {

    private val TOKEN_REGEX = Regex("window.READER_TOKEN = '(\\S+)';")

    fun getPagesKey(document: Document): String? {
        val docHtml = document.html()
        val token = TOKEN_REGEX.find(docHtml)?.groupValues?.elementAt(1)
        return token
    }

    fun AndroidHttpClientGET(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        val inputStream = BufferedInputStream(connection.inputStream)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val response = reader.readAllLines()
        connection.disconnect()
        return response
    }

    private fun BufferedReader.readAllLines(): String {
        val result = StringBuffer()
        var line: String?
        while (this.readLine().also { line = it } != null) {
            result.append(line)
        }
        return result.toString()
    }
}
