package com.example.tiktoklive

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class SplashActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        val dm = resources.displayMetrics
        val base = minOf(dm.heightPixels, dm.widthPixels).toFloat()
        val logoSize = (base * 0.20f).toInt()
        val t1 = "App được phát triển bởi"
        val t2 = "Trung Vũ"

        // Dòng 1 chữ bé; dòng 2 chữ to, tính cỡ chữ sao cho hai dòng dài bằng nhau
        val size1 = base * 0.04f
        val w1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size1 }.measureText(t1)
        val p2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 100f; typeface = Typeface.DEFAULT_BOLD }
        val size2 = 100f * w1 / p2.measureText(t2)

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_note)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val line1 = TextView(this).apply {
            text = t1
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size1)
            includeFontPadding = false
        }
        val line2 = TextView(this).apply {
            text = t2
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size2)
            includeFontPadding = false
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(line1, LinearLayout.LayoutParams(-2, -2))
            addView(line2, LinearLayout.LayoutParams(-2, -2).apply { topMargin = (base * 0.008f).toInt() })
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(logo, LinearLayout.LayoutParams(logoSize, logoSize))
            addView(texts, LinearLayout.LayoutParams(-2, -2).apply { marginStart = (base * 0.04f).toInt() })
            alpha = 0f
        }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(row, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(root)
        row.animate().alpha(1f).setDuration(700).start()

        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2200)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
