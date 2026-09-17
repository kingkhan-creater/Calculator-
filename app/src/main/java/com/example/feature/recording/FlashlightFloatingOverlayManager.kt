package com.example.feature.recording

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.R
import kotlin.math.abs

/**
 * Manages the Flashlight Camouflage Floating Button.
 *
 * Requirements:
 * 1. Appears as a sleek Flashlight / Torch icon floating on the edge of the screen.
 * 2. Touching / tapping this button toggles the device hardware Flashlight ON / OFF.
 * 3. Long-pressing Volume DOWN button removes / hides this floating icon and locks controls.
 * 4. To restore the button controls & floating icon, the user must open the Calculator Vault.
 */
object FlashlightFloatingOverlayManager {
    private const val TAG = "FlashlightOverlay"
    private const val PREFS_NAME = "flashlight_floating_overlay_prefs"
    private const val KEY_FLOATING_ENABLED = "floating_flashlight_button_enabled"
    private const val KEY_CONTROLS_LOCKED = "floating_controls_locked_until_vault_opened"
    private const val KEY_POS_X = "floating_pos_x"
    private const val KEY_POS_Y = "floating_pos_y"

    @SuppressLint("StaticFieldLeak")
    private var floatingView: FrameLayout? = null
    private var windowManager: WindowManager? = null
    private var activeContext: Context? = null
    private var iconImageView: ImageView? = null
    private var backgroundDrawable: GradientDrawable? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isFloatingButtonEnabled(context: Context): Boolean {
        // Enabled by default so user gets the flashlight camouflage control
        return getPrefs(context).getBoolean(KEY_FLOATING_ENABLED, true)
    }

