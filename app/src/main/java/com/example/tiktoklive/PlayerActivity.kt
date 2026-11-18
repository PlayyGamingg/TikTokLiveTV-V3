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
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONTokener
import java.io.ByteArrayInputStream

// rank = mức hiển thị (1 SD, 2 HD, 3 FHD, 4 UHD); raw = mức gốc theo tên link (ld, sd, hd, uhd, or)
data class StreamInfo(val url: String, val rank: Int, val raw: Int, val flv: Boolean)

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

        // Biến trang TikTok thành lớp phủ bình luận: nền trong suốt, chữ trắng viền đen mảnh
        const val TWEAK_JS = """
        (function(){
          var H=window.innerHeight;
          var sh='-1px 0 #000,1px 0 #000,0 -1px #000,0 1px #000,-1px -1px #000,1px -1px #000,-1px 1px #000,1px 1px #000';
          var st=document.getElementById('__tv_css');
          if(!st){ st=document.createElement('style'); st.id='__tv_css'; document.head.appendChild(st); }
          st.textContent=
            'html,body{overflow:hidden !important;background:transparent !important}'+
            '*{color:#fff !important;text-shadow:'+sh+' !important;box-shadow:none !important;-webkit-mask-image:none !important;mask-image:none !important;backdrop-filter:none !important}'+
            '*::before,*::after{background:none !important;box-shadow:none !important;-webkit-mask-image:none !important;mask-image:none !important}'+
            '*::-webkit-scrollbar{display:none !important}'+
            'video,canvas{visibility:hidden !important}'+
            'input,textarea,[contenteditable="true"],[data-e2e*="chat-input"],[data-e2e*="comment-input"],[class*="InputContainer"],[class*="ChatFooter"]{display:none !important}'+
            '[class*="TopFan"],[class*="RankList"],[class*="Leaderboard"],[data-e2e*="top-user"]{display:none !important}'+
            '[class*="SideNav"],[class*="DivSideNav"],[data-e2e*="side-nav"],[class*="DivHeaderContainer"],[class*="HeaderContainer"],[id="app-header"]{display:none !important}';

          document.querySelectorAll('video').forEach(function(v){ try{ v.pause(); v.muted=true; v.removeAttribute('src'); v.load(); }catch(e){} });

          // Xử lý từng phần tử: bỏ nền gradient / nền màu của khung lớn (thanh trắng), ẩn ảnh bìa lớn
          function proc(e){
            if(!e||e.nodeType!==1||!e.getBoundingClientRect) return;
            var tg=e.tagName;
            if(tg==='SCRIPT'||tg==='STYLE'||tg==='svg'||tg==='path'||tg==='use'||tg==='g') return;
            var q=e.getBoundingClientRect();
            if(!q.width||!q.height) return;
            if(tg==='IMG'){ if(q.width>=200&&q.height>=120) e.style.setProperty('visibility','hidden','important'); return; }
            var cs=getComputedStyle(e);
            var bi=cs.backgroundImage;
            if(bi && bi!=='none' && bi.indexOf('gradient')>=0) e.style.setProperty('background-image','none','important');
            var m=cs.backgroundColor.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?/);
            if(m){
              var a=(m[4]===undefined)?1:parseFloat(m[4]);
              if(a>0.05 && q.width>=120 && q.height>=10) e.style.setProperty('background','transparent','important');
            }
          }
          // Theo dõi phần tử mới / đổi class (thanh trắng hay xuất hiện sau một lúc)
          if(!window.__tvObs){
            window.__tvQ=new Set();
            var sched=false;
            window.__tvObs=new MutationObserver(function(ms){
              ms.forEach(function(m){
                if(m.type==='childList'){
                  m.addedNodes.forEach(function(n){
                    if(n.nodeType===1){ window.__tvQ.add(n); if(n.querySelectorAll) n.querySelectorAll('*').forEach(function(c){ window.__tvQ.add(c); }); }
                  });
                } else if(m.target && m.target.nodeType===1){ window.__tvQ.add(m.target); }
              });
              if(!sched){
                sched=true;
                setTimeout(function(){ sched=false; var arr=Array.from(window.__tvQ); window.__tvQ.clear(); arr.forEach(proc); },300);
              }
            });
            window.__tvObs.observe(document.body,{childList:true,subtree:true,attributes:true,attributeFilter:['class']});
          }
          window.__tvN=(window.__tvN||0)+1;
          if(window.__tvN===1||window.__tvN%5===0){
            var all=document.body.querySelectorAll('*');
            for(var j=0;j<all.length;j++) proc(all[j]);
          }

          // Ẩn bảng xếp hạng / top người gửi theo nội dung chữ, giữ lại dòng "Người xem"
          var tops=document.body.querySelectorAll('div,section,span');
          for(var t=0;t<tops.length;t++){
            var te=tops[t];
            if(te.dataset.tvTop) continue;
            var txt=(te.innerText||'').trim();
            if(!txt || txt.length>400) continue;
            if(/Người xem/i.test(txt)) continue;
            if(/(Được gửi nhiều nhất|Bảng xếp hạng|Top người|Top fan|Xếp hạng)/i.test(txt)){
              var tr=te.getBoundingClientRect();
              if(tr.height>0 && tr.height<H*0.4){
                te.style.setProperty('display','none','important');
                te.dataset.tvTop='1';
              }
            }
          }

          // Tự dò và ẩn thanh bên trái
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
        val load = DefaultLoadControl.Builder().setBufferDurationsMs(3000, 10000, 1000, 2000).build()
        // Loại bỏ bộ giải mã Dolby Vision để TV không nhận tín hiệu DV từ app
        val noDolbySelector = MediaCodecSelector { mime, secure, tunnel ->
            MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunnel).filter { info ->
                val mt = info.mimeType ?: ""
                val nm = info.name.lowercase()
                !mt.contains("dolby", true) && !nm.contains("dolby") &&
                    !nm.contains("dvhe") && !nm.contains("dvh1") && !nm.contains("dovi")
            }
        }
        player = ExoPlayer.Builder(this,
            DefaultRenderersFactory(this).setEnableDecoderFallback(true).setMediaCodecSelector(noDolbySelector))
            .setLoadControl(load)
            .build()
        // Ưu tiên H.264 (thường là SDR) nếu luồng có nhiều bản mã hóa
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredVideoMimeType(MimeTypes.VIDEO_H264)
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
            override fun onTracksChanged(tracks: Tracks) {
                for (g in tracks.groups) {
                    if (g.type != C.TRACK_TYPE_VIDEO) continue
                    for (i in 0 until g.length) {
                        if (g.isTrackSelected(i) && isHdrFormat(g.getTrackFormat(i))) { skipHdr(); return }
                    }
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

        val chatW = (screenW * 0.35f).toInt()
        val chatH = (screenH * 0.58f).toInt()
        val cut = (screenH * 0.13f).toInt()   // phần dưới (ô nhập bình luận) bị cắt bỏ
        val shiftRight = 0  // không dời nữa: mở rộng khung để hiện trọn cột chat
        chatBox = FrameLayout(this).apply {
            clipChildren = true
            visibility = if (showComments) View.VISIBLE else View.GONE
        }
        chatBox.addView(web, FrameLayout.LayoutParams(screenW, chatH + cut).apply {
            leftMargin = -(screenW - chatW) + shiftRight
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

    // ================= Mạng: gửi kèm cookie đăng nhập TikTok =================
    private fun buildHttp(): DefaultHttpDataSource.Factory {
        val ck = try { CookieManager.getInstance().getCookie("https://www.tiktok.com") ?: "" } catch (e: Exception) { "" }
        val props = mutableMapOf("Referer" to "https://www.tiktok.com/")
        if (ck.isNotEmpty()) props["Cookie"] = ck
        return DefaultHttpDataSource.Factory()
            .setUserAgent(UA)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(props)
    }

    private fun isLoggedIn(): Boolean = try {
        (CookieManager.getInstance().getCookie("https://www.tiktok.com") ?: "").contains("sessionid=")
    } catch (e: Exception) { false }

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
    private fun rankLabel(r: Int): String = when (r) {
        1 -> "SD"; 2 -> "HD"; 3 -> "FHD"; 4 -> "UHD"; else -> "Tự động"
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
                val raw = when (RANK_RE.find(u)?.groupValues?.get(1)?.lowercase()) {
                    "ld" -> 1; "sd" -> 2; "hd" -> 3; "uhd" -> 4; "or" -> 5; else -> 0
                }
                val tier = when (raw) { 1, 2 -> 1; 3 -> 2; 4 -> 3; 5 -> 4; else -> 0 }
                list.add(StreamInfo(u, tier, raw, u.substringBefore('?').endsWith(".flv")))
            }
            if (list.isEmpty()) return
            streams = list
            ranks = list.map { it.rank }.distinct().sorted()
            streamFound = true
            // Mặc định: mức cao nhất đến FHD (bỏ UHD / bản gốc để đỡ nặng cho TV)
            playRank(ranks.filter { it in 1..3 }.lastOrNull() ?: ranks.first())
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

    private val hdrBlocked = HashSet<String>()   // link bị bỏ vì là HDR / Dolby Vision

    private fun candidates() = streams.filter { it.rank == curRank }
        .sortedWith(compareByDescending<StreamInfo> { it.raw }.thenByDescending { it.flv })

    private fun tryPlay() {
        val c = candidates()
        while (attempt < c.size && c[attempt].url in hdrBlocked) attempt++
        if (attempt >= c.size) {
            if (c.isNotEmpty() && c.all { it.url in hdrBlocked }) { skipHdrTier(); return }
            showMsg("Không phát được ở mức này. Thử đổi độ phân giải hoặc Tải lại", 0)
            return
        }
        val source = DefaultMediaSourceFactory(buildHttp()).createMediaSource(MediaItem.fromUri(c[attempt].url))
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
        updateButtons()
    }

    private fun isHdrFormat(f: Format): Boolean {
        val mime = f.sampleMimeType ?: ""
        val codecs = f.codecs ?: ""
        val t = f.colorInfo?.colorTransfer
        return mime == MimeTypes.VIDEO_DOLBY_VISION ||
            codecs.startsWith("dvh") || codecs.startsWith("dva") || codecs.startsWith("dav1") ||
            t == C.COLOR_TRANSFER_ST2084 || t == C.COLOR_TRANSFER_HLG
    }

    /** Luồng đang phát là HDR / Dolby Vision -> bỏ link này, thử link khác. */
    private fun skipHdr() {
        val c = candidates()
        if (attempt < c.size) hdrBlocked.add(c[attempt].url)
        attempt++
        tryPlay()
    }

    /** Mọi link của mức này đều là HDR -> bỏ mức này, chuyển xuống mức thấp hơn. */
    private fun skipHdrTier() {
        val old = curRank
        ranks = ranks.filter { it != old }
        val lower = ranks.filter { it in 1 until old }.maxOrNull() ?: ranks.firstOrNull()
        if (lower == null) {
            showMsg("Luồng này chỉ có bản HDR, không có bản SDR để phát", 0)
            return
        }
        showMsg("Mức " + rankLabel(old) + " là HDR nên đã bỏ qua, chuyển sang " + rankLabel(lower), 3500)
        playRank(lower)
    }

    private fun changeQuality(dir: Int, wrap: Boolean) {
        if (ranks.size < 2) {
            showMsg(if (ranks.isEmpty()) "Chưa có luồng video" else "Kênh này chỉ có 1 mức độ phân giải", 3000)
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

    private fun reload() {
        hdrBlocked.clear()
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
