package ch.kudoz.app

import android.app.Application
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

class KudozApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: KudozApp
            private set

        val supabase: SupabaseClient by lazy {
            createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
                defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true; explicitNulls = false })
                install(Auth) {
                    alwaysAutoRefresh = true
                    autoLoadFromStorage = true
                }
                install(Postgrest)
            }
        }
    }
}
