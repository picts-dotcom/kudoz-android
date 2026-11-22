package ch.kudoz.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import ch.kudoz.app.BuildConfig
import ch.kudoz.app.KudozApp
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import java.security.SecureRandom

/**
 * Anmeldung über https://kudoz.ch/app-login (siehe src/routes/app-login.tsx im Web-Repo).
 *
 * 1. Die App erzeugt einen zufälligen `state` und öffnet die Seite im Browser-Tab.
 * 2. Die Seite meldet frisch mit Google an und gibt die Tokens per intent:// nur an ch.kudoz.app.
 * 3. Die App prüft den `state` und übernimmt die Sitzung (danach frischt nur noch die App auf).
 */
object AuthFlow {
    private const val PREFS = "kudoz_auth_flow"
    private const val KEY_STATE = "state"
    private const val KEY_STARTED = "started"

    fun start(context: Context) {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val state = bytes.joinToString("") { "%02x".format(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATE, state)
            .putLong(KEY_STARTED, System.currentTimeMillis())
            .apply()
        val uri = Uri.parse("${BuildConfig.WEB_URL}/app-login?state=$state")
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    }

    /** true, wenn der Intent eine Anmelde-Rückgabe ist (auch wenn sie ungültig war). */
    fun isAuthIntent(intent: Intent?): Boolean =
        intent?.data?.scheme == "kudoz" && intent.data?.path == "/app-auth"

    sealed interface Result {
        data object Ok : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun complete(context: Context, intent: Intent): Result {
        val raw = intent.getStringExtra("kudoz_auth") ?: return Result.Failed("Keine Anmeldedaten erhalten.")
        val params = Uri.parse("x://x?$raw")
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val expected = prefs.getString(KEY_STATE, null)
        val started = prefs.getLong(KEY_STARTED, 0)
        prefs.edit().clear().apply()

        if (expected == null || params.getQueryParameter("state") != expected) {
            return Result.Failed("Diese Anmeldung wurde nicht in dieser App gestartet. Bitte nochmals anmelden.")
        }
        if (System.currentTimeMillis() - started > 15 * 60_000) {
            return Result.Failed("Die Anmeldung hat zu lange gedauert. Bitte nochmals anmelden.")
        }
        val access = params.getQueryParameter("access_token") ?: return Result.Failed("Token fehlt.")
        val refresh = params.getQueryParameter("refresh_token") ?: return Result.Failed("Token fehlt.")
        val expiresAt = params.getQueryParameter("expires_at")?.toLongOrNull() ?: 0L
        val expiresIn = (expiresAt - System.currentTimeMillis() / 1000).coerceIn(60, 3600)

        return try {
            val auth = KudozApp.supabase.auth
            auth.importSession(
                UserSession(accessToken = access, refreshToken = refresh, expiresIn = expiresIn, tokenType = "bearer", user = null),
                autoRefresh = true,
            )
            auth.retrieveUserForCurrentSession(updateSession = true)
            val mail = auth.currentUserOrNull()?.email.orEmpty().lowercase()
            if (!mail.endsWith("@ruggenacher.ch")) {
                auth.signOut(SignOutScope.LOCAL)
                Result.Failed("Die Kudoz-App ist nur für Konten mit @ruggenacher.ch.")
            } else Result.Ok
        } catch (e: Exception) {
            Result.Failed("Anmeldung fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun signOut() {
        ch.kudoz.app.push.Push.unregister()
        // LOCAL: beendet nur die Sitzung dieser App, nicht die im Browser.
        runCatching { KudozApp.supabase.auth.signOut(SignOutScope.LOCAL) }
    }
}
