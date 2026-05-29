package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LauncherDao {
    // Custom App Settings
    @Query("SELECT * FROM custom_app_settings")
    fun getAllCustomAppSettings(): Flow<List<CustomAppSetting>>

    @Query("SELECT * FROM custom_app_settings WHERE packageName = :packageName LIMIT 1")
    suspend fun getCustomAppSetting(packageName: String): CustomAppSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomAppSetting(setting: CustomAppSetting)

    @Update
    suspend fun updateCustomAppSetting(setting: CustomAppSetting)

    @Query("DELETE FROM custom_app_settings WHERE packageName = :packageName")
    suspend fun deleteCustomAppSetting(packageName: String)

    // Launcher Settings
    @Query("SELECT * FROM launcher_settings")
    fun getAllLauncherSettings(): Flow<List<LauncherSetting>>

    @Query("SELECT value FROM launcher_settings WHERE `key` = :key LIMIT 1")
    suspend fun getLauncherSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLauncherSetting(setting: LauncherSetting)

    @Query("DELETE FROM launcher_settings WHERE `key` = :key")
    suspend fun deleteLauncherSetting(key: String)
}
