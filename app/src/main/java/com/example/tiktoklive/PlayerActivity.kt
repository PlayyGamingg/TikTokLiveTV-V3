package com.example.tiktoklive

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONTokener
import java.io.ByteArrayInputStream

data class StreamInfo(val url: String, val rank: Int, val flv: Boolean)

/**
 * Video: ExoPlayer toàn màn hình.
 * Bình luận: WebView trong suốt đặt chồng lên góc dưới-trái của video (chữ trắng viền đen),
 * chỉ lấy cột chat của trang TikTok; phần nhập bình luận bị cắt bỏ.
 *
 * Điều khiển:
 *   Chạm màn hình / OK / MENU : mở-đóng thanh tùy chọn
 *   Trái / Phải               : bật / tắt bình luận
 *   Lên / Xuống               : đổi độ phân giải
 *   Play/Pause                : tạm dừng / phát
 *   Back                      : thoát
 */
class PlayerActivity : Activity() {

    companion object {
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val TEXT_MULT = floatArrayOf(0.75f, 1.0f, 1.3f, 1.6f)
        val TEXT_NAMES = arrayOf("Nhỏ", "Vừa", "To", "Rất to")
        val RANK_RE = Regex("_(ld|sd|hd|uhd|or)\\d*(?=[./?&])")

        // Lấy link luồng video (flv / m3u8) từ nội dung trang live
        const val EXTRACT_JS = """
        (function(){
          try{
            var h=document.documentElement.innerHTML;
            h=h.replace(/\\+u002f/gi,'/').replace(/\\+u0026/gi,'&').replace(/\\+\//g,'/').replace(/&amp;/g,'&');
            var m=h.match(/https?:\/\/[^"'\\\s<>]+\.(?:flv|m3u8)[^"'\\\s<>]*/g)||[];
            var p=(performance.getEntriesByType('resource')||[]).map(function(e){return e.name}).filter(function(n){return /\.(flv|m3u8)/.test(n)});
            var all=m.concat(p), o=[], s={};
            for(var i=0;i<all.length;i++){ if(!s[all[i]]){ s[all[i]]=1; o.push(all[i]); } }
            return JSON.stringify(o);
          }catch(e){ return '[]'; }
        })();
        """

        // Biến trang TikTok thành lớp phủ bình luận: nền trong suốt, chữ trắng viền đen, ẩn thanh bên/thanh trên
        const val TWEAK_JS = """
        (function(){
          var H=window.innerHeight;
          var st=document.getElementById('__tv_css');
          if(!st){ st=document.createElement('style'); st.id='__tv_css'; document.head.appendChild(st); }
          st.textContent=
            'html,body{overflow:hidden !important;background:transparent !important;background-color:transparent !important}'+
            '*{color:#fff !important;-webkit-text-fill-color:#fff !important;text-shadow:-1.5px -1.5px 0 #000,1.5px -1.5px 0 #000,-1.5px 1.5px 0 #000,1.5px 1.5px 0 #000,0 0 4px rgba(0,0,0,.9) !important}'+
            '*::-webkit-scrollbar{display:none !important}'+
            'video,canvas{visibility:hidden !important}'+
            '[class*="SideNav"],[class*="DivSideNav"],[data-e2e*="side-nav"],[data-e2e="live-side-nav"],'+
            '[class*="DivHeaderContainer"],[class*="HeaderContainer"],[id="app-header"],[data-e2e="tiktok-header"]{display:none !important}';

          document.querySelectorAll('video').forEach(function(v){ try{ v.pause(); v.muted=true; v.removeAttribute('src'); v.load(); }catch(e){} });

          // Các khung lớn -> nền trong suốt; ảnh lớn (ảnh bìa) -> ẩn
          var all=document.body.querySelectorAll('*');
          for(var j=0;j<all.length;j++){
            var e=all[j];
            if(!e.dataset||e.dataset.tvT) continue;
            var tg=e.tagName;
            if(tg==='SCRIPT'||tg==='STYLE'||tg==='path'||tg==='svg'||tg==='use'||tg==='g') continue;
            var q=e.getBoundingClientRect();
            if(q.width===0&&q.height===0) continue;
            e.dataset.tvT='1';
            if(tg==='IMG'){ if(q.width>=200&&q.height>=120) e.style.setProperty('visibility','hidden','important'); continue; }
            if(q.width>=160&&q.height>=30){
              e.style.setProperty('background-color','transparent','important');
              e.style.setProperty('background-image','none','important');
              e.style.setProperty('backdrop-filter','none','important');
            }
          }

          // Tự dò và ẩn thanh bên trái (không phụ thuộc tên class)
          if(!window.__tvSideDone){
            var nodes=document.body.querySelectorAll('div,nav,aside,section'), hidden=[];
            for(var i=0;i<nodes.length;i++){
              var el=nodes[i];
              var r=el.getBoundingClientRect();
              if(r.left<=2 && r.width>=120 && r.width<=420 && r.height>=H*0.8 && r.height<=H+4){
                var t=el.innerText||'';
                if(/LIVE|Follow|Khám phá|Explore|Nhận Xu|Thêm/i.test(t)){
                  el.style.setProperty('display','none','important'); hidden.push(el);
                }
              }
            }
            if(hidden.length){
              window.__tvSideDone=true;
              hidden.forEach(function(h){
                var p=h.parentElement;
                while(p && p!==document.documentElement){
                  var kids=p.children;
                  for(var k=0;k<kids.length;k++){
                    var cs=getComputedStyle(kids[k]);
                    if(parseInt(cs.marginLeft)>100) kids[k].style.setProperty('margin-left','0','important');
                    if(parseInt(cs.paddingLeft)>100) kids[k].style.setProperty('padding-left','0','important');
                    if(cs.position==='absolute' && parseInt(cs.left)>100) kids[k].style.setProperty('left','0','important');
                  }
                  var pcs=getComputedStyle(p);
                  if(parseInt(pcs.paddingLeft)>100) p.style.setProperty('padding-left','0','important');
                  if(parseInt(pcs.marginLeft)>100) p.style.setProperty('margin-left','0','important');
                  p=p.parentElement;
                }
              });
              window.dispatchEvent(new Event('resize'));
            }
          }
        })();
        """
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var web: WebView
    private lateinit var chatBox: FrameLayout
    private lateinit var msg: TextView
    private lateinit var barHolder: HorizontalScrollView
    private lateinit var btnCmt: Button
    private lateinit var btnQ: Button
    private lateinit var btnPause: Button
    private lateinit var btnT: Button

    private var user = ""
    private var chatUrl = ""
    private var screenW = 0
    private var screenH = 0
    private var showComments = true
    private var textIdx = 1
    private var barVisible = false

    private var streamFound = false
    private var pollCount = 0
    private var streams = listOf<StreamInfo>()
    private var ranks = listOf<Int>()
    private var curRank = -1
    private var attempt = 0

    // Độ phân giải thực đo được sau khi phát (vd 720p, 1080p60...)
    private val measuredP = HashMap<Int, Int>()
    private val measuredFps60 = HashMap<Int, Boolean>()
    private var fpsC0 = 0
    private var fpsT0 = 0L
    private var fpsRank = -1

    private val hideMsgRun = Runnable { msg.visibility = View.GONE }
    private val hideBarRun = Runnable { hideBar() }

    private val fpsRun = Runnable {
        val c = player.videoDecoderCounters
        if (c != null && fpsRank == curRank && player.isPlaying) {
            c.ensureUpdated()
            val dt = (SystemClock.elapsedRealtime() - fpsT0) / 1000f
            if (dt >= 2f) {
                val fps = (c.renderedOutputBufferCount - fpsC0) / dt
                measuredFps60[fpsRank] = fps >= 45f
                updateButtons()
            }
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            if (streamFound || isFinishing) return
            web.evaluateJavascript(EXTRACT_JS) { res -> handleExtract(res) }
            pollCount++
            if (pollCount > 25) {
                showMsg("Không lấy được luồng video. Kênh có thể chưa LIVE. Chạm màn hình > Tải lại", 0)
                return
            }
            handler.postDelayed(this, 2000)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (showComments && (web.url ?: "").startsWith("http")) web.evaluateJavascript(TWEAK_JS, null)
            handler.postDelayed(this, 2000)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        goImmersive()

        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        showComments = prefs.getBoolean("comments", true)
        textIdx = prefs.getInt("ts", 1).coerceIn(0, TEXT_MULT.size - 1)
        user = intent.getStringExtra("user") ?: ""
        chatUrl = "https://www.tiktok.com/@$user/live"
        screenW = resources.displayMetrics.widthPixels
        screenH = resources.displayMetrics.heightPixels

        // ---------- Player ----------
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(UA)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.tiktok.com/"))
        val load = DefaultLoadControl.Builder().setBufferDurationsMs(3000, 10000, 1000, 2000).build()
        player = ExoPlayer.Builder(this, DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(load)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> { handler.removeCallbacks(hideMsgRun); msg.visibility = View.GONE }
                    Player.STATE_BUFFERING -> if (streamFound) showMsg("Đang tải video…", 0)
                    Player.STATE_ENDED -> showMsg("Livestream đã kết thúc hoặc mất kết nối. Chạm màn hình > Tải lại", 0)
                    else -> {}
                }
            }
            override fun onVideoSizeChanged(v: VideoSize) {
                if (v.width > 0 && v.height > 0 && curRank >= 0) {
                    measuredP[curRank] = minOf(v.width, v.height)
                    updateButtons()
                    startFpsProbe()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                attempt++
                tryPlay()
            }
        })

        playerView = PlayerView(this).apply {
            useController = false
            isFocusable = false
            setBackgroundColor(Color.BLACK)
        }
        playerView.player = player

        // ---------- Lớp phủ bình luận (WebView trong suốt, chỉ hiện cột chat) ----------
        web = WebView(this).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.useWideViewPort = true
            settings.userAgentString = UA
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView?, url: String?) {
                if (showComments && (url ?: "").startsWith("http")) web.evaluateJavascript(TWEAK_JS, null)
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val p = request?.url?.path ?: return null
                if (p.endsWith(".flv") || p.endsWith(".m3u8") || p.endsWith(".m4s") ||
                    p.endsWith(".mp4") || p.endsWith(".webm")) {
                    return WebResourceResponse("text/plain", "utf-8", 200, "OK", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }
        }

        val chatW = (screenW * 0.30f).toInt()
        val chatH = (screenH * 0.58f).toInt()
        val cut = (screenH * 0.13f).toInt()   // phần dưới (ô nhập bình luận) bị cắt bỏ
        chatBox = FrameLayout(this).apply {
            clipChildren = true
            visibility = if (showComments) View.VISIBLE else View.GONE
        }
        chatBox.addView(web, FrameLayout.LayoutParams(screenW, chatH + cut).apply {
            leftMargin = -(screenW - chatW)
        })

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        root.addView(chatBox, FrameLayout.LayoutParams(chatW, chatH).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = 8
            bottomMargin = 8
        })

