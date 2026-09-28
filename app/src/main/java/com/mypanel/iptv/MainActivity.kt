package com.mypanel.iptv

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var playerBox: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var overlay: View
    private lateinit var nowPlaying: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnPlay: ImageButton
    private lateinit var listPanel: LinearLayout
    private lateinit var recycler: RecyclerView
    private lateinit var search: EditText
    private lateinit var groupSpinner: Spinner
    private lateinit var status: TextView

    private val adapter = ChannelAdapter { pos -> play(pos, false) }
    private var player: ExoPlayer? = null

    private var all: List<Channel> = emptyList()
    private var shown: List<Channel> = emptyList()
    private var groups: List<String> = emptyList()
    private var selectedGroup: String? = null
    private var playingId = -1
    private var currentIndex = -1
    private var fullscreen = false

    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlay = Runnable { if (fullscreen) overlay.visibility = View.GONE }

    private val prefs by lazy { getSharedPreferences("cfg", Context.MODE_PRIVATE) }
    private val cacheFile by lazy { File(filesDir, "channels.json") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        root = findViewById(R.id.root)
        playerBox = findViewById(R.id.playerBox)
        playerView = findViewById(R.id.playerView)
        overlay = findViewById(R.id.overlay)
        nowPlaying = findViewById(R.id.nowPlaying)
        progress = findViewById(R.id.progress)
        btnPlay = findViewById(R.id.btnPlay)
        listPanel = findViewById(R.id.listPanel)
        recycler = findViewById(R.id.recycler)
        search = findViewById(R.id.search)
        groupSpinner = findViewById(R.id.groupSpinner)
        status = findViewById(R.id.status)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        groupSpinner.setPopupBackgroundResource(R.color.panel)

        initPlayer()
        initControls()
        applyLayout()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreen) {
                    setFullscreen(false)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        loadCache()
        refresh()
    }

    // ---------------- Player ----------------

    private fun initPlayer() {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()

        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlay.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
            }

            override fun onPlaybackStateChanged(state: Int) {
                progress.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            }

            override fun onPlayerError(error: PlaybackException) {
                progress.visibility = View.GONE
                Toast.makeText(this@MainActivity, "خطا در پخش این کانال", Toast.LENGTH_SHORT).show()
            }
        })
        playerView.player = p
        player = p
    }

    private fun initControls() {
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { step(-1); showOverlay() }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { step(1); showOverlay() }
        findViewById<ImageButton>(R.id.btnRew).setOnClickListener { seek(false); showOverlay() }
        findViewById<ImageButton>(R.id.btnFwd).setOnClickListener { seek(true); showOverlay() }
        btnPlay.setOnClickListener { togglePlay(); showOverlay() }
        findViewById<ImageButton>(R.id.btnFull).setOnClickListener { setFullscreen(!fullscreen) }
        findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener { refresh() }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener { showSettings(false) }

        playerView.setOnClickListener {
            if (overlay.visibility == View.VISIBLE && fullscreen) {
                overlay.visibility = View.GONE
            } else {
                showOverlay()
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilter() }
        })

        groupSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val g = if (pos == 0) null else groups.getOrNull(pos - 1)
                if (g != selectedGroup) {
                    selectedGroup = g
                    applyFilter()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun play(index: Int, scroll: Boolean) {
        val ch = shown.getOrNull(index) ?: return
        val p = player ?: return
        currentIndex = index
        playingId = ch.id
        nowPlaying.text = ch.name
        p.setMediaItem(MediaItem.fromUri(ch.url))
        p.prepare()
        p.playWhenReady = true
        adapter.select(index)
        if (scroll) recycler.scrollToPosition(index)
        showOverlay()
    }

    private fun step(dir: Int) {
        if (shown.isEmpty()) return
        val next = if (currentIndex < 0) 0 else (currentIndex + dir + shown.size) % shown.size
        play(next, true)
    }

    private fun seek(forward: Boolean) {
        val p = player ?: return
        if (!p.isCurrentMediaItemSeekable) {
            Toast.makeText(this, "این پخش زنده قابل جلو/عقب زدن نیست", Toast.LENGTH_SHORT).show()
            return
        }
        if (forward) p.seekForward() else p.seekBack()
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_IDLE && playingId != -1) {
            p.prepare()
            p.playWhenReady = true
        } else if (p.isPlaying || p.playWhenReady) {
            p.pause()
        } else {
            p.play()
        }
    }

    // ---------------- Layout / Fullscreen ----------------

    private fun applyLayout() {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        root.orientation = if (land) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        if (fullscreen) {
            listPanel.visibility = View.GONE
            playerBox.layoutParams = LinearLayout.LayoutParams(mp, mp)
        } else {
            listPanel.visibility = View.VISIBLE
            if (land) {
                playerBox.layoutParams = LinearLayout.LayoutParams(0, mp, 1.6f)
                listPanel.layoutParams = LinearLayout.LayoutParams(0, mp, 1f)
            } else {
                val w = resources.displayMetrics.widthPixels
                playerBox.layoutParams = LinearLayout.LayoutParams(mp, w * 9 / 16)
                listPanel.layoutParams = LinearLayout.LayoutParams(mp, 0, 1f)
            }
        }
    }

    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (on) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            c.show(WindowInsetsCompat.Type.systemBars())
        }
        applyLayout()
        showOverlay()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLayout()
    }

    private fun showOverlay() {
        overlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideOverlay)
        if (fullscreen) handler.postDelayed(hideOverlay, 4000)
    }

    // کنترل با ریموت تلویزیون و کلیدهای مدیا
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> { step(1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { step(-1); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlay(); return true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seek(true); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { seek(false); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (fullscreen && overlay.visibility != View.VISIBLE) {
                    showOverlay()
                    btnPlay.requestFocus()
                    return true
                }
                if (fullscreen) showOverlay()
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------------- Data ----------------

    private fun loadCache() {
        try {
            if (cacheFile.exists()) {
                setChannels(Repo.parse(cacheFile.readText()))
            }
        } catch (e: Exception) {
            // کش خراب را نادیده می‌گیریم
        }
    }

    private fun currentUrl(): String = prefs.getString("url", null) ?: BuildConfig.DEFAULT_PANEL_URL
    private fun currentKey(): String = prefs.getString("key", null) ?: BuildConfig.DEFAULT_API_KEY

    private fun refresh() {
        val url = currentUrl()
        val key = currentKey()
        if (url.isBlank() || key.isBlank()) {
            showSettings(true)
            return
        }
        status.text = "در حال دریافت لیست کانال‌ها..."
        lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    val body = Repo.download(url, key)
                    val parsed = Repo.parse(body)
                    cacheFile.writeText(body)
                    parsed
                }
                setChannels(list)
            } catch (e: Exception) {
                status.text = "خطا در دریافت: ${e.message ?: "نامشخص"}" +
                        if (all.isNotEmpty()) " (لیست ذخیره‌شده نمایش داده می‌شود)" else ""
            }
        }
    }

    private fun setChannels(list: List<Channel>) {
        all = list
        groups = list.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()
        val names = listOf("همه گروه‌ها") + groups
        val ad = ArrayAdapter(this, R.layout.spinner_item, names)
        ad.setDropDownViewResource(R.layout.spinner_item)
        selectedGroup = null
        groupSpinner.adapter = ad
        groupSpinner.setSelection(0, false)
        applyFilter()
    }

    private fun applyFilter() {
        val q = search.text.toString().trim().lowercase()
        val g = selectedGroup
        shown = all.filter { c ->
            (g == null || c.group == g) && (q.isEmpty() || c.name.lowercase().contains(q))
        }
        adapter.submit(shown)
        currentIndex = shown.indexOfFirst { it.id == playingId }
        if (currentIndex >= 0) adapter.select(currentIndex)
        status.text = "${shown.size} کانال از ${all.size}"
    }

    // ---------------- Settings ----------------

    private fun showSettings(firstRun: Boolean) {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val lay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val u = EditText(this).apply {
            hint = "آدرس پنل (مثال: https://site.com/iptv)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(currentUrl())
            setSingleLine()
        }
        val k = EditText(this).apply {
            hint = "کلید API"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(currentKey())
            setSingleLine()
        }
        lay.addView(u)
        lay.addView(k)
        AlertDialog.Builder(this)
            .setTitle("اتصال به پنل")
            .setView(lay)
            .setCancelable(!firstRun || all.isNotEmpty())
            .setPositiveButton("ذخیره") { _, _ ->
                prefs.edit()
                    .putString("url", u.text.toString().trim())
                    .putString("key", k.text.toString().trim())
                    .apply()
                refresh()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    // ---------------- Lifecycle ----------------

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