    fun setFloatingButtonEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FLOATING_ENABLED, enabled).apply()
        if (enabled) {
            show(context)
        } else {
            hide()
        }
    }

    fun isControlsLocked(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CONTROLS_LOCKED, false)
    }

    private fun setControlsLocked(context: Context, locked: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_CONTROLS_LOCKED, locked).apply()
    }

    /**
     * Called when the user long-presses Volume DOWN.
     * Removes the floating flashlight icon and locks button controls until vault is opened.
     */
    fun hideAndLock(context: Context) {
        mainHandler.post {
            // 1. Turn off flashlight if active
            FlashlightManager.setTorch(context, false)

            // 2. Hide floating view
            hide()

            // 3. Mark controls locked
            setControlsLocked(context, true)
            Log.d(TAG, "Floating flashlight button hidden and controls locked via Volume DOWN long-press.")
        }
    }

    /**
     * Called when the user unlocks the Vault (enters Master PIN in Calculator).
     * Clears the lock and restores the floating flashlight button.
     */
    fun unlockAndRestore(context: Context) {
        mainHandler.post {
            setControlsLocked(context, false)
            Log.d(TAG, "Controls unlocked via Vault Master PIN. Restoring floating flashlight button.")
            if (isFloatingButtonEnabled(context)) {
                show(context)
            }
        }
    }

    /**
     * Shows the floating flashlight button on screen.
     */
    fun show(context: Context) {
        mainHandler.post {
            if (floatingView != null) {
                // Already showing, just update visual
                updateTorchVisual(FlashlightManager.isTorchOn)
                return@post
            }

            if (isControlsLocked(context)) {
                Log.d(TAG, "Controls are locked. Flashlight button will not show until Vault is opened.")
                return@post
            }

            if (!isFloatingButtonEnabled(context)) {
                return@post
            }

            val appContext = context.applicationContext
            activeContext = appContext

            val wm = (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                ?: (appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                ?: return@post
            windowManager = wm

            try {
                createFloatingView(context, wm)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create floating flashlight view: ${e.message}", e)
            }
        }
    }

    /**
     * Hides and removes the floating flashlight button.
     */
    fun hide() {
        mainHandler.post {
            try {
                floatingView?.let { view ->
                    windowManager?.removeView(view)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating view: ${e.message}")
            } finally {
                floatingView = null
                iconImageView = null
                backgroundDrawable = null
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingView(context: Context, wm: WindowManager) {
        val density = context.resources.displayMetrics.density
        val buttonSize = (52 * density).toInt()
        val iconSize = (30 * density).toInt()

        // 1. Create root frame layout
        val root = FrameLayout(context).apply {
            clipToOutline = false
            elevation = 12 * density
        }

        // 2. Circular background drawable
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
        }
        backgroundDrawable = bg
        root.background = bg

        // 3. Flashlight Icon Image View
        val iv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        iconImageView = iv

        val ivParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
            gravity = Gravity.CENTER
        }
        root.addView(iv, ivParams)

        // 4. Determine Window Type
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (context is android.accessibilityservice.AccessibilityService) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else if (android.provider.Settings.canDrawOverlays(context)) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            }
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Saved coordinates or default edge position
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        val prefs = getPrefs(context)
        val defaultX = screenWidth - buttonSize - (12 * density).toInt()
        val defaultY = screenHeight / 2 - buttonSize / 2

        val initialX = prefs.getInt(KEY_POS_X, defaultX)
        val initialY = prefs.getInt(KEY_POS_Y, defaultY)

        val params = WindowManager.LayoutParams(
            buttonSize,
            buttonSize,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        // 5. Drag and Tap handling
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var initialTouchX = 0f
        var initialTouchY = 0f
        var startX = 0
        var startY = 0
        var isDragging = false
        var downTimeMs = 0L

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    startX = params.x
                    startY = params.y
                    isDragging = false
                    downTimeMs = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (!isDragging && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = startX + deltaX
                        params.y = startY + deltaY
                        try {
                            wm.updateViewLayout(root, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - downTimeMs
                    if (!isDragging && duration < 500) {
                        // Single Tap -> Toggle Flashlight ON / OFF
                        root.performClick()
                        FlashlightManager.toggleTorch(context)
                    } else if (isDragging) {
                        // Snap to nearest side edge (Left or Right)
                        val midX = screenWidth / 2
                        val edgeMargin = (10 * density).toInt()
                        params.x = if (params.x + buttonSize / 2 < midX) {
                            edgeMargin
                        } else {
                            screenWidth - buttonSize - edgeMargin
                        }
                        // Clamp Y within screen bounds
                        val minY = (50 * density).toInt()
                        val maxY = screenHeight - buttonSize - (50 * density).toInt()
                        params.y = params.y.coerceIn(minY, maxY)

                        try {
                            wm.updateViewLayout(root, params)
                            prefs.edit()
                                .putInt(KEY_POS_X, params.x)
                                .putInt(KEY_POS_Y, params.y)
                                .apply()
                        } catch (_: Exception) {}
                    }
                    true
                }
                else -> false
            }
        }

        floatingView = root
        wm.addView(root, params)

        // Initialize state visuals
        updateTorchVisual(FlashlightManager.isTorchOn)
    }

    /**
     * Updates the flashlight button visual appearance based on whether torch is ON or OFF.
     */
    fun updateTorchVisual(isTorchOn: Boolean) {
        mainHandler.post {
            val ctx = activeContext ?: return@post
            val iv = iconImageView ?: return@post
            val bg = backgroundDrawable ?: return@post

            val density = ctx.resources.displayMetrics.density
            if (isTorchOn) {
                // Torch ON: Bright glowing amber/yellow design
                bg.setColor(Color.parseColor("#FFF59E0B")) // Amber glow
                bg.setStroke((3 * density).toInt(), Color.parseColor("#FEF08A")) // Bright yellow rim
                iv.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.ic_flashlight_on))
                iv.setColorFilter(Color.parseColor("#1E1E24")) // Dark contrast torch on glowing circle
            } else {
                // Torch OFF: Sleek dark obsidian disc with subtle silver rim
                bg.setColor(Color.parseColor("#E61A1D24")) // 90% obsidian
                bg.setStroke((2 * density).toInt(), Color.parseColor("#44FFFFFF")) // Subtle frosted rim
                iv.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.ic_flashlight_off))
                iv.setColorFilter(Color.parseColor("#E2E8F0")) // Crisp metallic torch
            }
        }
    }
}
