package com.spendwise.domain.model

import java.math.BigDecimal
import java.time.Instant

data class Transaction(
    val id: String,
    val amount: BigDecimal,
    val currency: String = "INR",
    val merchantName: String,
    val merchantRaw: String,
    val category: Category,
    val subcategory: String? = null,
    val timestamp: Instant,
    val source: TransactionSource,
    val categoryConfidence: Float = 0f,
    val categorySource: CategorySource = CategorySource.UNKNOWN,
    val isSynced: Boolean = false,
    val notes: String? = null,
    val rawNotificationText: String? = null
)

enum class TransactionSource {
    NOTIFICATION,
    MANUAL,
    IMPORT,
    BANK_STATEMENT
}

enum class CategorySource {
    LOCAL_LLM,
    CLOUD_API,
    RULE_BASED,
    USER_CORRECTED,
    UNKNOWN
}

enum class Category(
    val displayName: String,
    val icon: String,
    val colorHex: String
) {
    FOOD("Food & Dining", "restaurant", "#F97316"),
    GROCERIES("Groceries", "shopping_cart", "#22C55E"),
    TRANSPORT("Transport", "directions_car", "#3B82F6"),
    SHOPPING("Shopping", "shopping_bag", "#EC4899"),
    UTILITIES("Utilities", "receipt", "#8B5CF6"),
    ENTERTAINMENT("Entertainment", "movie", "#F43F5E"),
    HEALTH("Health", "medical_services", "#14B8A6"),
    TRANSFERS("Transfers", "swap_horiz", "#6366F1"),
    OTHER("Other", "category", "#6B7280");

    companion object {
        fun fromString(value: String): Category {
            return entries.find {
                it.name.equals(value, ignoreCase = true) ||
                it.displayName.equals(value, ignoreCase = true)
            } ?: OTHER
        }
    }
}

data class TransactionSummary(
    val totalSpent: BigDecimal,
    val transactionCount: Int,
    val byCategory: Map<Category, BigDecimal>,
    val periodStart: Instant,
    val periodEnd: Instant
)
