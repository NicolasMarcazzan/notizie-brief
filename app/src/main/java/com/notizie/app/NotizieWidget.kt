package com.notizie.app

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.serialization.json.Json

class NotizieWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("notizie", Context.MODE_PRIVATE)
        val cached = prefs.getString("cached_brief", null)

        val brief: DailyBrief? = if (cached != null) {
            try {
                Json.decodeFromString<DailyBrief>(cached)
            } catch (_: Exception) { null }
        } else null

        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(12),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = if (brief != null) "☀ ${brief.date}" else "Notizie",
                    style = TextStyle(fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.padding(bottom = 8)
                )

                if (brief != null) {
                    brief.items.forEach { item ->
                        Column(modifier = GlanceModifier.padding(bottom = 8)) {
                            Text(
                                text = item.headline,
                                style = TextStyle(fontWeight = FontWeight.Bold),
                                modifier = GlanceModifier.padding(bottom = 2)
                            )
                            Text(text = item.summary)
                        }
                    }
                } else {
                    Text("Configure the worker URL in the app to get started.")
                }

                Row(
                    modifier = GlanceModifier.padding(top = 4),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "↻ Refresh",
                        style = TextStyle(fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>())
                    )
                }
            }
        }
    }
}

class NotizieWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NotizieWidget()
}
