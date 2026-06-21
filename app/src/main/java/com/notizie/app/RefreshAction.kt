package com.notizie.app

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prefs = context.getSharedPreferences("notizie", Context.MODE_PRIVATE)
        val workerUrl = prefs.getString("worker_url", null) ?: return

        val brief = withContext(Dispatchers.IO) {
            DataFetcher.fetchBrief(workerUrl)
        }

        if (brief != null) {
            prefs.edit().putString("cached_brief", kotlinx.serialization.json.Json.encodeToString(DailyBrief.serializer(), brief)).apply()
        }

        NotizieWidget().update(context, glanceId)
    }
}
