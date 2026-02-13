package com.rupeelog.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["category"]),
        Index(value = ["merchant_name"]),
        Index(value = ["is_synced"])
    ]
)
data class TransactionEntity(
    @PrimaryKey
    val id: String,

    val amount: Double,

    val currency: String = "INR",

    @ColumnInfo(name = "merchant_name")
    val merchantName: String,

    @ColumnInfo(name = "merchant_raw")
    val merchantRaw: String,

    val category: String,

    val subcategory: String? = null,

    val timestamp: Long,

    val source: String,

    @ColumnInfo(name = "category_confidence")
    val categoryConfidence: Float = 0f,

    @ColumnInfo(name = "category_source")
    val categorySource: String = "UNKNOWN",

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,

    val notes: String? = null,

    @ColumnInfo(name = "raw_notification_text")
    val rawNotificationText: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "category_rules",
    indices = [Index(value = ["pattern"], unique = true)]
)
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val pattern: String,

    val category: String,

    val subcategory: String? = null,

    val confidence: Float = 0.9f,

    @ColumnInfo(name = "is_user_created")
    val isUserCreated: Boolean = false,

    @ColumnInfo(name = "match_count")
    val matchCount: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["category", "period_start"], unique = true)]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val category: String? = null,  // null = overall budget

    val amount: Double,

    @ColumnInfo(name = "period_start")
    val periodStart: Long,

    @ColumnInfo(name = "period_end")
    val periodEnd: Long,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)

@Entity(
    tableName = "insights",
    indices = [Index(value = ["type"]), Index(value = ["created_at"])]
)
data class InsightEntity(
    @PrimaryKey
    val id: String,

    val type: String,

    val title: String,

    val description: String,

    val action: String? = null,

    val priority: String,

    @ColumnInfo(name = "related_category")
    val relatedCategory: String? = null,

    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,

    @ColumnInfo(name = "is_dismissed")
    val isDismissed: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null
)

@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["status"]), Index(value = ["created_at"])]
)
data class SyncQueueEntity(
    @PrimaryKey
    val id: String,

    val type: String,

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    val payload: String,

    val status: String = "PENDING",

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "processed_at")
    val processedAt: Long? = null
)
