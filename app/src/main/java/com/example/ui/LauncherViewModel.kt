package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CustomAppSetting
import com.example.data.LauncherRepository
import com.example.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = LauncherRepository(database.launcherDao())

    // Search queries
    val searchQuery = MutableStateFlow("")

    // Active Category Filter ("All", "Favorites", "Social", "Work", "Utilities", "Games", "My Apps")
    val selectedCategory = MutableStateFlow("All")

    // Real system applications fetched and cached
    private val _systemApps = MutableStateFlow<List<AppItem>>(emptyList())

    // Merge of system applications and Fossify style styled mock tools
    val allApps: StateFlow<List<AppItem>> = combine(
        _systemApps,
        repository.customAppSettingsMap,
        searchQuery,
        selectedCategory
    ) { systemAppsList, customAppSettingsMap, query, category ->
        val mergedList = mutableListOf<AppItem>()

        // 1. Prepare Core / Mock applications to represent Fossify philosophy
        val mockApps = getMockFossifyApps()

        // 2. Add mock apps (prevent duplicates by package name if they exist in system)
        val systemAppsPackageNames = systemAppsList.map { it.packageName }.toSet()
        val uniqueMockApps = mockApps.filter { it.packageName !in systemAppsPackageNames }

        mergedList.addAll(systemAppsList)
        mergedList.addAll(uniqueMockApps)

        // 3. Enrich application items with Room settings (custom labels, pin states, hidden, usage statistics)
        val enrichedList = mergedList.map { item ->
            val custom = customAppSettingsMap[item.packageName]
            item.copy(
                displayLabel = custom?.customLabel ?: item.originalLabel,
                isFavorite = custom?.isFavorite ?: false,
                isHidden = custom?.isHidden ?: false,
                isDocked = custom?.isDocked ?: false,
                category = custom?.category ?: getFallbackCategory(item.packageName),
                usageCount = custom?.usageCount ?: 0,
                lastUsedTimestamp = custom?.lastUsedTimestamp ?: 0L
            )
        }

        // 4. Apply filters
        var filtered = enrichedList.filter { app ->
            val matchesSearch = app.displayLabel.contains(query, ignoreCase = true) || 
                                app.packageName.contains(query, ignoreCase = true)
            matchesSearch
        }

        filtered = when (category) {
            "All" -> filtered.filter { !it.isHidden }
            "Favorites" -> filtered.filter { it.isFavorite && !it.isHidden }
            "Hidden" -> filtered.filter { it.isHidden }
            else -> filtered.filter { it.category == category && !it.isHidden }
        }

        // Sort by display label alphabetically
        filtered.sortedWith(compareBy { it.displayLabel.lowercase() })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // General app configuration streams
    val launcherSettings: StateFlow<Map<String, String>> = repository.allLauncherSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    init {
        loadSystemInstalledApplications()
        initializeDefaultSettings()
    }

    private fun loadSystemInstalledApplications() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val parsedApps = resolveInfos.mapNotNull { info ->
                val packageName = info.activityInfo.packageName
                if (packageName == getApplication<Application>().packageName) {
                    // Skip our own launcher from listing inside itself
                    return@mapNotNull null
                }
                val className = info.activityInfo.name
                val originalLabel = info.loadLabel(pm).toString()
                val systemIcon = info.loadIcon(pm)

                AppItem(
                    packageName = packageName,
                    className = className,
                    originalLabel = originalLabel,
                    displayLabel = originalLabel,
                    systemIcon = systemIcon,
                    isSystem = true
                )
            }

            _systemApps.value = parsedApps
        }
    }

    private fun initializeDefaultSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            // Apply fallback settings values to the local db if they don't exist
            if (repository.getSetting("grid_columns", "").isEmpty()) {
                repository.saveSetting("grid_columns", "4")
            }
            if (repository.getSetting("show_labels", "").isEmpty()) {
                repository.saveSetting("show_labels", "true")
            }
            if (repository.getSetting("theme_accent_color", "").isEmpty()) {
                repository.saveSetting("theme_accent_color", "0xFFD0E4FF") // Elegant Dark light blue accent
            }
            if (repository.getSetting("wallpaper_type", "").isEmpty()) {
                repository.saveSetting("wallpaper_type", "Solid")
            }
            if (repository.getSetting("wallpaper_preset", "").isEmpty()) {
                repository.saveSetting("wallpaper_preset", "Elegant Dark")
            }
        }
    }

    // Interactive operations
    fun toggleAppFavorite(packageName: String, currentSetting: CustomAppSetting?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(packageName, currentSetting)
        }
    }

    fun toggleAppHidden(packageName: String, currentSetting: CustomAppSetting?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleHidden(packageName, currentSetting)
        }
    }

    fun toggleAppDocked(packageName: String, currentSetting: CustomAppSetting?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleDocked(packageName, currentSetting)
        }
    }

    fun renameApplication(packageName: String, newName: String?, currentSetting: CustomAppSetting?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.renameApp(packageName, newName, currentSetting)
        }
    }

    fun assignAppCategory(packageName: String, categoryName: String?, currentSetting: CustomAppSetting?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCategory(packageName, categoryName, currentSetting)
        }
    }

    fun recordLaunchedAppUsage(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentSetting = repository.getAppSetting(packageName)
            repository.recordAppUsage(packageName, currentSetting)
        }
    }

    fun saveLauncherConfigSetting(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting(key, value)
        }
    }

    // Helper functions
    private fun getFallbackCategory(packageName: String): String {
        return when {
            packageName.contains("chat", true) || packageName.contains("social", true) || packageName.contains("messenger", true) || packageName.contains("whatsapp", true) || packageName.contains("facebook", true) -> "Social"
            packageName.contains("play", true) || packageName.contains("game", true) || packageName.contains("arcade", true) -> "Games"
            packageName.contains("gmail", true) || packageName.contains("email", true) || packageName.contains("office", true) || packageName.contains("calendar", true) -> "Work"
            packageName.contains("camera", true) || packageName.contains("gallery", true) || packageName.contains("photos", true) || packageName.contains("music", true) || packageName.contains("youtube", true) -> "Media"
            else -> "Utilities"
        }
    }

    private fun getMockFossifyApps(): List<AppItem> {
        return listOf(
            AppItem(
                packageName = "org.fossify.gallery",
                className = "",
                originalLabel = "Fossify Gallery",
                displayLabel = "Fossify Gallery",
                mockIcon = Icons.Default.Star,
                iconColor = 0xFFEC5A5A, // red-orange
                category = "Media"
            ),
            AppItem(
                packageName = "org.fossify.messenger",
                className = "",
                originalLabel = "Fossify Messages",
                displayLabel = "Fossify Messages",
                mockIcon = Icons.Default.Send,
                iconColor = 0xFF4361EE, // vibrant blue
                category = "Social"
            ),
            AppItem(
                packageName = "org.fossify.contacts",
                className = "",
                originalLabel = "Fossify Contacts",
                displayLabel = "Fossify Contacts",
                mockIcon = Icons.Default.Person,
                iconColor = 0xFF7209B7, // deep purple
                category = "Utilities"
            ),
            AppItem(
                packageName = "org.fossify.phone",
                className = "",
                originalLabel = "Fossify Phone",
                displayLabel = "Fossify Phone",
                mockIcon = Icons.Default.ThumbUp, // Dial gesture standard 
                iconColor = 0xFF2EC4B6, // bright teal
                category = "Utilities"
            ),
            AppItem(
                packageName = "org.fossify.files",
                className = "",
                originalLabel = "Fossify Files",
                displayLabel = "Fossify Files",
                mockIcon = Icons.Default.List,
                iconColor = 0xFFFF9F1C, // golden orange
                category = "Utilities"
            ),
            AppItem(
                packageName = "org.fossify.notes",
                className = "",
                originalLabel = "Fossify Notes",
                displayLabel = "Fossify Notes",
                mockIcon = Icons.Default.Edit,
                iconColor = 0xFF31572C, // forest sage green
                category = "Work"
            ),
            AppItem(
                packageName = "org.fossify.calculator",
                className = "",
                originalLabel = "Fossify Calculator",
                displayLabel = "Fossify Calculator",
                mockIcon = Icons.Default.Add,
                iconColor = 0xFF9D4EDD, // modern magenta
                category = "Utilities"
            ),
            AppItem(
                packageName = "org.fossify.browser",
                className = "",
                originalLabel = "Fossify Browser",
                displayLabel = "Fossify Browser",
                mockIcon = Icons.Default.Home,
                iconColor = 0xFF0077B6, // cobalt ocean
                category = "Utilities"
            ),
            AppItem(
                packageName = "org.fossify.settings",
                className = "",
                originalLabel = "Fossify Settings",
                displayLabel = "Fossify Settings",
                mockIcon = Icons.Default.Settings,
                iconColor = 0xFF6C757D, // space gray
                category = "Utilities"
            )
        )
    }

    // --- Live Weather System ---
    val weatherTemp = MutableStateFlow<String?>("72°F")
    val weatherLocality = MutableStateFlow<String>("Live Weather")
    val weatherError = MutableStateFlow<String?>(null)
    val weatherLoading = MutableStateFlow<Boolean>(false)

    fun updateWeatherForLocation(latitude: Double, longitude: Double) {
        weatherLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Fetch location city name via Geocoder
                try {
                    val geocoder = android.location.Geocoder(getApplication<Application>(), java.util.Locale.getDefault())
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val city = address.locality ?: address.subAdminArea ?: address.adminArea ?: "Live Location"
                        weatherLocality.value = city
                    }
                } catch (e: Exception) {
                    // Fallback
                }

                // Query weather forecast from Open-Meteo
                val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current_weather=true&temperature_unit=fahrenheit"
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(urlString).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw java.io.IOException("Unexpected response: $response")
                    val body = response.body?.string() ?: ""
                    val json = org.json.JSONObject(body)
                    val current = json.getJSONObject("current_weather")
                    val temp = current.getDouble("temperature")
                    val rounded = Math.round(temp)
                    weatherTemp.value = "${rounded}°F"
                    weatherError.value = null
                }
            } catch (e: Exception) {
                weatherError.value = e.localizedMessage
                // Keep default or previous temp if fails, but set error
            } finally {
                weatherLoading.value = false
            }
        }
    }
}
