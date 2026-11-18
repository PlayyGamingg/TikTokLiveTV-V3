package com.example.tiktoklive

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        /** Tạm tắt mục đăng nhập bằng mã QR. Đổi thành true để bật lại. */
        const val SHOW_LOGIN = false
    }
    private lateinit var prefs: SharedPreferences
    private lateinit var input: EditText
    private lateinit var sw: Switch
    private lateinit var loginStatus: TextView
    private lateinit var btnLogin: Button
    private lateinit var btnLogout: Button
    private lateinit var histBox: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("cfg", MODE_PRIVATE)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(24), dp(48), dp(24))
        }
        fun add(v: View, top: Int = 12) =
            col.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })

        add(TextView(this).apply {
            text = "TikTok LIVE cho Android TV"
            textSize = 28f; setTextColor(Color.WHITE)
        }, 0)

        input = EditText(this).apply {
            hint = "Nhập ID TikTok (vd: tenkenh, không cần @)"
            setText(prefs.getString("user", ""))
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setTextColor(Color.WHITE); setHintTextColor(0xFF888888.toInt())
            textSize = 22f
            setOnEditorActionListener { _, _, _ -> start(text.toString()); true }
        }
        add(input)

        sw = Switch(this).apply {
            text = "Hiện bình luận"
            textSize = 20f; setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("comments", true)
        }
        add(sw)

        add(Ui.mkButton(this, 22f).apply {
            text = "Xem LIVE"
            setOnClickListener { start(input.text.toString()) }
        })

        if (SHOW_LOGIN) {
        loginStatus = TextView(this).apply { textSize = 16f }
        add(loginStatus, 20)

        btnLogin = Ui.mkButton(this, 18f).apply {
            setOnClickListener {
                // Đăng nhập lại: xoá phiên cũ để màn hình QR hiện ra
                if (isLoggedIn()) { CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush() }
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
            }
        }
        add(btnLogin, 8)

        btnLogout = Ui.mkButton(this, 16f).apply {
            text = "Đăng xuất TikTok"
            setOnClickListener {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                refreshLogin()
            }
        }
        add(btnLogout, 8)
        }

        add(TextView(this).apply {
            text = "Lịch sử tìm kiếm"
            textSize = 20f; setTextColor(Color.WHITE)
        }, 24)
        histBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(histBox, 8)

        add(TextView(this).apply {
            text = "Khi đang xem: chạm màn hình hoặc nhấn OK/MENU để mở tùy chọn. ←/→ bật/tắt bình luận, ↑/↓ đổi độ phân giải."
            textSize = 15f; setTextColor(0xFFAAAAAA.toInt())
        }, 20)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFillViewport = true
            addView(col)
        })
    }

    override fun onResume() {
        super.onResume()
        if (SHOW_LOGIN) refreshLogin()
        refreshHistory()
    }

    // ---------- đăng nhập ----------
    private fun isLoggedIn(): Boolean = try {
        (CookieManager.getInstance().getCookie("https://www.tiktok.com") ?: "").contains("sessionid=")
    } catch (e: Exception) { false }

    private fun refreshLogin() {
        val li = isLoggedIn()
        loginStatus.text = if (li) "Đã đăng nhập TikTok ✓ — có thể xem độ phân giải cao"
        else "Chưa đăng nhập TikTok — đăng nhập để xem độ phân giải cao"
        loginStatus.setTextColor(if (li) 0xFF7CFC9A.toInt() else 0xFFFFB74D.toInt())
        btnLogin.text = if (li) "Đăng nhập lại bằng mã QR" else "Đăng nhập TikTok bằng mã QR"
        btnLogout.visibility = if (li) View.VISIBLE else View.GONE
    }

    // ---------- lịch sử ----------
    private fun history(): MutableList<String> =
        (prefs.getString("hist", "") ?: "").split("\n").filter { it.isNotBlank() }.toMutableList()

    private fun saveHistory(list: List<String>) {
        prefs.edit().putString("hist", list.take(10).joinToString("\n")).apply()
    }

    private fun refreshHistory() {
        histBox.removeAllViews()
        val h = history()
        if (h.isEmpty()) {
            histBox.addView(TextView(this).apply {
                text = "Chưa có lịch sử"; textSize = 16f; setTextColor(0xFF888888.toInt())
            })
            return
        }
        h.forEach { u ->
            histBox.addView(Ui.mkButton(this, 18f).apply {
                text = "@$u"
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setOnClickListener { start(u) }
                setOnLongClickListener {
                    val l = history(); l.remove(u); saveHistory(l); refreshHistory(); true
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        histBox.addView(Ui.mkButton(this, 16f).apply {
            text = "Xóa toàn bộ lịch sử"
            setOnClickListener { saveHistory(emptyList()); refreshHistory() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        histBox.addView(TextView(this).apply {
            text = "Giữ OK (hoặc nhấn giữ) lên một mục để xóa mục đó."
            textSize = 14f; setTextColor(0xFF888888.toInt())
        })
    }

    // ---------- xem live ----------
    private fun start(raw: String) {
        var u = raw.trim()
        Regex("@([^/?\\s]+)").find(u)?.let { u = it.groupValues[1] }
        u = u.removePrefix("@")
        if (u.isEmpty()) { Toast.makeText(this, "Hãy nhập ID kênh", Toast.LENGTH_SHORT).show(); return }
        prefs.edit().putString("user", u).putBoolean("comments", sw.isChecked).apply()
        val h = history(); h.remove(u); h.add(0, u); saveHistory(h)
        startActivity(Intent(this, PlayerActivity::class.java).putExtra("user", u))
    }
}
