package ch.kudoz.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import ch.kudoz.app.push.KudozMessagingService
import ch.kudoz.app.push.Push
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import ch.kudoz.app.data.AuthFlow
import ch.kudoz.app.ui.AppShell
import ch.kudoz.app.ui.KudozTheme
import ch.kudoz.app.ui.LoginScreen
import ch.kudoz.app.ui.SplashScreen
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val loginMessage = mutableStateOf<String?>(null)
    private val loginBusy = mutableStateOf(false)
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            KudozTheme {
                val status by KudozApp.supabase.auth.sessionStatus.collectAsState()
                when (status) {
                    is SessionStatus.Authenticated -> {
                        LaunchedEffect(Unit) {
                            if (Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            Push.register()
                        }
                        AppShell()
                    }
                    is SessionStatus.Initializing -> SplashScreen()
                    else -> LoginScreen(
                        message = loginMessage.value,
                        busy = loginBusy.value,
                        onLogin = { loginMessage.value = null; AuthFlow.start(this) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(KudozMessagingService.EXTRA_LINK)?.let { Push.pendingLink.value = it }
        if (intent == null || !AuthFlow.isAuthIntent(intent)) return
        loginBusy.value = true
        lifecycleScope.launch {
            when (val r = AuthFlow.complete(this@MainActivity, intent)) {
                is AuthFlow.Result.Ok -> loginMessage.value = null
                is AuthFlow.Result.Failed -> loginMessage.value = r.reason
            }
            loginBusy.value = false
        }
        // Tokens nicht im Intent liegen lassen (z.B. bei Neustart der Activity).
        setIntent(Intent(this, MainActivity::class.java))
    }
}
