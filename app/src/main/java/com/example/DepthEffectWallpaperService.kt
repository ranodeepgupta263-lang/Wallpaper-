package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.text.format.DateFormat
import android.view.SurfaceHolder
import java.io.File
import java.util.*
import kotlin.math.sin

class DepthEffectWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return DepthEngine()
    }

    inner class DepthEngine : Engine(), SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {

        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var width = 0
        private var height = 0

        // Configuration
        private lateinit var config: WallpaperConfig
        private var bgBitmap: Bitmap? = null
        private var fgBitmap: Bitmap? = null
        private var mediaPlayer: MediaPlayer? = null

        // Sensors & Parallax offsets
        private var sensorManager: SensorManager? = null
        private var accelSensor: Sensor? = null
        private var roll = 0f
        private var pitch = 0f
        private var smoothedRoll = 0f
        private var smoothedPitch = 0f

        // Particles
        private val particles = mutableListOf<Particle>()
        private val random = Random()

        // Time Broadcast Receiver to update clocks without checking 60 times/sec
        private var timeString = ""
        private var dateString = ""
        private val timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateTimeStrings()
                if (visible) drawFrame()
            }
        }

        // Main 60FPS tick animation runnable
        private val animationRunnable = object : Runnable {
            override fun run() {
                if (visible) {
                    updateParticles()
                    smoothSensors()
                    drawFrame()
                    handler.postDelayed(this, 16) // roughly 60 FPS
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            config = WallpaperConfig.load(this@DepthEffectWallpaperService)

            // Setup Time receiver
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            registerReceiver(timeReceiver, filter)
            updateTimeStrings()

            // Setup Sensors
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            // Listen to configuration updates
            val sharedPrefs = getSharedPreferences("depth_wallpaper_prefs", Context.MODE_PRIVATE)
            sharedPrefs.registerOnSharedPreferenceChangeListener(this)

            if (config.bgType != "VIDEO") {
                loadWallpaperBitmaps()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(animationRunnable)
            try {
                unregisterReceiver(timeReceiver)
            } catch (e: Exception) {
                // Ignore is already unregistered
            }
            val sharedPrefs = getSharedPreferences("depth_wallpaper_prefs", Context.MODE_PRIVATE)
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(this)
            unregisterSensors()
            recycleBitmaps()
            releaseVideoWallpaper()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                config = WallpaperConfig.load(this@DepthEffectWallpaperService)
                if (config.bgType == "VIDEO") {
                    stopAnimationAndCanvas()
                    handleVideoWallpaper()
                } else {
                    releaseVideoWallpaper()
                    loadWallpaperBitmaps()
                    registerSensors()
                    updateTimeStrings()
                    initializeParticles()
                    handler.post(animationRunnable)
                }
            } else {
                handler.removeCallbacks(animationRunnable)
                unregisterSensors()
                pauseVideoWallpaper()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            if (config.bgType == "VIDEO") {
                stopAnimationAndCanvas()
                handleVideoWallpaper()
            } else {
                releaseVideoWallpaper()
                loadWallpaperBitmaps()
                initializeParticles()
                drawFrame()
            }
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            // Reload configuration changed in the UI editor dynamically
            handler.post {
                config = WallpaperConfig.load(this@DepthEffectWallpaperService)
                if (config.bgType == "VIDEO") {
                    stopAnimationAndCanvas()
                    handleVideoWallpaper()
                } else {
                    releaseVideoWallpaper()
                    loadWallpaperBitmaps()
                    initializeParticles()
                    if (visible) drawFrame()
                }
            }
        }

        private fun stopAnimationAndCanvas() {
            handler.removeCallbacks(animationRunnable)
            unregisterSensors()
        }

        private fun pauseVideoWallpaper() {
            try {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun releaseVideoWallpaper() {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mediaPlayer = null
        }

        private fun handleVideoWallpaper() {
            recycleBitmaps()
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    try {
                        val videoPath = config.customVideoPath
                        if (videoPath != null && File(videoPath).exists()) {
                            setDataSource(videoPath)
                        } else {
                            // High quality fallbacks if empty (like default retrowave planet video)
                            setDataSource("https://assets.mixkit.co/videos/preview/mixkit-nebula-in-space-12349-large.mp4")
                        }
                        setSurface(surfaceHolder.surface)
                        isLooping = true
                        setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        prepareAsync()
                        setOnPreparedListener { mp ->
                            mp.start()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                try {
                    mediaPlayer?.setSurface(surfaceHolder.surface)
                    if (mediaPlayer?.isPlaying == false) {
                        mediaPlayer?.start()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun updateTimeStrings() {
            val calendar = Calendar.getInstance()
            val hourFormat = if (DateFormat.is24HourFormat(this@DepthEffectWallpaperService)) "HH" else "hh"
            val hourStr = DateFormat.format(hourFormat, calendar).toString()
            val minStr = DateFormat.format("mm", calendar).toString()
            timeString = "$hourStr:$minStr"
            dateString = DateFormat.format("EEEE, MMMM d", calendar).toString()
        }

        private fun registerSensors() {
            accelSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        private fun unregisterSensors() {
            sensorManager?.unregisterListener(this)
        }

        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            // Gravity components
            val gravityX = event.values[0]
            val gravityY = event.values[1]

            // Calculate tilt roll & pitch
            roll = -gravityX * 4f // map to offset scale
            pitch = gravityY * 4f
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        private fun smoothSensors() {
            smoothedRoll = smoothedRoll * 0.9f + roll * 0.1f
            smoothedPitch = smoothedPitch * 0.9f + pitch * 0.1f
        }

        private fun loadWallpaperBitmaps() {
            recycleBitmaps()

            val w = if (width > 0) width else 1080
            val h = if (height > 0) height else 1920

            if (config.bgType == "CUSTOM") {
                // Load Custom Background
                val bgPath = config.customBgPath
                val fgPath = config.customFgPath

                if (bgPath != null && File(bgPath).exists()) {
                    val originalBg = BitmapFactory.decodeFile(bgPath)
                    if (originalBg != null) {
                        bgBitmap = scaleCenterCrop(originalBg, w, h)
                        originalBg.recycle()
                    }
                }
                if (fgPath != null && File(fgPath).exists()) {
                    val originalFg = BitmapFactory.decodeFile(fgPath)
                    if (originalFg != null) {
                        fgBitmap = scaleCenterCrop(originalFg, w, h)
                        originalFg.recycle()
                    }
                }
            }

            // Fallback to procedural generator if no custom loaded or presets selected
            if (bgBitmap == null) {
                val presetType = if (config.bgType == "CUSTOM") "PRESET_CYBERPUNK" else config.bgType
                bgBitmap = ProceduralPresetGenerator.generatePreset(presetType, false, w, h)
                fgBitmap = ProceduralPresetGenerator.generatePreset(presetType, true, w, h)
            }
        }

        private fun recycleBitmaps() {
            bgBitmap?.run { if (!isRecycled) recycle() }
            fgBitmap?.run { if (!isRecycled) recycle() }
            bgBitmap = null
            fgBitmap = null
        }

        private fun scaleCenterCrop(src: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
            val srcWidth = src.width
            val srcHeight = src.height

            val scaleX = targetWidth.toFloat() / srcWidth
            val scaleY = targetHeight.toFloat() / srcHeight
            val scale = maxOf(scaleX, scaleY)

            val newWidth = (srcWidth * scale).toInt()
            val newHeight = (srcHeight * scale).toInt()

            val scaled = Bitmap.createScaledBitmap(src, newWidth, newHeight, true)

            val xOffset = (newWidth - targetWidth) / 2
            val yOffset = (newHeight - targetHeight) / 2

            val cropped = Bitmap.createBitmap(scaled, xOffset, yOffset, targetWidth, targetHeight)
            scaled.recycle()
            return cropped
        }

        private fun initializeParticles() {
            particles.clear()
            val numParticles = when (config.particleStyle) {
                "RAIN" -> 80
                "SNOW" -> 50
                "SAKURA" -> 45
                else -> 0
            }
            for (i in 0 until numParticles) {
                particles.add(createRandomParticle(true))
            }
        }

        private fun createRandomParticle(initRandomY: Boolean): Particle {
            val pStyle = config.particleStyle
            val startY = if (initRandomY) random.nextFloat() * height else -20f
            val pColor = when (pStyle) {
                "RAIN" -> Color.parseColor("#999DFFFF") // Light cyan blue
                "SAKURA" -> Color.parseColor("#FFFFB3C6") // Cherry pink
                else -> Color.parseColor("#E0FFFFFF") // Pure snowy ice
            }
            return Particle(
                x = random.nextFloat() * width,
                y = startY,
                size = 4f + random.nextFloat() * 10f,
                speed = when (pStyle) {
                    "RAIN" -> 22f + random.nextFloat() * 15f
                    "SAKURA" -> 3f + random.nextFloat() * 5f
                    else -> 2f + random.nextFloat() * 4f // Snow
                },
                angle = when (pStyle) {
                    "RAIN" -> 1.4f // steep descent
                    "SAKURA" -> 0.8f + random.nextFloat() * 0.4f // diagonal swing
                    else -> 1.0f + random.nextFloat() * 0.3f // gentle down
                },
                color = pColor,
                opacity = 150 + random.nextInt(105),
                wiggle = random.nextFloat() * 5f,
                wiggleSpeed = 0.05f + random.nextFloat() * 0.05f
            )
        }

        private fun updateParticles() {
            if (config.particleStyle == "NONE") {
                particles.clear()
                return
            }
            if (particles.isEmpty()) {
                initializeParticles()
            }

            val pStyle = config.particleStyle
            val iterator = particles.listIterator()
            while (iterator.hasNext()) {
                val p = iterator.next()
                p.y += p.speed
                p.x += Math.cos(p.angle.toDouble()).toFloat() * p.speed * 0.3f

                // Wiggle motion for snow and sakura
                if (pStyle == "SNOW" || pStyle == "SAKURA") {
                    p.x += sin(p.y * p.wiggleSpeed) * p.wiggle * 0.15f
                }

                // If exited boundary, restart from top
                if (p.y > height + 30f || p.x < -30f || p.x > width + 30f) {
                    iterator.set(createRandomParticle(false))
                }
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    // Parallax calculations:
                    // Z-0 (Base Background) moves opposite to gyroscopic tilt
                    val bgOffsetX = -smoothedRoll * 1.5f * config.parallaxSensitivity
                    val bgOffsetY = smoothedPitch * 1.5f * config.parallaxSensitivity

                    // Z-3 (Foreground Cutout) moves in same direction as tilt
                    val fgOffsetX = smoothedRoll * 2.2f * config.parallaxSensitivity
                    val fgOffsetY = -smoothedPitch * 2.2f * config.parallaxSensitivity

                    // Z-2 (Clock Text) stays relatively stable, or moves subtly slower
                    val clockOffsetX = smoothedRoll * 0.4f * config.parallaxSensitivity
                    val clockOffsetY = -smoothedPitch * 0.4f * config.parallaxSensitivity

                    // 1. Draw Base Wallpaper (Z-0)
                    bgBitmap?.let {
                        val src = Rect(0, 0, it.width, it.height)
                        // Add canvas padding transformation matrix based on scaleFactor
                        canvas.save()
                        canvas.translate(bgOffsetX + config.offsetX, bgOffsetY + config.offsetY)
                        canvas.scale(config.scaleFactor, config.scaleFactor, width / 2f, height / 2f)
                        canvas.drawBitmap(it, src, Rect(0, 0, width, height), null)
                        canvas.restore()
                    }

                    // 2. Draw Dimming Overlay if selected (Z-1)
                    if (config.dimOpacity > 0f) {
                        val overlayPaint = Paint().apply {
                            color = Color.BLACK
                            alpha = (config.dimOpacity * 255).toInt()
                        }
                        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
                    }

                    // 3. Draw Dynamic Lockscreen Clock (Z-2)
                    canvas.save()
                    canvas.translate(clockOffsetX, clockOffsetY)
                    drawClock(canvas)
                    canvas.restore()

                    // 4. Draw Cute Foreground Cutout (Z-3)
                    fgBitmap?.let {
                        val src = Rect(0, 0, it.width, it.height)
                        canvas.save()
                        canvas.translate(fgOffsetX + config.offsetX, fgOffsetY + config.offsetY)
                        canvas.scale(config.scaleFactor, config.scaleFactor, width / 2f, height / 2f)

                        val fgPaint = Paint().apply {
                            alpha = (config.depthAlpha * 255).toInt()
                            isAntiAlias = true
                            isFilterBitmap = true
                        }
                        canvas.drawBitmap(it, src, Rect(0, 0, width, height), fgPaint)
                        canvas.restore()
                    }

                    // 5. Draw Particles on top of everything (Z-4)
                    drawParticles(canvas)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        private fun drawClock(canvas: Canvas) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = 210f * config.clockScale
            }

            // Apply different typography configurations
            when (config.clockStyle) {
                "STANDARD" -> {
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    paint.style = Paint.Style.FILL
                    paint.color = config.clockColor
                }
                "BOLD" -> {
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    paint.style = Paint.Style.FILL
                    paint.color = config.clockColor
                }
                "SERIF" -> {
                    paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    paint.style = Paint.Style.FILL
                    paint.color = config.clockColor
                }
                "DIGITAL" -> {
                    paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    paint.style = Paint.Style.FILL
                    paint.color = config.clockColor
                }
                "OUTLINE" -> {
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 14f
                    paint.color = config.clockColor
                }
            }

            val clockX = width / 2f
            val baseClockY = config.clockOffsetY

            // Parse Time into components (Separated Color for HR/MIN support!)
            val parts = timeString.split(":")
            if (parts.size == 2 && config.clockStyle != "OUTLINE") {
                val hrPart = parts[0]
                val minPart = parts[1]

                val paintHr = Paint(paint).apply { color = config.clockHourColor }
                val paintColon = Paint(paint).apply { color = config.clockColor }
                val paintMin = Paint(paint).apply { color = config.clockMinColor }

                // Measure sizes of strings to align side-to-side
                val wHr = paintHr.measureText(hrPart)
                val wColon = paintColon.measureText(":")

                // Draw centered Hour, Colon, Minute
                val totalWidth = wHr + wColon + paintMin.measureText(minPart)
                val startX = (width - totalWidth) / 2f

                canvas.drawText(hrPart, startX + (wHr / 2f), baseClockY, paintHr)
                canvas.drawText(":", startX + wHr + (wColon / 2f), baseClockY - 10f, paintColon)
                canvas.drawText(minPart, startX + wHr + wColon + (paintMin.measureText(minPart) / 2f), baseClockY, paintMin)
            } else {
                canvas.drawText(timeString, clockX, baseClockY, paint)
            }

            // Draw Subtitle (Date) just above the clock
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = 48f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                color = config.clockColor
                alpha = 200
            }
            canvas.drawText(dateString.uppercase(Locale.getDefault()), clockX, baseClockY - 140f, datePaint)
        }

        private fun drawParticles(canvas: Canvas) {
            val pStyle = config.particleStyle
            if (pStyle == "NONE") return

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            for (p in particles) {
                paint.color = p.color
                paint.alpha = p.opacity

                when (pStyle) {
                    "RAIN" -> {
                        paint.strokeWidth = p.size / 3f
                        canvas.drawLine(p.x, p.y, p.x + (p.speed * 0.12f), p.y + p.speed, paint)
                    }
                    "SAKURA" -> {
                        // Draw organic cherry blossom petal shapes
                        canvas.save()
                        canvas.translate(p.x, p.y)
                        canvas.rotate((p.y * p.wiggleSpeed * 15f) % 360f)
                        canvas.drawOval(-p.size, -p.size * 0.6f, p.size, p.size * 0.6f, paint)
                        canvas.restore()
                    }
                    else -> { // SNOW
                        canvas.drawCircle(p.x, p.y, p.size / 2f, paint)
                    }
                }
            }
        }
    }

    private data class Particle(
        var x: Float,
        var y: Float,
        val size: Float,
        val speed: Float,
        val angle: Float,
        val color: Int,
        val opacity: Int,
        val wiggle: Float,
        val wiggleSpeed: Float
    )
}
