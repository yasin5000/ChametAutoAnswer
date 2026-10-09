package com.example.chametautoanswer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var toggle: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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

        toggle = Switch(this).apply {
            textSize = 20f
            setPadding(0, 32, 0, 32)
            isChecked = prefs.getBoolean(KEY_ENABLED, false)
            text = label(isChecked)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_ENABLED, checked).apply()
                text = label(checked)
            }
        }

        val instructions = TextView(this).apply {
            text = "\n1. Enable the accessibility service below (one time).\n" +
                "2. Turn the switch above ON when you want calls auto-received.\n" +
                "3. Turn it OFF when you want to answer calls yourself.\n"
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
        layout.addView(toggle)
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

    private fun label(on: Boolean) = if (on) "Auto Receive: ON" else "Auto Receive: OFF"

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
            "Accessibility service: ENABLED"
        } else {
            "Accessibility service: OFF. Enable “Chamet Auto Answer Helper” in settings."
        }
    }

    companion object {
        const val PREFS = "chamet_prefs"
        const val KEY_ENABLED = "auto_enabled"
    }
}
