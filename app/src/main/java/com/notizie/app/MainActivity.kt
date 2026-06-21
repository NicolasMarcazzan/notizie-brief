package com.notizie.app

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

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
            text = "Enter your Worker URL:"
            textSize = 16f
            setPadding(0, 0, 0, 12)
        }
        layout.addView(label)

        val urlInput = EditText(this).apply {
            setText(savedUrl)
            hint = "https://notizie-brief.supernotizieoggi.workers.dev"
        }
        layout.addView(urlInput)

        val saveBtn = Button(this).apply {
            text = "Save & Start"
            setOnClickListener {
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    prefs.edit().putString("worker_url", url).apply()
                    RefreshWorker.schedule(this@MainActivity)
                    Toast.makeText(this@MainActivity, "Saved! Widget will update shortly.", Toast.LENGTH_LONG).show()
                }
            }
        }
        layout.addView(saveBtn)

        setContentView(layout)
    }
}
