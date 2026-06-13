package com.example

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

data class WallpaperConfig(
    val bgType: String = "PRESET_CYBERPUNK", // "PRESET_CYBERPUNK", "PRESET_MOUNTAIN", "PRESET_ARCH", "CUSTOM", "VIDEO"
    val customBgPath: String? = null,
    val customFgPath: String? = null,
    val customVideoPath: String? = null,
    val clockColor: Int = Color.WHITE,
    val clockHourColor: Int = Color.WHITE,
    val clockMinColor: Int = Color.WHITE,
    val clockStyle: String = "BOLD", // "STANDARD", "BOLD", "SERIF", "DIGITAL", "OUTLINE"
    val depthAlpha: Float = 1.0f,
    val particleStyle: String = "NONE", // "NONE", "RAIN", "SNOW", "SAKURA"
    val parallaxSensitivity: Float = 1.0f,
    val scaleFactor: Float = 1.0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val clockOffsetY: Float = 250f, // vertical offset for clock rendering
    val clockScale: Float = 1.0f,
    val dimOpacity: Float = 0.0f
) {
    companion object {
        private const val PREFS_NAME = "depth_wallpaper_prefs"

        fun load(context: Context): WallpaperConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return WallpaperConfig(
                bgType = prefs.getString("bgType", "PRESET_CYBERPUNK") ?: "PRESET_CYBERPUNK",
                customBgPath = prefs.getString("customBgPath", null),
                customFgPath = prefs.getString("customFgPath", null),
                customVideoPath = prefs.getString("customVideoPath", null),
                clockColor = prefs.getInt("clockColor", Color.WHITE),
                clockHourColor = prefs.getInt("clockHourColor", Color.WHITE),
                clockMinColor = prefs.getInt("clockMinColor", Color.parseColor("#FF6B6B")), // accent red/coral
                clockStyle = prefs.getString("clockStyle", "BOLD") ?: "BOLD",
                depthAlpha = prefs.getFloat("depthAlpha", 1.0f),
                particleStyle = prefs.getString("particleStyle", "NONE") ?: "NONE",
                parallaxSensitivity = prefs.getFloat("parallaxSensitivity", 1.0f),
                scaleFactor = prefs.getFloat("scaleFactor", 1.0f),
                offsetX = prefs.getFloat("offsetX", 0f),
                offsetY = prefs.getFloat("offsetY", 0f),
                clockOffsetY = prefs.getFloat("clockOffsetY", 350f), // standard upper-third
                clockScale = prefs.getFloat("clockScale", 1.0f),
                dimOpacity = prefs.getFloat("dimOpacity", 0.0f)
            )
        }

        fun save(context: Context, config: WallpaperConfig) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("bgType", config.bgType)
                putString("customBgPath", config.customBgPath)
                putString("customFgPath", config.customFgPath)
                putString("customVideoPath", config.customVideoPath)
                putInt("clockColor", config.clockColor)
                putInt("clockHourColor", config.clockHourColor)
                putInt("clockMinColor", config.clockMinColor)
                putString("clockStyle", config.clockStyle)
                putFloat("depthAlpha", config.depthAlpha)
                putString("particleStyle", config.particleStyle)
                putFloat("parallaxSensitivity", config.parallaxSensitivity)
                putFloat("scaleFactor", config.scaleFactor)
                putFloat("offsetX", config.offsetX)
                putFloat("offsetY", config.offsetY)
                putFloat("clockOffsetY", config.clockOffsetY)
                putFloat("clockScale", config.clockScale)
                putFloat("dimOpacity", config.dimOpacity)
                apply()
            }
        }
    }
}
