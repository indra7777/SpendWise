package com.spendwise.data.remote

import com.spendwise.BuildConfig

object SupabaseConfig {
    val SUPABASE_URL: String = BuildConfig.SUPABASE_URL
    val SUPABASE_ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY
    // IMPORTANT: This must be the WEB Client ID from Google Cloud Console, NOT the Android Client ID.
    val GOOGLE_WEB_CLIENT_ID: String = BuildConfig.GOOGLE_WEB_CLIENT_ID
}
