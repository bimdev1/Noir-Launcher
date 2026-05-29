package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.os.Build
import android.provider.Settings
import com.google.android.gms.location.LocationServices
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.CustomAppSetting
import com.example.model.AppItem
import com.example.ui.LauncherViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: LauncherViewModel = viewModel()
                LauncherHomeScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun LauncherHomeScreen(viewModel: LauncherViewModel) {
    val context = LocalContext.current
    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val settings by viewModel.launcherSettings.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()

    val weatherTemp by viewModel.weatherTemp.collectAsStateWithLifecycle()
    val weatherLocality by viewModel.weatherLocality.collectAsStateWithLifecycle()
    val weatherLoading by viewModel.weatherLoading.collectAsStateWithLifecycle()

    val locationPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.updateWeatherForLocation(location.latitude, location.longitude)
                } else {
                    val priority = com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY
                    fusedLocationClient.getCurrentLocation(priority, null)
                        .addOnSuccessListener { loc ->
                            if (loc != null) {
                                viewModel.updateWeatherForLocation(loc.latitude, loc.longitude)
                            }
                        }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(locationPermissionState.allPermissionsGranted) {
        if (locationPermissionState.allPermissionsGranted) {
            fetchCurrentLocation()
        }
    }

    var isDrawerOpen by remember { mutableStateOf(false) }
    var activeAppForOptions by remember { mutableStateOf<AppItem?>(null) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    // Settings data values from db state
    val columns = settings["grid_columns"]?.toIntOrNull() ?: 4
    val showLabels = settings["show_labels"]?.toBoolean() ?: true
    val accentColorHex = settings["theme_accent_color"] ?: "0xFFEB5E28"
    val accentColor = Color(accentColorHex.toLongOrNull() ?: 0xFFEB5E28.toLong())
    val wallpaperType = settings["wallpaper_type"] ?: "Gradient"
    val wallpaperPreset = settings["wallpaper_preset"] ?: "Cosmic Night"
    val customGreet = settings["user_greet_name"] ?: "Tim"

    // Real-time time updater
    var currentTimeString by remember { mutableStateOf("00:00:00") }
    var currentDateString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            currentTimeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(cal.time)
            currentDateString = SimpleDateFormat("EEEE, d MMMM YYYY", Locale.getDefault()).format(cal.time)
            delay(1000)
        }
    }

    // List of active favorite applications to show on the desktop workspace
    val favoriteApps = remember(allApps) {
        allApps.filter { it.isFavorite }
    }

    // Visual Brush setup for dynamic backgrounds
    val backgroundBrush = getBackgroundBrush(wallpaperType, wallpaperPreset)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (wallpaperType == "Custom Image") Color.Black else Color.Transparent)
            .then(
                if (wallpaperType != "Custom Image") {
                    Modifier.background(backgroundBrush)
                } else {
                    Modifier
                }
            )
    ) {
        if (wallpaperType == "Custom Image") {
            coil.compose.AsyncImage(
                model = if (wallpaperPreset.startsWith("http://") || wallpaperPreset.startsWith("https://")) {
                    wallpaperPreset
                } else {
                    "https://images.unsplash.com/photo-1518531933037-91b2f5f229cc?w=1080"
                },
                contentDescription = "Custom Wallpaper Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            // Overlay dimming layer for text contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }
        // SYSTEM SAFE AREAS CONTAINER
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!isDrawerOpen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Glassmorphic Elegant Dark Dock Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(32.dp))
                                .background(Color(0x992F3033)) // #2F3033 at 60% opacity
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(32.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dial app shortcut (Highlighted in soft light-blue)
                                QuickMainDockCallShortcut(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL)
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Dialer app not found", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )

                                // Messages app shortcut
                                QuickSubDockShortcut(
                                    icon = Icons.Default.Send,
                                    label = "Messages",
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_MAIN).apply {
                                            addCategory(Intent.CATEGORY_APP_MESSAGING)
                                        }
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Messaging app not found", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )

                                // Contacts app shortcut
                                QuickSubDockShortcut(
                                    icon = Icons.Default.Person,
                                    label = "Contacts",
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_MAIN).apply {
                                            addCategory(Intent.CATEGORY_APP_CONTACTS)
                                        }
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Contacts app not found", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )

                                // Settings application launcher shortcut
                                QuickSubDockShortcut(
                                    icon = Icons.Default.Settings,
                                    label = "Settings",
                                    onClick = { isSettingsOpen = true }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Bottom modern navigation bar line pill
                        Box(
                            modifier = Modifier
                                .width(96.dp)
                                .height(4.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        )
                    }
                }
            }
        ) { innerPadding ->
            // MAIN HOME CONTAINER
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ELEGANT MINIMAL DISPLAY HEADER - Left-aligned, clean structure
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(top = 32.dp, bottom = 12.dp, start = 8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    val weekdayFormat = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }
                    val dateFormat = remember { SimpleDateFormat("MMMM d", Locale.getDefault()) }
                    
                    val now = Calendar.getInstance().time
                    val weekdayString = weekdayFormat.format(now)
                    val dateString = dateFormat.format(now)

                    // Large dynamic weekday title standard
                    Text(
                        text = weekdayString,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Light,
                        color = Color(0xFFD0E4FF),
                        letterSpacing = (-0.5).sp
                    )

                    // Date underneath
                    Text(
                        text = dateString,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFFBBC7DB).copy(alpha = 0.9f),
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    // Unified Weather + Time + Greet row
                    Row(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (!locationPermissionState.allPermissionsGranted) {
                                    locationPermissionState.launchMultiplePermissionRequest()
                                } else {
                                    fetchCurrentLocation()
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (weatherLoading) Icons.Default.Refresh else Icons.Default.Star,
                            contentDescription = "Weather info icon",
                            tint = Color(0xFFD0E4FF),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (weatherLoading) "Locating..." else (weatherTemp ?: "72°F"),
                            color = Color(0xFFE2E2E6),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "•",
                            color = Color(0xFFBBC7DB).copy(alpha = 0.4f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = weatherLocality,
                            color = Color(0xFFBBC7DB),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "•",
                            color = Color(0xFFBBC7DB).copy(alpha = 0.4f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "User",
                            tint = Color(0xFFBBC7DB),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Hi, $customGreet",
                            color = Color(0xFFBBC7DB),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // INTEGRATED ELEGANT SEARCH BOX
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(top = 16.dp, bottom = 22.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .background(Color(0xFF2F3033))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(27.dp))
                        .clickable { isDrawerOpen = true }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search apps icon",
                            tint = Color(0xFFBBC7DB),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Search apps...",
                            color = Color(0xFFBBC7DB),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Swipe up drawer helper icon",
                            tint = Color(0xFFBBC7DB).copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // FAVORITES HOMESCREEN DOCK GRID
                if (favoriteApps.isNotEmpty()) {
                    Text(
                        text = "FAVORITE APPS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 1.5.sp,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(top = 20.dp, bottom = 8.dp),
                        textAlign = TextAlign.Start
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(12.dp)
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(favoriteApps) { app ->
                                AppLayoutGridItem(
                                    app = app,
                                    showLabel = showLabels,
                                    textColor = Color.White,
                                    onLaunch = {
                                        launchApp(context, app, viewModel)
                                    },
                                    onLongClick = {
                                        activeAppForOptions = app
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Quick placeholder for empty favorites
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(top = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Empty pinned apps",
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No pinned shortcuts yet.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Open all apps, long press any tool, and select 'Pin to Home' to pin your main apps here.",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // SLIDE UP ALL APPS DRAWER OVERLAY
        AnimatedVisibility(
            visible = isDrawerOpen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(stiffness = 300f)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(stiffness = 300f)
            ) + fadeOut()
        ) {
            AllAppsDrawerPanel(
                viewModel = viewModel,
                allApps = allApps,
                query = query,
                currentCategory = currentCategory,
                columns = columns,
                showLabels = showLabels,
                accentColor = accentColor,
                onClose = { isDrawerOpen = false },
                onLaunchApp = { app ->
                    launchApp(context, app, viewModel)
                    isDrawerOpen = false
                },
                onLongClickApp = { app ->
                    activeAppForOptions = app
                }
            )
        }

        // CONTEXT DIALOG FOR APP OPTIONS (PINS, RENAME, DIRECTORY, REMOVE)
        activeAppForOptions?.let { app ->
            AppOptionsDialog(
                app = app,
                accentColor = accentColor,
                onDismiss = { activeAppForOptions = null },
                onToggleFavorite = {
                    viewModel.toggleAppFavorite(app.packageName, CustomAppSetting(packageName = app.packageName, isFavorite = app.isFavorite))
                    activeAppForOptions = null
                    Toast.makeText(context, if (app.isFavorite) "Removed from favorites" else "Added to favorites", Toast.LENGTH_SHORT).show()
                },
                onToggleHidden = {
                    viewModel.toggleAppHidden(app.packageName, CustomAppSetting(packageName = app.packageName, isHidden = app.isHidden))
                    activeAppForOptions = null
                    Toast.makeText(context, if (app.isHidden) "Restored app to list" else "App hidden from list", Toast.LENGTH_SHORT).show()
                },
                onRename = { customLabel ->
                    viewModel.renameApplication(app.packageName, customLabel, CustomAppSetting(packageName = app.packageName))
                    activeAppForOptions = null
                    Toast.makeText(context, "App renamed successfully", Toast.LENGTH_SHORT).show()
                },
                onSetCategory = { categoryName ->
                    viewModel.assignAppCategory(app.packageName, categoryName, CustomAppSetting(packageName = app.packageName))
                    activeAppForOptions = null
                    Toast.makeText(context, "Added to category: $categoryName", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // FULL SETTINGS VIEW BUILDER SCREEN
        if (isSettingsOpen) {
            LauncherSettingsDialog(
                viewModel = viewModel,
                currentColumns = columns,
                currentShowLabels = showLabels,
                currentAccentColor = accentColorHex,
                currentWallpaperType = wallpaperType,
                currentWallpaperPreset = wallpaperPreset,
                currentGreet = customGreet,
                allApps = allApps,
                onDismiss = { isSettingsOpen = false }
            )
        }
    }
}

// QUICK SHORTCUT ICON COMPOSABLE
@Composable
fun QuickDockShortcut(
    label: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.08f), CircleShape)
            .size(46.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

// CUSTOM LAUNCH HELPER WITH USAGE METRICS RECORDING
fun launchApp(context: Context, app: AppItem, viewModel: LauncherViewModel) {
    viewModel.recordLaunchedAppUsage(app.packageName)
    if (app.isSystem) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to launch application", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Could not resolve launch intent", Toast.LENGTH_SHORT).show()
        }
    } else {
        // Mock app launched indicator
        Toast.makeText(context, "Launching Mock Core ${app.displayLabel} successfully!", Toast.LENGTH_SHORT).show()
    }
}

// GRID REUSABLE ICON AND LABEL VIEWER COMPOSABLE
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppLayoutGridItem(
    app: AppItem,
    showLabel: Boolean,
    textColor: Color,
    onLaunch: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onLaunch,
                onLongClick = onLongClick
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = Color(0xFF3B4858),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (app.systemIcon != null) {
                // Highly robust native imageView wrapper
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageDrawable(app.systemIcon)
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    update = { view ->
                        view.setImageDrawable(app.systemIcon)
                    }
                )
            } else if (app.mockIcon != null) {
                Icon(
                    imageVector = app.mockIcon,
                    contentDescription = app.displayLabel,
                    tint = Color(0xFFD0E4FF), // Unified light-blue Elegant Dark accent
                    modifier = Modifier.size(28.dp)
                )
            } else {
                IconButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.background(Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Unknown app",
                        tint = Color(0xFFD0E4FF)
                    )
                }
            }
        }

        if (showLabel) {
            Text(
                text = app.displayLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE2E2E6),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
            )
        }
    }
}

// ALL APPS PANEL SCREEN
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllAppsDrawerPanel(
    viewModel: LauncherViewModel,
    allApps: List<AppItem>,
    query: String,
    currentCategory: String,
    columns: Int,
    showLabels: Boolean,
    accentColor: Color,
    onClose: () -> Unit,
    onLaunchApp: (AppItem) -> Unit,
    onLongClickApp: (AppItem) -> Unit
) {
    val categories = listOf("All", "Favorites", "Social", "Work", "Utilities", "Games", "Media")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(top = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // DRAWER HEADER CONTROLS
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .testTag("close_drawer_button")
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Beautifully stylized custom search bar
                TextField(
                    value = query,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text("Search system tools...", color = Color.White.copy(alpha = 0.4f)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("app_search_field")
                        .height(52.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.1f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                        focusedIndicatorColor = accentColor,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(26.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White.copy(alpha = 0.5f)
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    singleLine = true
                )
            }

            // HORIZONTAL CATEGORIES BAR FILTERS Row
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    val isSelected = currentCategory == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) accentColor else Color.White.copy(alpha = 0.1f)
                            )
                            .clickable { viewModel.selectedCategory.value = category }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = category,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // MAIN APPS DISPLAY LIST
            if (allApps.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("apps_grid")
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(allApps) { app ->
                        AppLayoutGridItem(
                            app = app,
                            showLabel = showLabels,
                            textColor = Color.White.copy(alpha = 0.9f),
                            onLaunch = { onLaunchApp(app) },
                            onLongClick = { onLongClickApp(app) }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "No results",
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No applications found.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Try searching with a different request or clearing search keyword filters.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// APP CONTEXT MENU COMPOSABLE
@Composable
fun AppOptionsDialog(
    app: AppItem,
    accentColor: Color,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit,
    onRename: (String) -> Unit,
    onSetCategory: (String?) -> Unit
) {
    var isRenamingMode by remember { mutableStateOf(false) }
    var renameInputString by remember { mutableStateOf(app.displayLabel) }
    var isCategorizeMode by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .testTag("app_options_dialog"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header details
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(accentColor.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (app.systemIcon != null) {
                        AndroidView(
                            factory = { ctx ->
                                ImageView(ctx).apply {
                                    setImageDrawable(app.systemIcon)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        )
                    } else if (app.mockIcon != null) {
                        Icon(
                            imageVector = app.mockIcon,
                            contentDescription = app.displayLabel,
                            tint = accentColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Text(
                    text = app.displayLabel,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = app.packageName,
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Divider(color = Color.White.copy(alpha = 0.1f))

                if (isRenamingMode) {
                    // Inline app rename controller
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "Choose custom label nickname:",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextField(
                            value = renameInputString,
                            onValueChange = { renameInputString = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("rename_input"),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                                focusedIndicatorColor = accentColor
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { isRenamingMode = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                            ) {
                                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                            }

                            Button(
                                onClick = { onRename(renameInputString) },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                modifier = Modifier.weight(1f).testTag("save_rename_button")
                            ) {
                                Text("Save Label")
                            }
                        }
                    }
                } else if (isCategorizeMode) {
                    // Category manager selections
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Set Application Category Folder:",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val cats = listOf("Social", "Work", "Utilities", "Games", "Media")
                        cats.forEach { cat ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSetCategory(cat) }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(cat, color = Color.White, fontSize = 14.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { onSetCategory(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reset Category to Default", color = Color.White)
                        }

                        Button(
                            onClick = { isCategorizeMode = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                } else {
                    // Standard action menu items list
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ContextMenuItem(
                            label = if (app.isFavorite) "Unhome / Unpin from Home" else "Pin to Home View",
                            icon = if (app.isFavorite) Icons.Default.Close else Icons.Default.Star,
                            iconColor = if (app.isFavorite) Color.Red else Color.Yellow,
                            onClick = onToggleFavorite
                        )

                        ContextMenuItem(
                            label = "Rename Custom Nickname",
                            icon = Icons.Default.Edit,
                            onClick = { isRenamingMode = true }
                        )

                        ContextMenuItem(
                            label = "Set Folder Category",
                            icon = Icons.Default.Menu,
                            onClick = { isCategorizeMode = true }
                        )

                        ContextMenuItem(
                            label = if (app.isHidden) "Restore/Unhide App" else "Hide App From List",
                            icon = Icons.Default.Lock,
                            iconColor = if (app.isHidden) Color.Green else Color.LightGray,
                            onClick = onToggleHidden
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close Config Menu", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContextMenuItem(
    label: String,
    icon: ImageVector,
    iconColor: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// SYSTEM PRESET BACKGROUND BRUSH CALCULATOR
fun getBackgroundBrush(type: String, preset: String): Brush {
    if (type == "Solid") {
        val color = when (preset) {
            "Elegant Dark" -> Color(0xFF1A1C1E)
            "Ivory White" -> Color(0xFFF9F6F0)
            "Pitch Black" -> Color(0xFF0F0F12)
            "Indigo Midnight" -> Color(0xFF0D1B2A)
            "Pastel Sage" -> Color(0xFFCAD2C5)
            else -> Color(0xFF1A1C1E) // Elegant Dark fallback
        }
        return Brush.linearGradient(listOf(color, color))
    }

    return when (preset) {
        "Sunset Dusk" -> Brush.linearGradient(listOf(Color(0xFFE0533C), Color(0xFF8E217B)))
        "Forest Aurora" -> Brush.linearGradient(listOf(Color(0xFF0F5132), Color(0xFF1D976C)))
        "Oceanic Breeze" -> Brush.linearGradient(listOf(Color(0xFF023E8A), Color(0xFF0096C7)))
        "Cosmic Amethyst" -> Brush.radialGradient(listOf(Color(0xFF3C096C), Color(0xFF10002B)))
        else -> Brush.radialGradient(
            colors = listOf(Color(0xFF2E0854), Color(0xFF0F051D)),
        ) // default "Cosmic Night"
    }
}

// CORE CUSTOMIZATION OPTIONS AND SETTINGS PANEL COMPOSABLE
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LauncherSettingsDialog(
    viewModel: LauncherViewModel,
    currentColumns: Int,
    currentShowLabels: Boolean,
    currentAccentColor: String,
    currentWallpaperType: String,
    currentWallpaperPreset: String,
    currentGreet: String,
    allApps: List<AppItem>,
    onDismiss: () -> Unit
) {
    var greetText by remember { mutableStateOf(currentGreet) }
    var activeTab by remember { mutableStateOf("Options") } // Options vs Wallpapers vs Hiddens

    val hiddenInstalledApps = remember(allApps) {
        allApps.filter { it.isHidden }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .testTag("launcher_settings_dialog"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161519)),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Customize Launcher",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close settings", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom Tabs picker inside settings
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Options", "Wallpapers", "Hidden Manager").forEach { tab ->
                        val isSel = activeTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { activeTab = tab }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                color = if (isSel) Color.White else Color.White.copy(alpha = 0.6f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Scrollable container for properties
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (activeTab) {
                        "Options" -> {
                            val isDefaultLauncher = remember {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    val rm = viewModel.getApplication<Application>().getSystemService(Context.ROLE_SERVICE) as? RoleManager
                                    rm?.isRoleHeld(RoleManager.ROLE_HOME) ?: false
                                } else {
                                    false
                                }
                            }
                            
                            val displayContext = LocalContext.current

                            Text(
                                text = "DEFAULT SYSTEM LAUNCHER",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable {
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                val roleManager = displayContext.getSystemService(Context.ROLE_SERVICE) as RoleManager
                                                if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                                                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                                                    displayContext.startActivity(intent)
                                                } else {
                                                    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                                                    displayContext.startActivity(intent)
                                                }
                                            } else {
                                                val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                                                displayContext.startActivity(intent)
                                            }
                                        } catch (e: Exception) {
                                            try {
                                                val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                                                displayContext.startActivity(intent)
                                            } catch (ex: Exception) {
                                                Toast.makeText(displayContext, "Could not open default launcher settings.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isDefaultLauncher) "Fossify is Default Launcher" else "Set as Default Launcher",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (isDefaultLauncher) "Fossify is managing your default home screen." else "Configure system preference to Fossify.",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                    }
                                    
                                    Icon(
                                        imageVector = if (isDefaultLauncher) Icons.Default.CheckCircle else Icons.Default.Home,
                                        contentDescription = "Default status",
                                        tint = if (isDefaultLauncher) Color(0xFFD0E4FF) else Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // User Greeting Configurations
                            Text(
                                text = "PERSONALIZE WELCOME GREETING",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            TextField(
                                value = greetText,
                                onValueChange = {
                                    greetText = it
                                    viewModel.saveLauncherConfigSetting("user_greet_name", it)
                                },
                                label = { Text("Set Username Nickname:") },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color.White.copy(alpha = 0.04f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("greet_edit_input"),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Desktop layout width options
                            Text(
                                text = "DESKTOP GRID COLS SIZE",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(3, 4, 5, 6).forEach { colOption ->
                                    val isSelected = currentColumns == colOption
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) Color(0xFFEB5E28) else Color.White.copy(alpha = 0.05f))
                                            .clickable {
                                                viewModel.saveLauncherConfigSetting("grid_columns", colOption.toString())
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$colOption cols",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Show labels switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Show Application Labels",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Toggle launcher app description labels visibility on home grid displays.",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp
                                    )
                                }

                                Switch(
                                    checked = currentShowLabels,
                                    onCheckedChange = {
                                        viewModel.saveLauncherConfigSetting("show_labels", it.toString())
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFFEB5E28),
                                        checkedTrackColor = Color(0xFFEB5E28).copy(alpha = 0.4f)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Theme Color Palette
                            Text(
                                text = "launcher Accent color options",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            val themes = listOf(
                                "0xFFEB5E28" to "Orange",
                                "0xFF4CAF50" to "Green",
                                "0xFF0077B6" to "Cobalt",
                                "0xFF9D4EDD" to "Magenta",
                                "0xFF2EC4B6" to "Teal",
                                "0xFFE91E63" to "Pink",
                                "0xFF6C757D" to "SlateGray"
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                themes.forEach { item ->
                                    val isSelected = currentAccentColor == item.first
                                    Box(
                                        modifier = Modifier
                                            .size(width = 90.dp, height = 40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(item.first.toLongOrNull() ?: 0L))
                                            .clickable {
                                                viewModel.saveLauncherConfigSetting("theme_accent_color", item.first)
                                            }
                                            .border(
                                                width = if (isSelected) 2.dp else 0.dp,
                                                color = if (isSelected) Color.White else Color.Transparent,
                                                shape = RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Text(
                                                item.second,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        "Wallpapers" -> {
                            // Wallpaper categories selector
                            Text(
                                text = "WALLPAPER DISPLAY STYLE",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Gradient", "Solid", "Custom Image").forEach { wallStyle ->
                                    val isSel = currentWallpaperType == wallStyle
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSel) Color(0xFFEB5E28) else Color.White.copy(alpha = 0.05f))
                                            .clickable {
                                                viewModel.saveLauncherConfigSetting("wallpaper_type", wallStyle)
                                                // Reset presets back to standard default
                                                val defPreset = when (wallStyle) {
                                                    "Gradient" -> "Cosmic Night"
                                                    "Solid" -> "Dark Cosmic"
                                                    else -> "https://images.unsplash.com/photo-1518531933037-91b2f5f229cc?w=1080"
                                                }
                                                viewModel.saveLauncherConfigSetting("wallpaper_preset", defPreset)
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = wallStyle,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            if (currentWallpaperType == "Custom Image") {
                                Text(
                                    text = "ENTER CUSTOM IMAGE URL",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                var imageInputText by remember { mutableStateOf(currentWallpaperPreset) }
                                TextField(
                                    value = imageInputText,
                                    onValueChange = {
                                        imageInputText = it
                                        viewModel.saveLauncherConfigSetting("wallpaper_preset", it)
                                    },
                                    placeholder = { Text("https://example.com/wallpaper.jpg", color = Color.Gray) },
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color.White.copy(alpha = 0.04f),
                                        unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                
                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = "PRESET AESTHETIC LINKS",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                val picPresets = listOf(
                                    "Warm Colorful Gradient" to "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=1080",
                                    "Dark Botanical Nature" to "https://images.unsplash.com/photo-1518531933037-91b2f5f229cc?w=1080",
                                    "Elegant Liquid Wave" to "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=1080",
                                    "Celestial Milky Way" to "https://images.unsplash.com/photo-1528459801416-a9e53bbf4e17?w=1080"
                                )

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    picPresets.forEach { item ->
                                        val isSelected = currentWallpaperPreset == item.second
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .clickable {
                                                    imageInputText = item.second
                                                    viewModel.saveLauncherConfigSetting("wallpaper_preset", item.second)
                                                }
                                                .border(
                                                    width = if (isSelected) 2.dp else 0.dp,
                                                    color = if (isSelected) Color(0xFFEB5E28) else Color.Transparent,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = item.first,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 13.sp
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = Color(0xFFEB5E28),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "SELECT ART PRESETS COLOR",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                val presets = if (currentWallpaperType == "Gradient") {
                                    listOf("Cosmic Night", "Sunset Dusk", "Forest Aurora", "Oceanic Breeze", "Cosmic Amethyst")
                                } else {
                                    listOf("Dark Cosmic", "Ivory White", "Pitch Black", "Indigo Midnight", "Pastel Sage")
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    presets.forEach { pres ->
                                        val isSelected = currentWallpaperPreset == pres
                                        val tempBrush = getBackgroundBrush(currentWallpaperType, pres)
                                        
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(tempBrush)
                                                .clickable {
                                                    viewModel.saveLauncherConfigSetting("wallpaper_preset", pres)
                                                }
                                                .border(
                                                    width = if (isSelected) 3.dp else 0.dp,
                                                    color = if (isSelected) Color(0xFFEB5E28) else Color.Transparent,
                                                    shape = RoundedCornerShape(14.dp)
                                                )
                                                .padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = pres,
                                                    color = if (pres == "Ivory White") Color.Black else Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = Color(0xFFEB5E28),
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        "Hidden Manager" -> {
                            Text(
                                text = "PRIVACY INSTALLED TOOLS MANAGER",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            if (hiddenInstalledApps.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    hiddenInstalledApps.forEach { app ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                                                ) {
                                                    if (app.systemIcon != null) {
                                                        AndroidView(
                                                            factory = { ctx -> ImageImageView(ctx, app.systemIcon) },
                                                            modifier = Modifier.size(36.dp)
                                                        )
                                                    } else if (app.mockIcon != null) {
                                                        Icon(
                                                            imageVector = app.mockIcon,
                                                            contentDescription = app.displayLabel,
                                                            tint = Color(app.iconColor),
                                                            modifier = Modifier.padding(6.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(app.displayLabel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            }

                                            Button(
                                                onClick = {
                                                    viewModel.toggleAppHidden(app.packageName, CustomAppSetting(packageName = app.packageName, isHidden = app.isHidden))
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                            ) {
                                                Text("Unhide", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Privacy items empty",
                                        tint = Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "No hidden application files.",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "To hide secondary tools, long press the app inside the drawer list and select 'Hide App From List'.",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEB5E28)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Decline / Save Settings", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Android native ImageView renderer helper
private fun ImageImageView(context: Context, drawable: android.graphics.drawable.Drawable): ImageView {
    return ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageDrawable(drawable)
    }
}

@Composable
fun QuickMainDockCallShortcut(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFD0E4FF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = "Phone call helper",
            tint = Color(0xFF1A1C1E),
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun QuickSubDockShortcut(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF3B4858))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFFD0E4FF),
            modifier = Modifier.size(28.dp)
        )
    }
}
