package ch.kudoz.app.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ch.kudoz.app.KudozApp
import ch.kudoz.app.MainActivity
import ch.kudoz.app.R
import ch.kudoz.app.data.Notification
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class KudozMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            if (KudozApp.supabase.auth.currentSessionOrNull() != null) runCatching { Push.registerToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "kudoz_notification") return
        val id = message.data["id"] ?: return
        // Der Dienst hat ~10 s Zeit; Text direkt bei Kudoz holen (Firebase kennt ihn nicht).
        runBlocking {
            val n = withTimeoutOrNull(8_000) {
                val auth = KudozApp.supabase.auth
                auth.sessionStatus.first { it !is SessionStatus.Initializing }
                if (auth.currentSessionOrNull() == null) return@withTimeoutOrNull null
                runCatching {
                    KudozApp.supabase.from("notifications").select { filter { eq("id", id) } }.decodeSingle<Notification>()
                }.getOrNull()
            }
            if (n?.readAt != null) return@runBlocking
            show(id, n?.title ?: "Du hast eine neue Mitteilung.", n?.link)
        }
    }

    private fun show(id: String, text: String, link: String?) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_ID, id)
            link?.let { putExtra(EXTRA_LINK, it) }
        }
        val pending = PendingIntent.getActivity(this, id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, Push.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kudoz)
            .setContentTitle("Kudoz")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(this).notify(id.hashCode(), notification)
    }

    companion object {
        const val EXTRA_LINK = "kudoz_link"
        const val EXTRA_NOTIFICATION_ID = "kudoz_notification_id"
    }
}
