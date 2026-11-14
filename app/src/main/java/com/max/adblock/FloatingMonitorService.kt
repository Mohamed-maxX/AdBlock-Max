package com.max.adblock

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.max.adblock.databinding.LayoutFloatingMonitorBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FloatingMonitorService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var binding: LayoutFloatingMonitorBinding? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var updateJob: Job? = null
    private lateinit var prefs: PreferencesManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        showFloatingMonitor()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingMonitor() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        val viewBinding = LayoutFloatingMonitorBinding.inflate(inflater)
        binding = viewBinding
        floatingView = viewBinding.root

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 200
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Draggable touch handling with strict screen bounds clamping
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        viewBinding.floatingRoot.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val rawNewX = initialX + (event.rawX - initialTouchX).toInt()
                    val rawNewY = initialY + (event.rawY - initialTouchY).toInt()
                    val pillWidth = floatingView?.width ?: 200
                    val pillHeight = floatingView?.height ?: 80

                    // Strictly clamp overlay within screen bounds:
                    // Never allow overlay to escape screen or cover critical system areas
                    params.x = rawNewX.coerceIn(0, (screenWidth - pillWidth).coerceAtLeast(0))
                    params.y = rawNewY.coerceIn(50, (screenHeight - pillHeight - 50).coerceAtLeast(50))

                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        viewBinding.btnFloatingClose.setOnClickListener {
            stopSelf()
        }

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return
        }

        // Loop to update Ping and Blocked count
        updateJob = scope.launch {
            while (isActive) {
                val ping = PingTester.measurePingMs("1.1.1.1", 53)
                val blocked = prefs.blockedCountToday
                binding?.tvFloatingPing?.text = if (ping > 0) "$ping ms" else "-- ms"
                binding?.tvFloatingBlocked?.text = "$blocked 🛡️"
                delay(3000)
            }
        }
    }

    override fun onDestroy() {
        scope.coroutineContext.cancelChildren()
        updateJob?.cancel()
        updateJob = null

        floatingView?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager?.removeViewImmediate(view)
                } else {
                    windowManager?.removeView(view)
                }
            } catch (e: IllegalArgumentException) {
                // View not attached to window manager
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                floatingView = null
                binding = null
            }
        }
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        onDestroy()
        return super.onUnbind(intent)
    }
}
