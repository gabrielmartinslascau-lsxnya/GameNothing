package app.gamenative.service.xbox.cloud

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.gamenative.service.xbox.StoreAccounts
import app.gamenative.service.xbox.XboxCatalog
import app.gamenative.service.xbox.XboxLibraryCache
import app.gamenative.service.xbox.XboxStoreId
import app.gamenative.service.xbox.Xodus
import app.gamenative.ui.theme.PluviaTheme
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.Executors

class XboxCloudPlayerActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private var web: WebView? = null
    private var notice: LinearLayout? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val signalling = Executors.newCachedThreadPool()
    @Volatile private var session: XboxCloudSession.Session? = null
    @Volatile private var sessionPath: String? = null
    @Volatile private var titleId: String? = null
    @Volatile private var closed = false
    @Volatile private var webUrl: String? = null
    @Volatile private var settings = XboxCloudSettings()
    @Volatile private var sessionSignals: XboxCloudSignalling? = null
    @Volatile private var startedWith: XboxCloudSettings? = null
    private var menu: ComposeView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideBars()
        watchBars()
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        val requested = intent.getStringExtra("product").orEmpty()
        showNotice("Signing in to Xbox Cloud Gaming…", withButtons = false)
        worker.execute {
            val result = runCatching {
                val account = StoreAccounts.signedInAs(applicationContext, XboxStoreId.XBOX) ?: error("Sign in to the Xbox store first.")
                val cached = XboxLibraryCache.read(applicationContext, account, Xodus.market())?.games?.firstOrNull { it.id == requested }
                val product = cached?.extra?.get("cloudId")?.takeIf { it.isNotEmpty() } ?: requested
                webUrl = XboxCatalog.cloudUrl(requested, cached?.title ?: intent.getStringExtra("title").orEmpty(), product)
                val active = XboxCloudSession.get(applicationContext, account)
                val title = XboxCloudSession.titles(active)[product] ?: error("This game can't be streamed with this account here.")
                settings = XboxCloudSettings.load(applicationContext)
                startedWith = settings
                session = active.forStream(settings.region, settings.osName)
                titleId = title
                diag("cloud player: $product -> title $title, region ${java.net.URI(session!!.baseUri).host}, transfer=${active.userToken != null}")
            }
            runOnUiThread {
                if (closed) return@runOnUiThread
                result.onSuccess { openPlayer() }.onFailure {
                    diag("cloud player not started: ${it.javaClass.simpleName}: ${it.message?.take(160)}")
                    showNotice(it.message ?: "Xbox Cloud Gaming could not start.", withButtons = true)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openPlayer() {
        val view = WebView(this)
        web = view
        view.settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
        }
        view.setBackgroundColor(Color.BLACK)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.addJavascriptInterface(Bridge(), "DDCloud")
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                if (url.scheme == "https" && url.host == "appassets.androidplatform.net") {
                    val path = url.path?.removePrefix("/assets/") ?: return null
                    val mime = when {
                        path.endsWith(".html") -> "text/html"
                        path.endsWith(".js") -> "application/javascript"
                        path.endsWith(".css") -> "text/css"
                        else -> "application/octet-stream"
                    }
                    return runCatching {
                        WebResourceResponse(mime, "UTF-8", assets.open(path))
                    }.getOrNull()
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
        }
        view.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                Timber.tag("XboxCloudPlayer").d("console %s: %s", message.messageLevel(), message.message().take(300))
                return true
            }
        }
        root.addView(view, 0, FrameLayout.LayoutParams(-1, -1))
        notice?.let { root.removeView(it) }
        notice = null
        view.requestFocus()
        view.loadUrl("https://appassets.androidplatform.net/assets/xcloud/player.html")
    }

    private inner class Bridge {
        @JavascriptInterface
        fun call(id: Int, op: String, arg: String) {
            signalling.execute {
                val reply = runCatching { handle(op, arg) }.getOrElse { XboxCloudSession.Reply(0, it.javaClass.simpleName) }
                if (reply.status !in 200..299) {
                    diag("cloud player $op: ${reply.status} ${reply.body.take(300)}")
                } else if (op != "state" && op != "keepalive") {
                    diag("cloud player $op: ${reply.status}")
                }
                runOnUiThread { web?.evaluateJavascript("window.__ddResolve($id, ${reply.status}, ${JSONObject.quote(reply.body)})", null) }
            }
        }

        @JavascriptInterface
        fun status(text: String) {
            Timber.tag("XboxCloudPlayer").i("status: %s", text.take(120))
        }

        @JavascriptInterface
        fun playing() {
            diag("cloud player streaming")
            runOnUiThread { web?.requestFocus() }
        }

        @JavascriptInterface
        fun settings(): String = pageSettings(this@XboxCloudPlayerActivity.settings)

        @JavascriptInterface
        fun log(text: String) {
            diag("cloud player page: ${text.take(240)}")
        }

        @JavascriptInterface
        fun failed(stage: String, detail: String) {
            diag("cloud player failed at ${stage.take(40)}: ${detail.take(300)}")
            runOnUiThread { if (!closed) showNotice("The stream could not start ($stage).", withButtons = true) }
        }
    }

    private fun handle(op: String, arg: String): XboxCloudSession.Reply {
        val active = session ?: error("no session")
        val signals = sessionSignals ?: XboxCloudSignalling(active, titleId ?: error("no title"), settings).also { sessionSignals = it }
        return signals.op(op, arg).also { sessionPath = signals.path }
    }

    private fun showNotice(text: String, withButtons: Boolean) {
        notice?.let { root.removeView(it) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(if (withButtons) Color.argb(230, 0, 0, 0) else Color.TRANSPARENT)
        }
        box.addView(
            TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
                this.text = text
                setPadding(32, 16, 32, 16)
            },
        )
        if (withButtons) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            (webUrl ?: intent.getStringExtra("webUrl"))?.let { url ->
                row.addView(
                    Button(this).apply {
                        this.text = "Open in Microsoft's player"
                        setOnClickListener {
                            startActivity(XboxCloudActivity.intent(this@XboxCloudPlayerActivity, url))
                            finish()
                        }
                    },
                )
            }
            row.addView(
                Button(this).apply {
                    this.text = "Close"
                    setOnClickListener { finish() }
                },
            )
            box.addView(row)
        }
        notice = box
        root.addView(box, FrameLayout.LayoutParams(-1, -1))
    }

    private val rehideBars = Runnable { if (window.decorView.hasWindowFocus()) hideBars() }

    private fun watchBars() {
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            if (insets.isVisible(WindowInsetsCompat.Type.navigationBars()) || insets.isVisible(WindowInsetsCompat.Type.statusBars())) {
                v.removeCallbacks(rehideBars)
                v.postDelayed(rehideBars, 1500)
            }
            ViewCompat.onApplyWindowInsets(v, insets)
        }
    }

    override fun onResume() {
        super.onResume()
        hideBars()
    }

    private fun hideBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    private var guideDown = false
    private var guideDownAt = 0L
    private var lastGuideUp = 0L
    private var pendingNexus: Runnable? = null
    private var swallowGuideUp = false

    private fun isGuideKey(event: KeyEvent): Boolean = event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE

    private fun guideKey(event: KeyEvent): Boolean {
        if (!isGuideKey(event)) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) {
                guideDown = true
                guideDownAt = event.eventTime
                val pending = pendingNexus
                if (pending != null && event.eventTime - lastGuideUp <= DOUBLE_PRESS_MS) {
                    root.removeCallbacks(pending)
                    pendingNexus = null
                    guideDown = false
                    swallowGuideUp = true
                    openMenu()
                }
            } else if (guideDown && event.eventTime - guideDownAt >= 600) {
                guideDown = false
                swallowGuideUp = true
                openMenu()
            }
        } else if (event.action == KeyEvent.ACTION_UP && guideDown) {
            guideDown = false
            lastGuideUp = event.eventTime
            val tap = Runnable {
                pendingNexus = null
                pressNexus(1)
                root.postDelayed({ pressNexus(0) }, 150)
            }
            pendingNexus = tap
            root.postDelayed(tap, DOUBLE_PRESS_MS)
        }
        return true
    }

    private fun pressNexus(value: Int) {
        web?.evaluateJavascript("window.__ddButton && window.__ddButton('Nexus', $value)", null)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (menu != null) {
            if (isGuideKey(event)) {
                if (event.action == KeyEvent.ACTION_UP) {
                    if (swallowGuideUp) swallowGuideUp = false else closeMenu()
                }
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) {
                    if (menuPage.isNotEmpty()) menuPage = "" else closeMenu()
                }
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        if (notice == null && guideKey(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (notice == null) openMenu() else finish()
            }
            return true
        }
        val handled = super.dispatchKeyEvent(event)
        val fromController = event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        return handled || (notice == null && web != null && fromController)
    }

    private var menuPage by mutableStateOf("")

    private fun openMenu() {
        if (menu != null || web == null) return
        web?.evaluateJavascript("window.__ddInput && window.__ddInput(false)", null)
        menuPage = ""
        val regions = session?.regionList?.map { it.name }.orEmpty()
        val view = ComposeView(this)
        view.setContent {
            PluviaTheme {
                var current by remember { mutableStateOf(settings) }
                val entries = remember { List(6) { FocusRequester() } }
                LaunchedEffect(menuPage) {
                    if (menuPage.isEmpty()) runCatching { entries[lastMainEntry.coerceIn(0, 5)].requestFocus() }
                }
                val cs = MaterialTheme.colorScheme
                Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxHeight()
                            .width(400.dp)
                            .background(cs.surface.copy(alpha = 0.95f))
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 12.dp),
                    ) {
                        Text(
                            intent.getStringExtra("title").orEmpty().ifEmpty { "Xbox Cloud Gaming" },
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        if (menuPage.isEmpty()) {
                            MenuEntry("Resume", "Back to the game", entries[0], 0) { closeMenu() }
                            MenuEntry("Xbox menu", "The game's Xbox menu (also: Guide once, or View + Menu)", entries[1], 1) {
                                closeMenu()
                                web?.evaluateJavascript("window.__ddNexus && window.__ddNexus()", null)
                            }
                            MenuEntry("Screen effects", "Sharpen, colour, deband, FXAA, CRT, NTSC, Toon, fps cap", entries[2], 2) { menuPage = "picture" }
                            MenuEntry("Stream quality", "Resolution, region, language, codec, bitrate (next stream)", entries[3], 3) { menuPage = "stream" }
                            MenuEntry("Performance overlay and sound", "Overlay level and layout, volume, controller rumble", entries[4], 4) { menuPage = "overlay" }
                            MenuEntry("Leave", "End the stream", entries[5], 5) { finish() }
                            Text(
                                "Guide twice opens this menu; Back or Guide closes it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        } else {
                            MenuEntry("‹  Back", null) { menuPage = "" }
                            if (menuPage == "stream") {
                                MenuEntry(
                                    "Restart the stream with these settings",
                                    "Ends this cloud session and starts a new one; the game restarts, unsaved progress is lost.",
                                ) { restartStream() }
                            }
                            val page = menuPage
                            XboxCloudSettingsPanel(
                                current,
                                regions,
                                live = true,
                                section = page,
                                restoreRow = lastRow[page] ?: 0,
                                onRowFocused = { lastRow[page] = it },
                                startedWith = startedWith,
                            ) { changed ->
                                current = changed
                                settings = changed
                                XboxCloudSettings.save(this@XboxCloudPlayerActivity, changed)
                                web?.evaluateJavascript("window.__ddApply && window.__ddApply(${JSONObject.quote(pageSettings(changed))})", null)
                            }
                        }
                    }
                }
            }
        }
        menu = view
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        view.requestFocus()
    }

    private fun pageSettings(s: XboxCloudSettings): String = JSONObject(s.toJson())
        .put("server", session?.let { active -> active.regionList.firstOrNull { it.baseUri == active.baseUri }?.name } ?: "")
        .toString()

    private var lastMainEntry = 0
    private val lastRow = mutableMapOf<String, Int>()

    @Composable
    private fun MenuEntry(
        title: String,
        note: String?,
        focus: FocusRequester? = null,
        index: Int = -1,
        onClick: () -> Unit,
    ) = XboxMenuEntry(title, note, focus, { if (index >= 0) lastMainEntry = index }, onClick)

    private fun restartStream() {
        val active = session
        val path = sessionPath
        sessionPath = null
        closeMenu()
        showNotice("Restarting the stream…", withButtons = false)
        Thread({
            if (active != null && path != null) {
                runCatching { XboxCloudSession.request(active, "DELETE", "/$path") }
                    .onSuccess { diag("cloud player restart: session closed ${it.status}") }
            }
            runOnUiThread {
                if (!isFinishing) {
                    startActivity(playerIntent(this, intent.getStringExtra("product").orEmpty(), intent.getStringExtra("title").orEmpty()))
                    finish()
                }
            }
        }, "xcloud-restart").start()
    }

    private fun closeMenu() {
        menu?.let { root.removeView(it) }
        menu = null
        web?.evaluateJavascript("window.__ddInput && window.__ddInput(true)", null)
        web?.requestFocus()
    }

    override fun onDestroy() {
        closed = true
        web?.let {
            it.evaluateJavascript("window.__ddStop && window.__ddStop()", null)
            root.removeView(it)
            it.destroy()
        }
        web = null
        val active = session
        val path = sessionPath
        if (active != null && path != null) {
            Thread({
                runCatching { XboxCloudSession.request(active, "DELETE", "/$path") }
                    .onSuccess { diag("cloud player session closed: ${it.status}") }
            }, "xcloud-stop").start()
        }
        worker.shutdownNow()
        signalling.shutdownNow()
        super.onDestroy()
    }

    private fun diag(text: String) = Timber.tag("XboxCloudPlayer").i("Xbox: %s", text)

    companion object {
        private const val DOUBLE_PRESS_MS = 350L

        fun intent(context: Context, productId: String, title: String): Intent =
            Intent(context, XboxCloudPlayerActivity::class.java)
                .putExtra("product", productId.uppercase())
                .putExtra("title", title)

        fun playerIntent(context: Context, productId: String, title: String): Intent =
            intent(context, productId, title)
    }
}
