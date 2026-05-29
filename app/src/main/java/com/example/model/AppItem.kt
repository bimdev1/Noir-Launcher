package com.example.model

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.vector.ImageVector

data class AppItem(
    val packageName: String,
    val className: String,
    val originalLabel: String,
    val displayLabel: String,
    val systemIcon: Drawable? = null,
    val mockIcon: ImageVector? = null,
    val iconColor: Long = 0xFF4CAF50, // default green
    val isSystem: Boolean = false,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val isDocked: Boolean = false,
    val category: String? = null,
    val usageCount: Int = 0,
    val lastUsedTimestamp: Long = 0L
)
