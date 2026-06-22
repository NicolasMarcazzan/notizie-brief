package com.notizie.app

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("notizie", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("worker_url", "")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Notizie"
            textSize = 28f
            setPadding(0, 0, 0, 24)
        }
        layout.addView(title)

        val label = TextView(this).apply {
            text = "Enter your Worker base URL (no path, e.g. without /trigger):"
            textSize = 16f
            setPadding(0, 0, 0, 12)
        }
        layout.addView(label)

        val urlInput = EditText(this).apply {
            setText(savedUrl)
            hint = "https://notizie-brief.supernotizieoggi.workers.dev"
        }
        layout.addView(urlInput)

        val statusText = TextView(this).apply {
            setPadding(0, 16, 0, 0)
        }

        val refreshNowBtn = Button(this).apply {
            text = "Fetch news now"
            isEnabled = !savedUrl.isNullOrBlank()
        }

        val saveBtn = Button(this).apply {
            text = "Save & Start"
            setOnClickListener {
                val url = DataFetcher.normalizeBaseUrl(urlInput.text.toString())
                if (url.isNotEmpty()) {
                    prefs.edit().putString("worker_url", url).apply()
                    urlInput.setText(url)
                    RefreshWorker.schedule(this@MainActivity)
                    refreshNowBtn.isEnabled = true
                    Toast.makeText(
                        this@MainActivity,
                        "Saved! Daily auto-refresh scheduled. Tap \"Fetch news now\" to get today's brief immediately.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this@MainActivity, "Enter a URL first.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(saveBtn)

        refreshNowBtn.setOnClickListener {
            val url = DataFetcher.normalizeBaseUrl(urlInput.text.toString())
            if (url.isEmpty()) {
                Toast.makeText(this, "Enter and save a URL first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            refreshNowBtn.isEnabled = false
            statusText.text = "Fetching today's brief (this calls Gemini, can take a few seconds)…"

            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    DataFetcher.triggerBrief(url)
                }
                refreshNowBtn.isEnabled = true
                when (result) {
                    is BriefResult.Success -> {
                        prefs.edit()
                            .putString(
                                "cached_brief",
                                Json.encodeToString(DailyBrief.serializer(), result.brief)
                            )
                            .apply()
                        val manager = GlanceAppWidgetManager(this@MainActivity)
                        val ids = manager.getGlanceIds(NotizieWidget::class.java)
                        ids.forEach { NotizieWidget().update(this@MainActivity, it) }
                        statusText.text = "Done. Got ${result.brief.items.size} stories for ${result.brief.date}."
                    }
                    is BriefResult.Failure -> {
                        statusText.text = "Failed: ${result.message}"
                    }
                }
            }
        }
        layout.addView(refreshNowBtn)
        layout.addView(statusText)

        setContentView(layout)
    }
}
