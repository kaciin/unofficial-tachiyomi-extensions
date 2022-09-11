package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import eu.kanade.tachiyomi.multisrc.mangasproject.MangasProject
import kotlin.system.exitProcess
class MangaLivreUrlActivity : Activity() {
    private val TAG = "MangaLivreUrlActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val title = pathSegments[1]
            val titleId = pathSegments[2]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", MangasProject.PREFIX_ID_SEARCH + title + "/" + titleId)
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, e.toString())
            }
        } else {
            Log.e(TAG, "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
