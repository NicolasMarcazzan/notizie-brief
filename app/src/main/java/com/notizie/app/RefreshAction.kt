package com.notizie.app

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Manual refresh from the widget button. This calls /trigger so it forces the
 * worker to run the full RSS -> Gemini -> KV pipeline and get genuinely fresh
 * news, rather than re-reading whatever happens to already be cached.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prefs = context.getSharedPreferences("notizie", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("worker_url", null)
        if (baseUrl.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No worker URL configured. Open the app to set it.", Toast.LENGTH_LONG).show()
            }
            return
        }

        val result = withContext(Dispatchers.IO) {
            DataFetcher.triggerBrief(baseUrl)
        }

        when (result) {
            is BriefResult.Success -> {
                prefs.edit()
                    .putString("cached_brief", Json.encodeToString(DailyBrief.serializer(), result.brief))
                    .apply()
            }
            is BriefResult.Failure -> {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Refresh failed: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        NotizieWidget().update(context, glanceId)
    }
}
