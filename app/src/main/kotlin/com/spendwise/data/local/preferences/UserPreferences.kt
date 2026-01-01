package com.spendwise.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "user_prefs",
        Context.MODE_PRIVATE
    )

    // Secure preferences (API keys, etc.)
    fun setGeminiApiKey(apiKey: String) {
        securePrefs.edit { putString(KEY_GEMINI_API, apiKey) }
    }

    fun getGeminiApiKey(): String? {
        return securePrefs.getString(KEY_GEMINI_API, null)
    }

    // Budget settings
    fun setMonthlyBudget(amount: Double) {
        prefs.edit { putFloat(KEY_MONTHLY_BUDGET, amount.toFloat()) }
    }

    fun getMonthlyBudget(): Double {
        return prefs.getFloat(KEY_MONTHLY_BUDGET, 40000f).toDouble()
    }

    // Category budgets
    fun setCategoryBudget(category: String, amount: Double) {
        prefs.edit { putFloat("${KEY_CATEGORY_BUDGET_PREFIX}$category", amount.toFloat()) }
    }

    fun getCategoryBudget(category: String): Double? {
        val key = "${KEY_CATEGORY_BUDGET_PREFIX}$category"
        return if (prefs.contains(key)) {
            prefs.getFloat(key, 0f).toDouble()
        } else {
            null
        }
    }

    // AI settings
    fun setLocalLLMEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LOCAL_LLM_ENABLED, enabled) }
    }

    fun isLocalLLMEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOCAL_LLM_ENABLED, true)
    }

    fun setCloudAIEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CLOUD_AI_ENABLED, enabled) }
    }

    fun isCloudAIEnabled(): Boolean {
        return prefs.getBoolean(KEY_CLOUD_AI_ENABLED, true)
    }

    // Notification settings
    fun setBudgetAlertThreshold(percent: Int) {
        prefs.edit { putInt(KEY_BUDGET_ALERT_THRESHOLD, percent) }
    }

    fun getBudgetAlertThreshold(): Int {
        return prefs.getInt(KEY_BUDGET_ALERT_THRESHOLD, 80)
    }

    fun setDailyInsightsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DAILY_INSIGHTS, enabled) }
    }

    fun isDailyInsightsEnabled(): Boolean {
        return prefs.getBoolean(KEY_DAILY_INSIGHTS, true)
    }

    // App settings
    fun setCurrency(currency: String) {
        prefs.edit { putString(KEY_CURRENCY, currency) }
    }

    fun getCurrency(): String {
        return prefs.getString(KEY_CURRENCY, "INR") ?: "INR"
    }

    fun setDarkModeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DARK_MODE, enabled) }
    }

    fun isDarkModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_DARK_MODE, false)
    }

    // First launch / onboarding
    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, completed) }
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    // Server settings
    fun setServerPort(port: Int) {
        prefs.edit { putInt(KEY_SERVER_PORT, port) }
    }

    fun getServerPort(): Int {
        return prefs.getInt(KEY_SERVER_PORT, 8080)
    }

    // Clear all data
    fun clearAll() {
        prefs.edit { clear() }
        securePrefs.edit { clear() }
    }

    companion object {
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_MONTHLY_BUDGET = "monthly_budget"
        private const val KEY_CATEGORY_BUDGET_PREFIX = "category_budget_"
        private const val KEY_LOCAL_LLM_ENABLED = "local_llm_enabled"
        private const val KEY_CLOUD_AI_ENABLED = "cloud_ai_enabled"
        private const val KEY_BUDGET_ALERT_THRESHOLD = "budget_alert_threshold"
        private const val KEY_DAILY_INSIGHTS = "daily_insights_enabled"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_SERVER_PORT = "server_port"
    }
}
