package com.example

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(dynamicColor = false, darkTheme = false) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFFF3EDF7)
                ) { innerPadding ->
                    DepthCustomizerScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun DepthCustomizerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Configuration State
    var config by remember { mutableStateOf(WallpaperConfig.load(context)) }

    // Preset Image Bitmaps cached for Preview
    var prBg by remember { mutableStateOf<Bitmap?>(null) }
    var prFg by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingCustom by remember { mutableStateOf(false) }
    var loadingStatusText by remember { mutableStateOf("Isolating foreground subject on-device...") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableIntStateOf(0) } // 0: Theme, 1: Clock, 2: Effects
    var showTutorial by remember { mutableStateOf(false) }
    var showExploreDialog by remember { mutableStateOf(false) }

    // Reload preview Bitmaps when configuration changed
    LaunchedEffect(config.bgType, config.customBgPath, config.customFgPath) {
        withContext(Dispatchers.IO) {
            prBg?.recycle()
            prFg?.recycle()
            prBg = null
            prFg = null

            if (config.bgType == "CUSTOM") {
                val bgPath = config.customBgPath
                val fgPath = config.customFgPath
                if (bgPath != null && File(bgPath).exists()) {
                    prBg = BitmapFactory.decodeFile(bgPath)
                }
                if (fgPath != null && File(fgPath).exists()) {
                    prFg = BitmapFactory.decodeFile(fgPath)
                }
            }

            // Fallback load/render presets if custom path is missing or customized config is preset-based
            if (prBg == null && config.bgType != "VIDEO") {
                val targetType = if (config.bgType == "CUSTOM") "PRESET_CYBERPUNK" else config.bgType
                prBg = ProceduralPresetGenerator.generatePreset(targetType, false)
                prFg = ProceduralPresetGenerator.generatePreset(targetType, true)
            }
        }
    }

    // Interactive Photo offset and scale updates
    var tempScale by remember { mutableFloatStateOf(1.0f) }
    var tempOffsetX by remember { mutableFloatStateOf(0f) }
    var tempOffsetY by remember { mutableFloatStateOf(0f) }

    // Synchronize loaded state changes
    LaunchedEffect(config.scaleFactor, config.offsetX, config.offsetY) {
        tempScale = config.scaleFactor
        tempOffsetX = config.offsetX
        tempOffsetY = config.offsetY
    }

    // Function to persistently write config changes
    val updateConfig = { newConfig: WallpaperConfig ->
        config = newConfig
        WallpaperConfig.save(context, newConfig)
    }

    // Action launcher for Custom Photo upload
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoadingCustom = true
            loadingStatusText = "AI Segmenter isolating main subject & creating layer stacks..."
            errorMsg = null
            coroutineScope.launch {
                try {
                    val paths = withContext(Dispatchers.IO) {
                        ImageSegmenterHelper.segmentImage(context, uri)
                    }
                    val newConfig = config.copy(
                        bgType = "CUSTOM",
                        customBgPath = paths.first,
                        customFgPath = paths.second,
                        scaleFactor = 1.0f,
                        offsetX = 0f,
                        offsetY = 0f
                    )
                    updateConfig(newConfig)
                } catch (e: Exception) {
                    errorMsg = e.message ?: "Analysis failed. Please select an image with a clear foreground subject."
                } finally {
                    isLoadingCustom = false
                }
            }
        }
    }

    // Action launcher for Custom Local Video upload
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoadingCustom = true
            loadingStatusText = "Optimizing live video loop..."
            errorMsg = null
            coroutineScope.launch {
                try {
                    val path = withContext(Dispatchers.IO) {
                        ImageSegmenterHelper.saveVideo(context, uri)
                    }
                    val newConfig = config.copy(
                        bgType = "VIDEO",
                        customVideoPath = path,
                        scaleFactor = 1.0f,
                        offsetX = 0f,
                        offsetY = 0f
                    )
                    updateConfig(newConfig)
                } catch (e: Exception) {
                    errorMsg = e.message ?: "Failed to save selected video."
                } finally {
                    isLoadingCustom = false
                }
            }
        }
    }

    // Apply Online Video handler
    val onApplyVideoUrl = { videoUrl: String ->
        showExploreDialog = false
        isLoadingCustom = true
        loadingStatusText = "Downloading Cinematic Loop..."
        errorMsg = null
        coroutineScope.launch {
            try {
                val videoFile = withContext(Dispatchers.IO) {
                    val destFile = File(context.filesDir, "custom_video.mp4")
                    if (destFile.exists()) destFile.delete()
                    val url = java.net.URL(videoUrl)
                    url.openStream().use { input ->
                        java.io.FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    destFile
                }
                val newConfig = config.copy(
                    bgType = "VIDEO",
                    customVideoPath = videoFile.absolutePath,
                    scaleFactor = 1.0f,
                    offsetX = 0f,
                    offsetY = 0f
                )
                updateConfig(newConfig)
            } catch (e: Exception) {
                errorMsg = "Background video loop download failed: ${e.localizedMessage}"
            } finally {
                isLoadingCustom = false
            }
        }
        Unit
    }

    // Apply Online Depth Image Segmenter handler
    val onApplyImageUrl = { imageUrl: String ->
        showExploreDialog = false
        isLoadingCustom = true
        loadingStatusText = "Downloading High-Res Canvas..."
        errorMsg = null
        coroutineScope.launch {
            try {
                val downloadedFile = withContext(Dispatchers.IO) {
                    val destFile = File(context.filesDir, "temp_explore.png")
                    if (destFile.exists()) destFile.delete()
                    val url = java.net.URL(imageUrl)
                    url.openStream().use { input ->
                        java.io.FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    destFile
                }
                
                loadingStatusText = "AI Isolating Subject..."
                val paths = withContext(Dispatchers.IO) {
                    ImageSegmenterHelper.segmentImage(context, Uri.fromFile(downloadedFile))
                }
                
                val newConfig = config.copy(
                    bgType = "CUSTOM",
                    customBgPath = paths.first,
                    customFgPath = paths.second,
                    scaleFactor = 1.0f,
                    offsetX = 0f,
                    offsetY = 0f
                )
                updateConfig(newConfig)
            } catch (e: Exception) {
                errorMsg = "AI isolation of online image failed: ${e.localizedMessage}"
            } finally {
                isLoadingCustom = false
            }
        }
        Unit
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF3EDF7))
    ) {
        // Top App Bar matching Natural Tones styling
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(
                    onClick = { /* back action visual feedback */ },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF1C1B1F)
                    )
                }
                Text(
                    text = "Depth Editor",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.5).sp,
                    color = Color(0xFF1C1B1F)
                )
            }
            
            Button(
                onClick = {
                    try {
                        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                            putExtra(
                                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                ComponentName(context, DepthEffectWallpaperService::class.java)
                            )
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Live wallpaper not supported or failed to start", Toast.LENGTH_LONG).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6750A4),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Apply",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        // AI Analyzer Progress Overlay
        if (isLoadingCustom) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE0F3EDF7))
                    .pointerInput(Unit) {} // lock user interactions
                    .testTag("ai_loading_dialog"),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF6750A4))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "AI Depth Analysis",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1C1B1F)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = loadingStatusText,
                            fontSize = 14.sp,
                            color = Color(0xFF49454F),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Left-right panning + pinch Zoom Live Preview (Takes ~55% height)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f)
                .padding(16.dp)
                .background(Color.Black, RoundedCornerShape(28.dp))
                .border(8.dp, Color(0xFF1C1B1F), RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (tempScale * zoom).coerceIn(1.0f, 4.0f)
                        val nextOffsetX = (tempOffsetX + pan.x).coerceIn(-600f, 600f)
                        val nextOffsetY = (tempOffsetY + pan.y).coerceIn(-900f, 900f)

                        tempScale = nextScale
                        tempOffsetX = nextOffsetX
                        tempOffsetY = nextOffsetY

                        // Instantly update saved coordinates
                        updateConfig(
                            config.copy(
                                scaleFactor = nextScale,
                                offsetX = nextOffsetX,
                                offsetY = nextOffsetY
                            )
                        )
                    }
                }
        ) {
            // Live Preview Canvas rendering
            val previewBg = prBg
            val previewFg = prFg

            // System clock display
            var previewTime by remember { mutableStateOf("") }
            var previewDate by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                while (true) {
                    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val dateFormatter = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                    previewTime = formatter.format(Date())
                    previewDate = dateFormatter.format(Date()).uppercase(Locale.getDefault())
                    kotlinx.coroutines.delay(1000)
                }
            }

            // Real-time custom simulation canvas
            val currentParticles = remember { mutableStateListOf<LiveParticle>() }
            val frameTime = rememberInfiniteTransition(label = "").animateFloat(
                initialValue = 0f,
                targetValue = 100000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(100000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = ""
            )

            // Update particle animation in-app preview
            LaunchedEffect(config.particleStyle) {
                currentParticles.clear()
                if (config.particleStyle != "NONE") {
                    for (i in 0..35) {
                        currentParticles.add(
                            LiveParticle(
                                x = Math.random().toFloat() * 1000f,
                                y = Math.random().toFloat() * 2000f,
                                size = 4f + Math.random().toFloat() * 12f,
                                speed = if (config.particleStyle == "RAIN") 18f else 3f,
                                wiggle = Math.random().toFloat() * 5f
                            )
                        )
                    }
                }
            }

            // Canvas drawing layers
            Box(modifier = Modifier.fillMaxSize()) {
                // Background Layer (Z-0)
                if (config.bgType == "VIDEO") {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                val videoPath = config.customVideoPath
                                if (videoPath != null && java.io.File(videoPath).exists()) {
                                    setVideoPath(videoPath)
                                } else {
                                    setVideoPath("https://assets.mixkit.co/videos/preview/mixkit-nebula-in-space-12349-large.mp4")
                                }
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    mp.setVolume(0f, 0f) // Keep muted in customizer editor
                                    start()
                                }
                            }
                        },
                        update = { videoView ->
                            try {
                                val videoPath = config.customVideoPath
                                if (videoPath != null && java.io.File(videoPath).exists()) {
                                    videoView.setVideoPath(videoPath)
                                } else {
                                    videoView.setVideoPath("https://assets.mixkit.co/videos/preview/mixkit-nebula-in-space-12349-large.mp4")
                                }
                                videoView.start()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (previewBg != null) {
                    Image(
                        bitmap = previewBg.asImageBitmap(),
                        contentDescription = "Background Asset",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = tempScale,
                                scaleY = tempScale,
                                translationX = tempOffsetX,
                                translationY = tempOffsetY
                            ),
                        contentScale = ContentScale.Crop
                    )
                }

                // Dim overlay (Z-1)
                if (config.dimOpacity > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = config.dimOpacity))
                    )
                }

                // Clock Layer (Z-2) Custom typography rendering
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = (config.clockOffsetY / 3f).dp), // Map relative coordinates to screen fraction
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, _, _ ->
                                    // Let users drag the clock vertically!
                                    val nextClockY = (config.clockOffsetY + pan.y).coerceIn(100f, 1100f)
                                    updateConfig(config.copy(clockOffsetY = nextClockY))
                                }
                            }
                    ) {
                        Text(
                            text = previewDate,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(config.clockColor).copy(alpha = 0.85f),
                            letterSpacing = 1.sp,
                            fontFamily = when (config.clockStyle) {
                                "SERIF" -> FontFamily.Serif
                                "DIGITAL" -> FontFamily.Monospace
                                else -> FontFamily.SansSerif
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        val parts = previewTime.split(":")
                        if (parts.size == 2 && config.clockStyle != "OUTLINE") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = parts[0],
                                    fontSize = (68 * config.clockScale).sp,
                                    fontWeight = if (config.clockStyle == "BOLD") FontWeight.Bold else FontWeight.Normal,
                                    color = Color(config.clockHourColor),
                                    fontFamily = when (config.clockStyle) {
                                        "SERIF" -> FontFamily.Serif
                                        "DIGITAL" -> FontFamily.Monospace
                                        else -> FontFamily.SansSerif
                                    }
                                )
                                Text(
                                    text = ":",
                                    fontSize = (68 * config.clockScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(config.clockColor),
                                    modifier = Modifier.graphicsLayer { translationY = -4f }
                                )
                                Text(
                                    text = parts[1],
                                    fontSize = (68 * config.clockScale).sp,
                                    fontWeight = if (config.clockStyle == "BOLD") FontWeight.Bold else FontWeight.Normal,
                                    color = Color(config.clockMinColor),
                                    fontFamily = when (config.clockStyle) {
                                        "SERIF" -> FontFamily.Serif
                                        "DIGITAL" -> FontFamily.Monospace
                                        else -> FontFamily.SansSerif
                                    }
                                )
                            }
                        } else {
                            Text(
                                text = previewTime,
                                fontSize = (68 * config.clockScale).sp,
                                fontWeight = if (config.clockStyle == "BOLD") FontWeight.Bold else FontWeight.Normal,
                                color = Color(config.clockColor),
                                fontFamily = when (config.clockStyle) {
                                        "SERIF" -> FontFamily.Serif
                                        "DIGITAL" -> FontFamily.Monospace
                                        else -> FontFamily.SansSerif
                                    }
                            )
                        }
                    }
                }

                // Foreground Cutout Layer (Z-3)
                if (previewFg != null) {
                    Image(
                        bitmap = previewFg.asImageBitmap(),
                        contentDescription = "Cutout Foreground Overlap",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = tempScale,
                                scaleY = tempScale,
                                translationX = tempOffsetX,
                                translationY = tempOffsetY,
                                alpha = config.depthAlpha
                            ),
                        contentScale = ContentScale.Crop
                    )
                }

                // Particles Simulation Layer (Z-4)
                if (config.particleStyle != "NONE") {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Keep animation loop ticking
                        val tick = frameTime.value
                        val pStyle = config.particleStyle
                        currentParticles.forEach { p ->
                            // Update coordinate
                            p.y += p.speed
                            if (pStyle == "SNOW" || pStyle == "SAKURA") {
                                p.x += sin(p.y * 0.02f) * p.wiggle * 0.2f
                            }
                            if (p.y > size.height) p.y = -20f
                            if (p.x < 0) p.x = size.width
                            if (p.x > size.width) p.x = 0f

                            // Draw particle shapes
                            if (pStyle == "RAIN") {
                                drawLine(
                                    color = Color(0xAA99DFFF),
                                    start = Offset(p.x, p.y),
                                    end = Offset(p.x + 2f, p.y + p.speed * 1.5f),
                                    strokeWidth = 2.dp.toPx()
                                )
                            } else if (pStyle == "SAKURA") {
                                drawOval(
                                    color = Color(0xFFFFB3C6),
                                    topLeft = Offset(p.x, p.y),
                                    size = androidx.compose.ui.geometry.Size(p.size, p.size * 0.6f)
                                )
                            } else {
                                // SNOW
                                drawCircle(
                                    color = Color(0xDFEFFFFF),
                                    radius = p.size / 2f,
                                    center = Offset(p.x, p.y)
                                )
                            }
                        }
                    }
                }

                // Pinch-to-zoom tip panel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x991C1B1F))))
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Pinch to Zoom & Drag Photo  •  Slide Clock to reposition",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Custom Error Message Toast Overlay
        errorMsg?.let { msg ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF551E1E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = "Error", tint = Color.Red)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = msg, color = Color.White, fontSize = 13.sp)
                }
            }
        }

        // Segmented Control Tabs (Z-Index / Parallax configuration panels)
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color(0xFFF7F2F9),
            contentColor = Color(0xFF49454F),
            indicator = { tabPositions ->
                if (activeTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = Color(0xFF6750A4)
                    )
                }
            }
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Wallpapers", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                icon = { Icon(Icons.Filled.PhotoAlbum, contentDescription = "Themes", modifier = Modifier.size(20.dp)) },
                selectedContentColor = Color(0xFF6750A4),
                unselectedContentColor = Color(0xFF49454F)
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Clock", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                icon = { Icon(Icons.Filled.Timer, contentDescription = "Clock settings", modifier = Modifier.size(20.dp)) },
                selectedContentColor = Color(0xFF6750A4),
                unselectedContentColor = Color(0xFF49454F)
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("Effects", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "Particle effects", modifier = Modifier.size(20.dp)) },
                selectedContentColor = Color(0xFF6750A4),
                unselectedContentColor = Color(0xFF49454F)
            )
        }

        // Bottom Settings Editor Panel Content (Takes remainder of screen height)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFF7F2F9))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (activeTab) {
                    0 -> {
                        // Wallpaper & Presets tab
                        Text(
                            text = "PRESET DEPTH THEMES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF49454F),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Horizontal list of exquisite custom-made presets
                        val presets = listOf(
                            PresetOption("PRESET_CYBERPUNK", "Cyberpunk", "City Skyline", Color(0xFF381352), Color(0xFFFF007F)),
                            PresetOption("PRESET_MOUNTAIN", "Mountain", "Twilight Peaks", Color(0xFF2B1B54), Color(0xFFC35175)),
                            PresetOption("PRESET_ARCH", "Minimal Arch", "Pastel Curved Wall", Color(0xFFE9DCD1), Color(0xFFC35C4A))
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().height(100.dp)
                        ) {
                            items(presets) { p ->
                                val selected = config.bgType == p.id
                                Box(
                                    modifier = Modifier
                                        .width(135.dp)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(p.colorBg)
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) Color(0xFF6750A4) else Color(0xFFCAC4D0),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable {
                                            updateConfig(config.copy(bgType = p.id))
                                        }
                                        .testTag("preset_card_${p.id.lowercase()}")
                                ) {
                                    // Soft decorative circles mapping sunset
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawCircle(
                                            color = p.colorSun,
                                            radius = size.width * 0.3f,
                                            center = Offset(size.width * 0.5f, size.height * 0.6f)
                                        )
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.Bottom
                                    ) {
                                        Text(
                                            text = p.name,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = p.desc,
                                            color = Color.LightGray,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { showExploreDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("explore_online_wallpapers_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEADDFF),
                                contentColor = Color(0xFF21005D)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFCAC4D0))
                        ) {
                            Icon(Icons.Filled.Explore, contentDescription = null, tint = Color(0xFF21005D))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Explore Live Videos & Wallpapers",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "CHOOSE YOUR OWN PHOTO OR VIDEO",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF49454F),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Custom image upload primary button
                            Button(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .testTag("upload_custom_image_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF6750A4)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFFCAC4D0))
                            ) {
                                Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = Color(0xFF6750A4))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Isolate Photo",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Custom video upload primary button
                            Button(
                                onClick = { videoPickerLauncher.launch("video/*") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .testTag("upload_custom_video_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF6750A4)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFFCAC4D0))
                            ) {
                                Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = Color(0xFF6750A4))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Select Video",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    1 -> {
                        // Clock customization tab
                        Text(
                            text = "CLOCK FONTS",
                            fontSize = 12.sp,
                            color = Color(0xFF49454F),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val styles = listOf(
                            Pair("BOLD", "System Bold"),
                            Pair("STANDARD", "Classic Standard"),
                            Pair("SERIF", "Elegant Serif"),
                            Pair("DIGITAL", "Monospace Console"),
                            Pair("OUTLINE", "Futuristic Outline")
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(styles) { s ->
                                val selected = config.clockStyle == s.first
                                FilterChip(
                                    selected = selected,
                                    onClick = { updateConfig(config.copy(clockStyle = s.first)) },
                                    label = { Text(s.second, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFEADDFF),
                                        selectedLabelColor = Color(0xFF21005D),
                                        containerColor = Color.White,
                                        labelColor = Color(0xFF49454F)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        selectedBorderColor = Color(0xFF6750A4),
                                        borderColor = Color(0xFFCAC4D0)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("clock_style_${s.first.lowercase()}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "PALETTE SELECTION & DUAL HOUR-MINUTE COLORS",
                            fontSize = 12.sp,
                            color = Color(0xFF49454F),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Curated vibrant color combinations
                        val palettes = listOf(
                            ColorPalette("Mono White", Color.White, Color.White),
                            ColorPalette("Sunset Coral", Color.White, Color(0xFFFF6B6B)),
                            ColorPalette("Cyber Vapor", Color(0xFF00FFFF), Color(0xFFFF007F)),
                            ColorPalette("Sunny Gold", Color(0xFFFFF9A6), Color(0xFFFF9F1C)),
                            ColorPalette("Winter Magic", Color.White, Color(0xFF9DFFFF)),
                            ColorPalette("Neon Lime", Color.White, Color(0xFF39FF14))
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(palettes) { p ->
                                val selected = config.clockHourColor == p.hour.toArgb() && config.clockMinColor == p.minute.toArgb()
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White)
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) Color(0xFF6750A4) else Color(0xFFCAC4D0),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            updateConfig(
                                                config.copy(
                                                    clockColor = p.hour.toArgb(),
                                                    clockHourColor = p.hour.toArgb(),
                                                    clockMinColor = p.minute.toArgb()
                                                )
                                            )
                                        }
                                        .padding(vertical = 10.dp, horizontal = 14.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(16.dp).background(p.hour, CircleShape))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(modifier = Modifier.size(16.dp).background(p.minute, CircleShape))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = p.name, color = Color(0xFF1C1B1F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Clock Scale Adjuster Slider
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "CLOCK SIZE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF49454F))
                            Text(text = "${(config.clockScale * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
                        }
                        Slider(
                            value = config.clockScale,
                            onValueChange = { updateConfig(config.copy(clockScale = it)) },
                            valueRange = 0.6f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF6750A4),
                                activeTrackColor = Color(0xFF6750A4),
                                inactiveTrackColor = Color(0xFFE7E0EC)
                            )
                        )
                    }

                    2 -> {
                        // Effects, Particle & Parallax separation properties tab
                        Text(
                            text = "LIVE PARTICLE MOTION",
                            fontSize = 12.sp,
                            color = Color(0xFF49454F),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val particleStyles = listOf(
                            Pair("NONE", "No Particles"),
                            Pair("SNOW", "Gentle Snow"),
                            Pair("RAIN", "Rain Slashes"),
                            Pair("SAKURA", "Sakura Blossoms")
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(particleStyles) { ps ->
                                val selected = config.particleStyle == ps.first
                                FilterChip(
                                    selected = selected,
                                    onClick = { updateConfig(config.copy(particleStyle = ps.first)) },
                                    label = { Text(ps.second, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFEADDFF),
                                        selectedLabelColor = Color(0xFF21005D),
                                        containerColor = Color.White,
                                        labelColor = Color(0xFF49454F)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        selectedBorderColor = Color(0xFF6750A4),
                                        borderColor = Color(0xFFCAC4D0)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        // Parallax sensitivity slider
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "GYROSCOPE PARALLAX INTENSITY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF49454F))
                            Text(text = "${(config.parallaxSensitivity * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
                        }
                        Slider(
                            value = config.parallaxSensitivity,
                            onValueChange = { updateConfig(config.copy(parallaxSensitivity = it)) },
                            valueRange = 0.0f..2.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF6750A4),
                                activeTrackColor = Color(0xFF6750A4),
                                inactiveTrackColor = Color(0xFFE7E0EC)
                            )
                        )

                        // Depth mask transparency slider
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "DEPTH ALIGNMENT TRANSPARENCY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF49454F))
                            Text(text = "${(config.depthAlpha * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
                        }
                        Slider(
                            value = config.depthAlpha,
                            onValueChange = { updateConfig(config.copy(depthAlpha = it)) },
                            valueRange = 0.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF6750A4),
                                activeTrackColor = Color(0xFF6750A4),
                                inactiveTrackColor = Color(0xFFE7E0EC)
                            )
                        )

                        // Backdrop dimming opacity slider
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "BACKGROUND DIM LAYER", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF49454F))
                            Text(text = "${(config.dimOpacity * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
                        }
                        Slider(
                            value = config.dimOpacity,
                            onValueChange = { updateConfig(config.copy(dimOpacity = it)) },
                            valueRange = 0.0f..0.8f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF6750A4),
                                activeTrackColor = Color(0xFF6750A4),
                                inactiveTrackColor = Color(0xFFE7E0EC)
                            )
                        )
                    }
                }
            }
        }

        // Action Toolbar (Set Live Wallpaper & tutorial triggers)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF7F2F9))
                .padding(16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Setup tutorial informational trigger
            OutlinedButton(
                onClick = { showTutorial = true },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("tutorial_button"),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6750A4))
            ) {
                Icon(Icons.Filled.HelpOutline, contentDescription = null, tint = Color(0xFF6750A4))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Lockscreen Tip", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            // High-impact active primary trigger
            Button(
                onClick = {
                    try {
                        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                            putExtra(
                                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                ComponentName(context, DepthEffectWallpaperService::class.java)
                            )
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Live wallpaper not supported or failed to start", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier
                    .weight(1.3f)
                    .height(52.dp)
                    .testTag("apply_wallpaper_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6750A4),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Wallpaper, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Set Wallpaper", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }

    // Samsung LockStar / Pixel System Clock Overlap Resolution Tutorial Dialog
    if (showTutorial) {
        Dialog(onDismissRequest = { showTutorial = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("tutorial_dialog"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.MenuBook,
                        contentDescription = null,
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Avoid Clock Overlap",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Because manufacturers lock their system clocks: the default lockscreen clock will draw right on top of our live wallpaper. To fix this, follow these options:",
                        fontSize = 13.sp,
                        color = Color(0xFF49454F),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider(color = Color(0xFFE7E0EC))
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TutorialStep("1", "Samsung devices", "Go into Settings > Lock Screen > LockStar (or edit Lockscreen) and change the style/clock to transparent or delete it entirely.")
                        TutorialStep("2", "Google Pixel & others", "Navigate to Settings > Wallpaper & style > Double-line clock and disable it, or move lockscreen widget alignment to minimalist settings.")
                        TutorialStep("3", "Alternative", "Some devices let you shrink the default clock size down to a tiny corner so it does not conflict with the beautiful central depth cutout!")
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showTutorial = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("close_tutorial_button")
                    ) {
                        Text("Got it!", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Trigger Explore dialog overlay
    if (showExploreDialog) {
        ExploreGalleryDialog(
            onDismiss = { showExploreDialog = false },
            onApplyVideo = onApplyVideoUrl,
            onApplyImage = onApplyImageUrl
        )
    }
}

private data class ExploreItem(
    val title: String,
    val desc: String,
    val url: String,
    val isVideo: Boolean,
    val gradientColors: List<Color>
)

private val exploreVideos = listOf(
    ExploreItem(
        title = "Futuristic Outrun Grid",
        desc = "Vibrant synthwave sunset with neon wireframe grid lines.",
        url = "https://assets.mixkit.co/videos/preview/mixkit-retro-futurism-grid-and-sunset-background-43093-large.mp4",
        isVideo = true,
        gradientColors = listOf(Color(0xFFFF007F), Color(0xFF381352))
    ),
    ExploreItem(
        title = "Astral Nebula Space",
        desc = "Spectacular loop of evolving stellar gases and cosmic dust.",
        url = "https://assets.mixkit.co/videos/preview/mixkit-nebula-in-space-12349-large.mp4",
        isVideo = true,
        gradientColors = listOf(Color(0xFF2B1B54), Color(0xFF5E17EB))
    ),
    ExploreItem(
        title = "Turquoise Oceans",
        desc = "Aerial tropical drone clip of soothing waves crashing on beach.",
        url = "https://assets.mixkit.co/videos/preview/mixkit-sea-waves-with-foam-from-above-39294-large.mp4",
        isVideo = true,
        gradientColors = listOf(Color(0xFF00F5D4), Color(0xFF00BBF9))
    ),
    ExploreItem(
        title = "Holographic Waves",
        desc = "Glow lasers shimmering organically like dynamic wave lines.",
        url = "https://assets.mixkit.co/videos/preview/mixkit-abstract-laser-lights-background-41712-large.mp4",
        isVideo = true,
        gradientColors = listOf(Color(0xFF70E000), Color(0xFF38B000))
    )
)

private val exploreImages = listOf(
    ExploreItem(
        title = "Abyssal Space Walk",
        desc = "High-contrast astronaut rendering in pitch black cosmos with deep blue accents.",
        url = "https://images.unsplash.com/photo-1541185933-ef5d8ed016c2?auto=format&fit=crop&q=80&w=800",
        isVideo = false,
        gradientColors = listOf(Color(0xFF0D0C1D), Color(0xFF1D3557))
    ),
    ExploreItem(
        title = "Bioluminescent Jellyfish",
        desc = "Radiant electric orange dome floating in absolute midnight oceanic void.",
        url = "https://images.unsplash.com/photo-1544735716-392fe2489ffa?auto=format&fit=crop&q=80&w=800",
        isVideo = false,
        gradientColors = listOf(Color(0xFF03071E), Color(0xFFDC2F02))
    ),
    ExploreItem(
        title = "Crimson Neon Bloom",
        desc = "Brilliant glowing red rose flower framed by flawless absolute dark velvet.",
        url = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&q=80&w=800",
        isVideo = false,
        gradientColors = listOf(Color(0xFF1A0503), Color(0xFF6A040F))
    ),
    ExploreItem(
        title = "Cyberpunk Oni Mask",
        desc = "Holographic purple and pink cybernetic face with stark isolation contours.",
        url = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&q=80&w=800",
        isVideo = false,
        gradientColors = listOf(Color(0xFF10002B), Color(0xFF7B2CBF))
    ),
    ExploreItem(
        title = "Futuristic Chrome Orb",
        desc = "Glossy neon fluid chrome orb hovering on flat black background.",
        url = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&q=80&w=800",
        isVideo = false,
        gradientColors = listOf(Color(0xFF081C15), Color(0xFF52B788))
    ),
    ExploreItem(
        title = "Minimalist Monstera Leaf",
        desc = "Vivid bright green Monstera leaf standing out of deep midnight shadows.",
        url = "https://images.unsplash.com/photo-1533038590840-1cde6b668f91?auto=format&fit=crop&q=80&w=800",
        isVideo = false,
        gradientColors = listOf(Color(0xFF0B1F13), Color(0xFF2D6A4F))
    ),
    ExploreItem(
        title = "Midnight Supercar",
        desc = "Carbon-black hypercar silhouette with piercing yellow dynamic headlamps.",
        url = "https://images.unsplash.com/photo-1618843479313-40f8afb4b4d8?auto=format&fit=crop&q=80&w=800",
        isVideo = false,
        gradientColors = listOf(Color(0xFF1C1F22), Color(0xFFD6B124))
    ),
    ExploreItem(
        title = "Cosmic Eclipse ring",
        desc = "Minimalist glowing neon ultraviolet ring floating in pitch infinity.",
        url = "https://images.unsplash.com/photo-1532690650605-1ee79705a87e?auto=format&fit=crop&q=80&w=800",
        isVideo = false,
        gradientColors = listOf(Color(0xFF0A0908), Color(0xFF49117C))
    )
)

@Composable
private fun ExploreGalleryDialog(
    onDismiss: () -> Unit,
    onApplyVideo: (String) -> Unit,
    onApplyImage: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Videos, 1: Wallpapers
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Text(
                    text = "Wallpapers & Video Loops",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F)
                )
                Text(
                    text = "Discover high-depth presets & cinematic loops",
                    fontSize = 12.sp,
                    color = Color(0xFF49454F)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF49454F),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFF6750A4)
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Live Videos", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        selectedContentColor = Color(0xFF6750A4),
                        unselectedContentColor = Color(0xFF49454F)
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Unsplash AMOLED", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        selectedContentColor = Color(0xFF6750A4),
                        unselectedContentColor = Color(0xFF49454F)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Grid of items
                val currentItems = if (selectedTab == 0) exploreVideos else exploreImages
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    currentItems.forEach { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFE7E0EC)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2F9))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail Gradient Card with play or image indicator
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Brush.linearGradient(item.gradientColors)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (item.isVideo) Icons.Filled.PlayArrow else Icons.Filled.Photo,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1C1B1F)
                                    )
                                    Text(
                                        text = item.desc,
                                        fontSize = 11.sp,
                                        color = Color(0xFF49454F),
                                        lineHeight = 14.sp
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Button(
                                    onClick = { 
                                        if (item.isVideo) onApplyVideo(item.url) else onApplyImage(item.url) 
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF6750A4)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Apply", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Close button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close Explorer", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ColumnScope.TutorialStep(num: String, device: String, instruction: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color(0xFFEADDFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = num, color = Color(0xFF21005D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = device, color = Color(0xFF1C1B1F), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(text = instruction, color = Color(0xFF49454F), fontSize = 11.sp)
        }
    }
}

private data class PresetOption(
    val id: String,
    val name: String,
    val desc: String,
    val colorBg: Color,
    val colorSun: Color
)

private data class ColorPalette(
    val name: String,
    val hour: Color,
    val minute: Color
)

private class LiveParticle(
    var x: Float,
    var y: Float,
    val size: Float,
    val speed: Float,
    val wiggle: Float
)
