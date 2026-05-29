package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_app_settings")
data class CustomAppSetting(
    @PrimaryKey val packageName: String,
    val customLabel: String? = null,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val category: String? = null,
    val usageCount: Int = 0,
    val lastUsedTimestamp: Long = 0L
)
