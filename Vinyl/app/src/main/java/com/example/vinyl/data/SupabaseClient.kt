package com.example.vinyl.data

import com.example.vinyl.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object Supabase {
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY,
    ) {
        install(Auth) {
            // scheme + host form the redirect URL "com.example.vinyl://auth-callback" that the
            // browser OAuth fallback comes back through. Without both set, supabase-kt never uses
            // a deeplink as the redirect and the fallback can't return a session.
            // Must also be allowlisted in Supabase -> Authentication -> URL Configuration.
            scheme = "com.example.vinyl"
            host = "auth-callback"

            // PKCE over the IMPLICIT default: the callback carries a short-lived code that
            // handleDeeplinks() exchanges for the session, rather than the session itself.
            flowType = FlowType.PKCE

            // Keeps the consent screen in an in-app tab instead of kicking out to the browser app.
            defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
        }
        install(Postgrest)
    }
}
