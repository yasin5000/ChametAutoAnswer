package com.example.chametautoanswer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32)
        }

        val title = TextView(this).apply {
            text = "Chamet Auto Answer"
            textSize = 24f
            gravity = Gravity.CENTER
        }

        val instructions = TextView(this).apply {
            text = "\\n1. Enable the accessibility service below.\\n" +
                "2. Return here and check the status.\\n" +
                "3. Test with a call you expect and can safely answer.\\n\\n" +
                "The helper only attempts a tap when it finds a clearly labelled " +
                "Answer/Accept/Receive button in a Chamet window. It does not use blind screen coordinates."
            textSize = 16f
        }

        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }

        val settingsButton = Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val refreshButton = Button(this).apply {
            text = "Refresh status"
            setOnClickListener { updateStatus() }
        }

        layout.addView(title)
        layout.addView(instructions)
        layout.addView(status)
        layout.addView(settingsButton)
        layout.addView(refreshButton)
        setContentView(layout)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val enabled = try {
            val setting = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            setting.split(':').any {
                it.contains(packageName, ignoreCase = true) &&
                    it.contains(ChametAccessibilityService::class.java.simpleName, ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        }
        status.text = if (enabled) {
            "Status: Accessibility service appears ENABLED."
        } else {
            "Status: Accessibility service is OFF. Enable “Chamet Auto Answer Helper” in settings."
        }
    }
}
