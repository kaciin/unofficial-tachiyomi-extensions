package eu.kanade.tachiyomi.extension.pt.centralnovel

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class CNUrlActivity : Activity() {

    private val TAG = "CNUrlActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val slug = pathSegments[1]
            val searchQuery = CentralNovel.PREFIX_SEARCH + slug
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", searchQuery)
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, e.toString())
            }
        } else {
            Log.e(TAG, "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
