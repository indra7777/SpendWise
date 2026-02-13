package com.rupeelog.agents.core

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.rupeelog.agents.categorization.CategorizationResult
import com.rupeelog.data.local.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local LLM Client for on-device inference using Gemma 3n via MediaPipe
 *
 * This client provides privacy-first, offline AI capabilities for transaction
 * categorization and basic insights without sending data to the cloud.
 *
 * Model Requirements:
 * - Gemma 3n (2B parameters) in compatible format
 * - Download to app's internal storage or assets
 * - Approximately 1-2 GB storage space
 */
@Singleton
class LocalLLMClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences
) {
    private var llmInference: LlmInference? = null
    private var isInitialized = false
    private var initializationError: String? = null

    /**
     * Initialize the local LLM model
     * Call this during app startup or when user enables local LLM
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            val modelPath = getModelPath()
            if (modelPath == null) {
                initializationError = "Model file not found. Please download Gemma 3n model."
                Log.w(TAG, initializationError!!)
                return@withContext false
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .setTemperature(0.3f)
                .setTopK(40)
                .setRandomSeed(42)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isInitialized = true
            initializationError = null
            Log.d(TAG, "Local LLM initialized successfully")
            true
        } catch (e: Exception) {
            initializationError = "Failed to initialize model: ${e.message}"
            Log.e(TAG, initializationError!!, e)
            false
        }
    }

    /**
     * Categorize a transaction using local LLM
     */
    suspend fun categorize(
        merchantText: String,
        amount: Double?
    ): CategorizationResult? = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            if (!initialize()) return@withContext null
        }

        val inference = llmInference ?: return@withContext null

        try {
            val prompt = buildCategorizationPrompt(merchantText, amount)
            val response = inference.generateResponse(prompt)
            parseCategorizationResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "Local LLM categorization failed", e)
            null
        }
    }

    /**
     * Generate a streaming response (for chat-like interactions)
     */
    suspend fun generateResponseStreaming(
        prompt: String,
        onToken: (String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            if (!initialize()) return@withContext null
        }

        val inference = llmInference ?: return@withContext null

        try {
            // Use synchronous generation for simplicity
            val response = inference.generateResponse(prompt)
            onToken(response)
            response
        } catch (e: Exception) {
            Log.e(TAG, "Streaming generation failed", e)
            null
        }
    }

    private fun buildCategorizationPrompt(merchantText: String, amount: Double?): String {
        return """
            |Categorize this transaction:
            |
            |Merchant: "$merchantText"
            |${amount?.let { "Amount: ₹$it" } ?: ""}
            |
            |Categories: FOOD, GROCERIES, TRANSPORT, SHOPPING, UTILITIES, ENTERTAINMENT, HEALTH, TRANSFERS, OTHER
            |
            |Reply with JSON only:
            |{"category": "CATEGORY", "confidence": 0.9, "merchant": "clean name"}
        """.trimMargin()
    }

    private fun parseCategorizationResponse(response: String): CategorizationResult? {
        return try {
            // Extract JSON from response
            val jsonStart = response.indexOf('{')
            val jsonEnd = response.lastIndexOf('}')

            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
                Log.w(TAG, "No valid JSON in response: $response")
                return null
            }

            val jsonStr = response.substring(jsonStart, jsonEnd + 1)
            val json = JSONObject(jsonStr)

            CategorizationResult(
                category = json.getString("category").uppercase(),
                subcategory = json.optString("subcategory").takeIf { it.isNotBlank() },
                merchantName = json.optString("merchant").takeIf { it.isNotBlank() },
                confidence = json.optDouble("confidence", 0.7).toFloat(),
                source = "LOCAL_LLM"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse local LLM response: $response", e)
            null
        }
    }

    /**
     * Get the path to the model file
     * Checks multiple locations:
     * 1. App internal files directory
     * 2. External app-specific directory
     * 3. Downloads folder (for manual installation)
     */
    private fun getModelPath(): String? {
        val modelNames = listOf(
            "gemma-3n-it.bin",
            "gemma-2b-it.bin",
            "gemma3n.bin"
        )

        // Check internal files directory
        for (name in modelNames) {
            val internalPath = File(context.filesDir, "models/$name")
            if (internalPath.exists()) {
                return internalPath.absolutePath
            }
        }

        // Check external files directory
        context.getExternalFilesDir(null)?.let { externalDir ->
            for (name in modelNames) {
                val externalPath = File(externalDir, "models/$name")
                if (externalPath.exists()) {
                    return externalPath.absolutePath
                }
            }
        }

        return null
    }

    /**
     * Check if model is downloaded and ready
     */
    fun isModelAvailable(): Boolean {
        return getModelPath() != null
    }

    /**
     * Get model download URL (placeholder - actual URL depends on model source)
     */
    fun getModelDownloadInfo(): ModelDownloadInfo {
        return ModelDownloadInfo(
            modelName = "Gemma 3n (2B)",
            sizeInMB = 1400,
            downloadUrl = "https://ai.google.dev/gemma/docs/get_started",
            instructions = """
                |1. Visit Google AI Gemma page
                |2. Accept the license agreement
                |3. Download the Gemma 3n model
                |4. Copy to: ${context.filesDir}/models/
            """.trimMargin()
        )
    }

    /**
     * Check if LLM is ready for inference
     */
    fun isReady(): Boolean = isInitialized && llmInference != null

    /**
     * Get initialization error message if any
     */
    fun getError(): String? = initializationError

    /**
     * Release resources
     */
    fun release() {
        llmInference?.close()
        llmInference = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "LocalLLMClient"
    }
}

data class ModelDownloadInfo(
    val modelName: String,
    val sizeInMB: Int,
    val downloadUrl: String,
    val instructions: String
)
