package com.stingai.messengerbot

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvPermissionStatus: TextView
    private lateinit var btnGrantPermission: Button
    private lateinit var etWebhookUrl: EditText
    private lateinit var btnSaveWebhook: Button
    
    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        etWebhookUrl = findViewById(R.id.etWebhookUrl)
        btnSaveWebhook = findViewById(R.id.btnSaveWebhook)

        sharedPrefs = getSharedPreferences("BotPrefs", Context.MODE_PRIVATE)

        // Load saved webhook
        val savedUrl = sharedPrefs.getString("webhook_url", "")
        etWebhookUrl.setText(savedUrl)

        btnSaveWebhook.setOnClickListener {
            val url = etWebhookUrl.text.toString().trim()
            sharedPrefs.edit().putString("webhook_url", url).apply()
        }

        btnGrantPermission.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        val componentName = ComponentName(this, NotificationService::class.java)
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        
        val isEnabled = enabledListeners != null && enabledListeners.contains(componentName.flattenToString())
        
        if (isEnabled) {
            tvPermissionStatus.text = "Permiso concedido. El bot está activo."
            tvPermissionStatus.setTextColor(android.graphics.Color.parseColor("#00AA00"))
            btnGrantPermission.isEnabled = false
        } else {
            tvPermissionStatus.text = "Falta permiso. Por favor, otórgalo."
            tvPermissionStatus.setTextColor(android.graphics.Color.parseColor("#FF0000"))
            btnGrantPermission.isEnabled = true
        }
    }
}