        // Lớp bắt chạm: chạm màn hình để mở / đóng thanh tùy chọn
        val touch = View(this).apply {
            isFocusable = false
            setOnClickListener { if (barVisible) hideBar() else showBar() }
        }
        root.addView(touch, FrameLayout.LayoutParams(-1, -1))

        // ---------- Thông báo ----------
        msg = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(32, 16, 32, 16)
            background = GradientDrawable().apply { setColor(0xCC000000.toInt()); cornerRadius = 24f }
            visibility = View.GONE
        }
        root.addView(msg, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = 48
        })

        // ---------- Thanh tùy chọn ----------
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 16)
            background = GradientDrawable().apply { setColor(0xDD000000.toInt()); cornerRadius = 28f }
        }
        btnCmt = mkBtn { setComments(!showComments) }
        btnQ = mkBtn { changeQuality(+1, true) }
        btnPause = mkBtn { player.playWhenReady = !player.playWhenReady }
        btnT = mkBtn {
            textIdx = (textIdx + 1) % TEXT_MULT.size
            getSharedPreferences("cfg", MODE_PRIVATE).edit().putInt("ts", textIdx).apply()
            applyTextSize()
        }
        val btnReload = mkBtn { reload() }.apply { text = "Tải lại" }
        val btnExit = mkBtn { finish() }.apply { text = "Thoát" }
        listOf(btnCmt, btnQ, btnPause, btnT, btnReload, btnExit).forEach { bt ->
            bar.addView(bt, LinearLayout.LayoutParams(-2, -2).apply { marginStart = 8; marginEnd = 8 })
        }
        barHolder = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
            addView(bar)
        }
        root.addView(barHolder, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 48
        })

        setContentView(root)
        applyTextSize()
        updateButtons()

        showMsg("Đang lấy luồng video…", 0)
        web.loadUrl(chatUrl)
        handler.postDelayed(poll, 3000)
        handler.postDelayed(tick, 2000)
    }

    // ================= UI helpers =================
    private fun mkBtn(onClick: () -> Unit): Button = Ui.mkButton(this, 15f).apply {
        setOnClickListener { onClick(); updateButtons(); resetBarTimer() }
    }

    private fun updateButtons() {
        btnCmt.text = "Bình luận: " + if (showComments) "BẬT" else "TẮT"
        btnQ.text = "Độ phân giải: " + if (curRank >= 0) rankLabel(curRank) else "-"
        btnPause.text = if (player.playWhenReady) "Tạm dừng" else "Phát"
        btnT.text = "Cỡ chữ bình luận: " + TEXT_NAMES[textIdx]
    }

    private fun showMsg(t: String, ms: Long) {
        msg.text = t
        msg.visibility = View.VISIBLE
        handler.removeCallbacks(hideMsgRun)
        if (ms > 0) handler.postDelayed(hideMsgRun, ms)
    }

    private fun showBar() {
        barVisible = true
        updateButtons()
        barHolder.visibility = View.VISIBLE
        btnCmt.requestFocus()
        resetBarTimer()
    }

    private fun hideBar() {
        barVisible = false
        barHolder.visibility = View.GONE
        handler.removeCallbacks(hideBarRun)
    }

    private fun resetBarTimer() {
        handler.removeCallbacks(hideBarRun)
        handler.postDelayed(hideBarRun, 8000)
    }

    /** Cỡ chữ mặc định ≈ 1,9% chiều cao màn hình (giống ảnh minh họa), nhân theo mức Nhỏ/Vừa/To/Rất to. */
    private fun applyTextSize() {
        val dm = resources.displayMetrics
        val target = minOf(dm.heightPixels, dm.widthPixels) * 0.019f * TEXT_MULT[textIdx]
        web.settings.textZoom = (100f * target / (14f * dm.density)).toInt().coerceIn(50, 300)
    }

    private fun goImmersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    // ================= Bình luận =================
    private fun setComments(on: Boolean) {
        showComments = on
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putBoolean("comments", on).apply()
        chatBox.visibility = if (on) View.VISIBLE else View.GONE
        if (on) {
            if (!(web.url ?: "").startsWith("http")) web.loadUrl(chatUrl)
        } else if (streamFound) {
            web.loadUrl("about:blank") // giải phóng RAM khi tắt bình luận
        }
        updateButtons()
        showMsg(if (on) "Bình luận: BẬT" else "Bình luận: TẮT", 1500)
    }

    // ================= Lấy luồng + phát =================
    private fun rankLabel(r: Int): String {
        val p = measuredP[r]
        if (p != null) return "${p}p" + if (measuredFps60[r] == true) "60" else ""
        return when (r) {
            1 -> "360p"; 2 -> "480p"; 3 -> "720p"; 4 -> "1080p"; 5 -> "Gốc"; else -> "Tự động"
        }
    }

    private fun startFpsProbe() {
        val c = player.videoDecoderCounters ?: return
        c.ensureUpdated()
        fpsC0 = c.renderedOutputBufferCount
        fpsT0 = SystemClock.elapsedRealtime()
        fpsRank = curRank
        handler.removeCallbacks(fpsRun)
        handler.postDelayed(fpsRun, 6000)
    }

    private fun handleExtract(res: String?) {
        if (streamFound || res == null || res == "null") return
        try {
            val v = JSONTokener(res).nextValue()
            val arr = JSONArray(if (v is String) v else v.toString())
            val list = ArrayList<StreamInfo>()
            for (i in 0 until arr.length()) {
                val u = arr.getString(i)
                val rank = when (RANK_RE.find(u)?.groupValues?.get(1)?.lowercase()) {
                    "ld" -> 1; "sd" -> 2; "hd" -> 3; "uhd" -> 4; "or" -> 5; else -> 0
                }
                list.add(StreamInfo(u, rank, u.substringBefore('?').endsWith(".flv")))
            }
            if (list.isEmpty()) return
            streams = list
            ranks = list.map { it.rank }.distinct().sorted()
            streamFound = true
            playRank(ranks.lastOrNull { it <= 3 } ?: ranks.first())
            if (!showComments) web.loadUrl("about:blank")
        } catch (e: Exception) {
            // thử lại ở lần poll sau
        }
    }

    private fun playRank(r: Int) {
        curRank = r
        attempt = 0
        tryPlay()
    }

    private fun tryPlay() {
        val c = streams.filter { it.rank == curRank }.sortedByDescending { it.flv }
        if (attempt >= c.size) {
            showMsg("Không phát được ở mức này. Thử đổi độ phân giải hoặc Tải lại", 0)
            return
        }
        player.setMediaItem(MediaItem.fromUri(c[attempt].url))
        player.prepare()
        player.playWhenReady = true
        updateButtons()
    }

    private fun changeQuality(dir: Int, wrap: Boolean) {
        if (ranks.size < 2) {
            showMsg(
                when {
                    ranks.isEmpty() -> "Chưa có luồng video"
                    isLoggedIn() -> "Kênh này chỉ có 1 mức độ phân giải"
                    else -> "Chỉ có 1 mức. Đăng nhập TikTok để có thêm độ phân giải cao"
                }, 3000
            )
            return
        }
        var i = ranks.indexOf(curRank) + dir
        if (i < 0 || i >= ranks.size) {
            if (wrap) i = (i + ranks.size) % ranks.size
            else {
                showMsg(if (dir > 0) "Đã ở mức cao nhất" else "Đã ở mức thấp nhất", 2000)
                return
            }
        }
        playRank(ranks[i])
        showMsg("Độ phân giải: " + rankLabel(curRank), 2500)
    }

    private fun isLoggedIn(): Boolean = try {
        (CookieManager.getInstance().getCookie("https://www.tiktok.com") ?: "").contains("sessionid=")
    } catch (e: Exception) { false }

    private fun reload() {
        streamFound = false
        pollCount = 0
        player.stop()
        showMsg("Đang tải lại…", 0)
        web.loadUrl(chatUrl)
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, 3000)
    }

    // ================= Phím điều khiển =================
    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        if (barVisible) {
            resetBarTimer()
            when (code) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { hideBar(); return true }
            }
            return super.onKeyDown(code, e) // Trái/Phải/OK: để hệ thống di chuyển/bấm nút
        }
        if (e.repeatCount > 0) return true
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_PROG_YELLOW -> setComments(!showComments)
            KeyEvent.KEYCODE_DPAD_UP -> changeQuality(+1, false)
            KeyEvent.KEYCODE_DPAD_DOWN -> changeQuality(-1, false)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_MENU -> showBar()
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.playWhenReady = !player.playWhenReady; updateButtons() }
            KeyEvent.KEYCODE_BACK -> finish()
            else -> return super.onKeyDown(code, e)
        }
        return true
    }

    override fun onStop() {
        super.onStop()
        player.playWhenReady = false
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.release()
        web.destroy()
        super.onDestroy()
    }
}
