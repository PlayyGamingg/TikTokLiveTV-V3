package com.example.tiktoklive

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

/** Đăng nhập TikTok bằng mã QR: quét mã bằng app TikTok trên điện thoại. */
class LoginActivity : Activity() {
    private lateinit var web: WebView
    private val handler = Handler(Looper.getMainLooper())

    private val check = object : Runnable {
        override fun run() {
            val ck = try { CookieManager.getInstance().getCookie("https://www.tiktok.com") ?: "" } catch (e: Exception) { "" }
            if (ck.contains("sessionid=")) {
                CookieManager.getInstance().flush()
                Toast.makeText(this@LoginActivity, "Đăng nhập thành công", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            handler.postDelayed(this, 2000)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = PlayerActivity.UA
            webViewClient = WebViewClient()
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        val hint = TextView(this).apply {
            text = "Mở ứng dụng TikTok trên điện thoại và quét mã QR trên màn hình này để đăng nhập. " +
                "Nếu chưa thấy mã QR, hãy chọn \"Sử dụng mã QR\". Đăng nhập xong màn hình sẽ tự đóng. Nhấn Back để thoát."
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(32, 16, 32, 16)
            background = GradientDrawable().apply { setColor(0xCC000000.toInt()); cornerRadius = 24f }
        }
        val root = FrameLayout(this)
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        root.addView(hint, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 32
        })
        setContentView(root)

        web.loadUrl("https://www.tiktok.com/login/qrcode")
        handler.postDelayed(check, 2000)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        web.destroy()
        super.onDestroy()
    }
}
