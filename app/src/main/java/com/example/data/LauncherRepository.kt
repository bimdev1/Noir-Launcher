package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LauncherRepository(private val dao: LauncherDao) {

    // Custom App Settings Flow
    val allCustomAppSettings: Flow<List<CustomAppSetting>> = dao.getAllCustomAppSettings()

    // Map of packageName to CustomAppSetting for fast lookup
    val customAppSettingsMap: Flow<Map<String, CustomAppSetting>> = dao.getAllCustomAppSettings().map { list ->
        list.associateBy { it.packageName }
    }

    suspend fun getAppSetting(packageName: String): CustomAppSetting? {
         return dao.getCustomAppSetting(packageName)
    }

    suspend fun saveAppSetting(setting: CustomAppSetting) {
        dao.insertCustomAppSetting(setting)
    }

    suspend fun toggleFavorite(packageName: String, currentSetting: CustomAppSetting?) {
        val setting = currentSetting?.copy(isFavorite = !currentSetting.isFavorite) 
            ?: CustomAppSetting(packageName = packageName, isFavorite = true)
        dao.insertCustomAppSetting(setting)
    }

    suspend fun toggleHidden(packageName: String, currentSetting: CustomAppSetting?) {
        val setting = currentSetting?.copy(isHidden = !currentSetting.isHidden) 
            ?: CustomAppSetting(packageName = packageName, isHidden = true)
        dao.insertCustomAppSetting(setting)
    }

    suspend fun renameApp(packageName: String, customLabel: String?, currentSetting: CustomAppSetting?) {
        val setting = currentSetting?.copy(customLabel = if (customLabel.isNull_or_Empty()) null else customLabel)
            ?: CustomAppSetting(packageName = packageName, customLabel = if (customLabel.isNull_or_Empty()) null else customLabel)
        dao.insertCustomAppSetting(setting)
    }

    suspend fun updateCategory(packageName: String, category: String?, currentSetting: CustomAppSetting?) {
        val setting = currentSetting?.copy(category = if (category.isNull_or_Empty()) null else category)
            ?: CustomAppSetting(packageName = packageName, category = if (category.isNull_or_Empty()) null else category)
        dao.insertCustomAppSetting(setting)
    }

    suspend fun recordAppUsage(packageName: String, currentSetting: CustomAppSetting?) {
        val setting = currentSetting?.copy(
            usageCount = (currentSetting.usageCount) + 1,
            lastUsedTimestamp = System.currentTimeMillis()
        ) ?: CustomAppSetting(
            packageName = packageName,
            usageCount = 1,
            lastUsedTimestamp = System.currentTimeMillis()
        )
        dao.insertCustomAppSetting(setting)
    }

    // General Settings
    val allLauncherSettings: Flow<Map<String, String>> = dao.getAllLauncherSettings().map { list ->
        list.associate { it.key to it.value }
    }

    suspend fun getSetting(key: String, defaultValue: String): String {
        return dao.getLauncherSetting(key) ?: defaultValue
    }

    suspend fun saveSetting(key: String, value: String) {
        dao.insertLauncherSetting(LauncherSetting(key, value))
    }

    private fun String?.isNull_or_Empty(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
