package com.example

import android.graphics.*

object ProceduralPresetGenerator {

    fun generatePreset(type: String, isForeground: Boolean, width: Int = 1080, height: Int = 1920): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (type) {
            "PRESET_CYBERPUNK" -> {
                if (!isForeground) {
                    // Draw Background: Neon retro sunset with grid lines
                    val skyGrad = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(Color.parseColor("#050515"), Color.parseColor("#0D0B2E"), Color.parseColor("#381352")),
                        floatArrayOf(0.0f, 0.5f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = skyGrad
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    paint.shader = null

                    // Draw Synthwave Giant Red/Magenta Sun
                    val sunY = height * 0.45f
                    val sunRadius = width * 0.35f
                    val sunGrad = LinearGradient(
                        0f, sunY - sunRadius, 0f, sunY + sunRadius,
                        intArrayOf(Color.parseColor("#FF007F"), Color.parseColor("#FF5E00")),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = sunGrad
                    canvas.drawCircle(width / 2f, sunY, sunRadius, paint)
                    paint.shader = null

                    // Draw Sun slices (Retro sunset horizontal stripes)
                    paint.color = Color.parseColor("#0D0B2E")
                    val stripeHeight = 12f
                    for (i in 0..10) {
                        val currY = sunY + (i * 24f) + 10f
                        if (currY < sunY + sunRadius) {
                            canvas.drawRect(0f, currY, width.toFloat(), currY + stripeHeight, paint)
                        }
                    }

                    // Draw Retro horizon grid perspective lines
                    paint.color = Color.parseColor("#00FFFF")
                    paint.strokeWidth = 2f
                    paint.alpha = 150
                    val horizonY = height * 0.6f
                    canvas.drawLine(0f, horizonY, width.toFloat(), horizonY, paint)

                    // Vertical converging perspective lines
                    val cols = 14
                    for (i in 0..cols) {
                        val startX = (width / cols.toFloat()) * i
                        val endX = (width / 2f) + (startX - (width / 2f)) * 0.15f
                        canvas.drawLine(startX, height.toFloat(), endX, horizonY, paint)
                    }
                    // Horizontal lines with perspective distance steps
                    var currentGridY = horizonY
                    var step = 8f
                    while (currentGridY < height) {
                        canvas.drawLine(0f, currentGridY, width.toFloat(), currentGridY, paint)
                        step *= 1.35f
                        currentGridY += step
                    }
                } else {
                    // Draw Foreground Cutout: Tall organic cyberpunk skyscraper silhouettes in front
                    // Transparent bitmap default
                    bitmap.eraseColor(Color.TRANSPARENT)

                    // Draw central futuristic buildings
                    paint.color = Color.parseColor("#03030F")
                    val path = Path()
                    path.moveTo(0f, height.toFloat())
                    path.lineTo(0f, height * 0.72f)
                    path.lineTo(width * 0.15f, height * 0.72f)
                    path.lineTo(width * 0.15f, height * 0.65f) // Spire building
                    path.lineTo(width * 0.2f, height * 0.52f) // Pyramid top
                    path.lineTo(width * 0.25f, height * 0.65f)
                    path.lineTo(width * 0.35f, height * 0.68f)
                    path.lineTo(width * 0.38f, height * 0.40f) // Tall tower
                    path.lineTo(width * 0.44f, height * 0.40f)
                    path.lineTo(width * 0.47f, height * 0.32f) // Antenna tip
                    path.lineTo(width * 0.48f, height * 0.44f)
                    path.lineTo(width * 0.58f, height * 0.49f) // Flat tower
                    path.lineTo(width * 0.62f, height * 0.49f)
                    path.lineTo(width * 0.7f, height * 0.65f)
                    path.lineTo(width * 0.85f, height * 0.58f) // Right tower angled
                    path.lineTo(width * 0.92f, height * 0.74f)
                    path.lineTo(width.toFloat(), height * 0.74f)
                    path.lineTo(width.toFloat(), height.toFloat())
                    path.close()
                    canvas.drawPath(path, paint)

                    // Draw some glowing windows on the buildings
                    paint.color = Color.parseColor("#00FFFF") // Neon cyan windows
                    val rand = java.util.Random(42)
                    for (bRow in 0..15) {
                        for (bCol in 0..12) {
                            val winX = (width * 0.1f) + (bCol * 70f)
                            val winY = (height * 0.55f) + (bRow * 85f)
                            if (bitmap.getPixel(winX.toInt().coerceIn(0, width - 1), winY.toInt().coerceIn(0, height - 1)) == Color.parseColor("#03030F")) {
                                if (rand.nextFloat() > 0.4f) {
                                    paint.color = if (rand.nextBoolean()) Color.parseColor("#00FFFF") else Color.parseColor("#FF007F")
                                    canvas.drawRect(winX, winY, winX + 15f, winY + 25f, paint)
                                }
                            }
                        }
                    }

                    // Add a bright neon accent edge line tracing the towers
                    paint.shader = LinearGradient(0f, height * 0.4f, width.toFloat(), height.toFloat(),
                        intArrayOf(Color.parseColor("#00FFFF"), Color.parseColor("#FF007F")), null, Shader.TileMode.CLAMP)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 6f
                    canvas.drawPath(path, paint)
                    paint.style = Paint.Style.FILL
                    paint.shader = null
                }
            }
            "PRESET_MOUNTAIN" -> {
                if (!isForeground) {
                    // Draw Background: Deep sunset twilight sky
                    val skyGrad = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(Color.parseColor("#0C1033"), Color.parseColor("#2B1B54"), Color.parseColor("#C35175"), Color.parseColor("#ECA377")),
                        floatArrayOf(0.0f, 0.4f, 0.75f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = skyGrad
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    paint.shader = null

                    // Draw full pale moon
                    paint.color = Color.parseColor("#FFFDDD")
                    paint.alpha = 200
                    canvas.drawCircle(width * 0.75f, height * 0.28f, width * 0.08f, paint)

                    // Draw soft atmospheric sky clouds
                    paint.shader = RadialGradient(
                        width * 0.75f, height * 0.28f, width * 0.25f,
                        Color.parseColor("#33FFFDDD"), Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(width * 0.75f, height * 0.28f, width * 0.25f, paint)
                    paint.shader = null
                } else {
                    // Draw Foreground: Majestic Mountain peak cutout
                    bitmap.eraseColor(Color.TRANSPARENT)

                    // Mountain ridge path
                    val path = Path()
                    path.moveTo(0f, height.toFloat())
                    path.lineTo(0f, height * 0.65f)
                    path.lineTo(width * 0.15f, height * 0.58f)
                    path.lineTo(width * 0.35f, height * 0.48f) // Jagged peak 1
                    path.lineTo(width * 0.42f, height * 0.52f)
                    path.lineTo(width * 0.65f, height * 0.35f) // The majestic central peak (behind clock numbers potentially!)
                    path.lineTo(width * 0.72f, height * 0.45f)
                    path.lineTo(width * 0.88f, height * 0.40f) // Peak 3
                    path.lineTo(width.toFloat(), height * 0.55f)
                    path.lineTo(width.toFloat(), height.toFloat())
                    path.close()

                    // Main mountain dark gradient
                    val mountainGrad = LinearGradient(
                        0f, height * 0.35f, 0f, height.toFloat(),
                        intArrayOf(Color.parseColor("#121430"), Color.parseColor("#06081A")),
                        null, Shader.TileMode.CLAMP
                    )
                    paint.shader = mountainGrad
                    canvas.drawPath(path, paint)
                    paint.shader = null

                    // Snow ridges on peaks
                    paint.color = Color.parseColor("#F5EEFF")
                    paint.alpha = 210
                    val snowPath = Path()
                    snowPath.moveTo(width * 0.35f, height * 0.48f)
                    snowPath.lineTo(width * 0.32f, height * 0.53f)
                    snowPath.lineTo(width * 0.36f, height * 0.51f)
                    snowPath.lineTo(width * 0.35f, height * 0.48f)

                    snowPath.moveTo(width * 0.65f, height * 0.35f)
                    snowPath.lineTo(width * 0.60f, height * 0.48f)
                    snowPath.lineTo(width * 0.66f, height * 0.44f)
                    snowPath.lineTo(width * 0.65f, height * 0.35f)
                    canvas.drawPath(snowPath, paint)
                }
            }
            "PRESET_ARCH" -> {
                if (!isForeground) {
                    // Draw Background: Soft retro terracotta color gradients
                    val skyGrad = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(Color.parseColor("#E9DCD1"), Color.parseColor("#DFBCA2"), Color.parseColor("#D49685")),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = skyGrad
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    paint.shader = null

                    // Draw minimalist red/peach abstract giant circle
                    paint.color = Color.parseColor("#C35C4A")
                    canvas.drawCircle(width / 2f, height * 0.5f, width * 0.33f, paint)
                } else {
                    // Draw Foreground: Minimal organic archway (clock sits inside it)
                    bitmap.eraseColor(Color.TRANSPARENT)

                    // Left Column Pillar
                    paint.color = Color.parseColor("#442E28") // Dark terracotta/stone
                    canvas.drawRect(0f, height * 0.35f, width * 0.18f, height.toFloat(), paint)

                    // Right Column Pillar
                    canvas.drawRect(width * 0.82f, height * 0.35f, width.toFloat(), height.toFloat(), paint)

                    // Curved Arch ceiling connecting columns (clock numbers will sit partly behind this curve)
                    val archPath = Path()
                    archPath.moveTo(0f, height.toFloat())
                    archPath.lineTo(0f, height * 0.35f)
                    archPath.cubicTo(
                        width * 0.1f, height * 0.22f,
                        width * 0.9f, height * 0.22f,
                        width.toFloat(), height * 0.35f
                    )
                    archPath.lineTo(width.toFloat(), height.toFloat())
                    // Cutout the middle by subtracting an inner arch
                    val innerArch = Path()
                    innerArch.moveTo(width * 0.18f, height.toFloat())
                    innerArch.lineTo(width * 0.18f, height * 0.38f)
                    innerArch.cubicTo(
                        width * 0.25f, height * 0.28f,
                        width * 0.75f, height * 0.28f,
                        width * 0.82f, height * 0.38f
                    )
                    innerArch.lineTo(width * 0.82f, height.toFloat())
                    innerArch.close()

                    archPath.op(innerArch, Path.Op.DIFFERENCE)
                    canvas.drawPath(archPath, paint)

                    // Add nice shadows or bevel highlights along the arch edge
                    paint.color = Color.parseColor("#261714")
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 14f
                    val edgePath = Path()
                    edgePath.moveTo(width * 0.18f, height.toFloat())
                    edgePath.lineTo(width * 0.18f, height * 0.38f)
                    edgePath.cubicTo(
                        width * 0.25f, height * 0.28f,
                        width * 0.75f, height * 0.28f,
                        width * 0.82f, height * 0.38f
                    )
                    edgePath.lineTo(width * 0.82f, height.toFloat())
                    canvas.drawPath(edgePath, paint)

                    paint.style = Paint.Style.FILL
                }
            }
        }
        return bitmap
    }
}
