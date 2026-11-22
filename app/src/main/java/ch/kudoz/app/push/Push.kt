package ch.kudoz.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ch.kudoz.app.BuildConfig
import ch.kudoz.app.KudozApp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Push über Firebase Cloud Messaging. Firebase kennt nur Geräte-Token und Mitteilungs-ID;
 * den Text holt die App selbst von Kudoz (siehe KudozMessagingService).
 */
object Push {
    const val CHANNEL_ID = "kudoz_mitteilungen"

    /** Link aus einer angetippten Benachrichtigung, den die Oberfläche öffnen soll. */
    val pendingLink = mutableStateOf<String?>(null)

    fun init(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build(),
            )
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Mitteilungen", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Neue Mitteilungen aus Kudoz: Zuweisungen, Kommentare, Bounties"
            },
        )
    }

    /** Letzter Stand der Anmeldung für Push (für die Diagnose in den Einstellungen). */
    val status = mutableStateOf("noch nicht geprüft")

    /** Nach dem Anmelden und bei jedem Start: Token bei Kudoz eintragen. */
    suspend fun register(): Boolean {
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }
            .getOrElse { status.value = "❌ Firebase-Token fehlt: ${it.message ?: it.javaClass.simpleName}"; return false }
        return runCatching { registerToken(token) }
            .onSuccess { status.value = "✅ Gerät bei Kudoz angemeldet (…${token.takeLast(6)})" }
            .onFailure { status.value = "❌ Anmeldung bei Kudoz fehlgeschlagen: ${it.message?.lineSequence()?.firstOrNull() ?: it.javaClass.simpleName}" }
            .isSuccess
    }

    suspend fun registerToken(token: String) {
        KudozApp.supabase.postgrest.rpc("push_register", buildJsonObject { put("_token", token); put("_platform", "android") })
    }

    /** Vor dem Abmelden: Gerät austragen, damit keine Pushes mehr ankommen. */
    suspend fun unregister() {
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            KudozApp.supabase.postgrest.rpc("push_unregister", buildJsonObject { put("_token", token) })
            FirebaseMessaging.getInstance().deleteToken().await()
        }
    }
}
