package com.stingai.messengerbot

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class NotificationService : NotificationListenerService() {

    private val client = OkHttpClient()
    private val TAG = "MessengerBotService"

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        // Filtramos para asegurar que es Facebook Messenger o Messenger Lite
        if (packageName != "com.facebook.orca" && packageName != "com.facebook.mlite") {
            return
        }

        val notification = sbn.notification
        val extras = notification.extras
        
        // Obtener el título (Nombre del remitente) y texto (mensaje)
        val title = extras.getString("android.title") ?: return
        val text = extras.getCharSequence("android.text")?.toString() ?: return

        // Evitar procesar mensajes enviados por nosotros mismos, o mensajes de sistema (ej: "(Tú) Hola")
        if (title.contains("You:", ignoreCase = true) || title.contains("Tú:", ignoreCase = true) || 
            text.startsWith("Tú:", ignoreCase = true)) {
            Log.d(TAG, "Ignoring self-sent message.")
            return
        }

        // Recuperar la url de n8n desde SharedPreferences
        val prefs = applicationContext.getSharedPreferences("BotPrefs", Context.MODE_PRIVATE)
        val webhookUrl = prefs.getString("webhook_url", "")
        
        if (webhookUrl.isNullOrEmpty()) {
            Log.e(TAG, "Webhook URL no configurada.")
            return
        }

        // Buscar la Action que permite respuesta directa (RemoteInput)
        val replyAction = getReplyAction(notification)
        if (replyAction == null) {
            Log.e(TAG, "No Direct Reply action found in this notification.")
            return
        }

        Log.d(TAG, "Intercepted message from $title: $text")
        
        // Enviar a n8n
        sendToN8n(webhookUrl, title, text) { responseText ->
            if (responseText != null && responseText.isNotEmpty()) {
                sendReply(replyAction, responseText)
            }
        }
    }

    private fun getReplyAction(notification: Notification): Notification.Action? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null) {
                for (remoteInput in remoteInputs) {
                    if (remoteInput.allowFreeFormInput) {
                        return action
                    }
                }
            }
        }
        return null
    }

    private fun sendToN8n(url: String, senderName: String, messageText: String, callback: (String?) -> Unit) {
        val json = JSONObject()
        json.put("sender", senderName)
        json.put("message", messageText)

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Error sending to n8n: \${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "n8n response: $responseBody")
                    
                    // Si n8n devuelve texto plano, usar eso. 
                    // Si devuelve un json, habría que parsearlo. Asumimos n8n responde el texto plano.
                    callback(responseBody)
                } else {
                    Log.e(TAG, "n8n returned error: \${response.code}")
                    callback(null)
                }
            }
        })
    }

    private fun sendReply(action: Notification.Action, msgToReply: String) {
        val remoteInputs = action.remoteInputs ?: return
        val remoteInput = remoteInputs.find { it.allowFreeFormInput } ?: return

        val intent = Intent()
        val bundle = Bundle()
        bundle.putCharSequence(remoteInput.resultKey, msgToReply)
        
        RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

        try {
            action.actionIntent.send(this, 0, intent)
            Log.d(TAG, "Reply sent successfully: $msgToReply")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send reply: \${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Ignorado
    }
}
