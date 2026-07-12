package com.codex.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.io.File
import kotlin.io.walkTopDown
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CodexMainActivity"
    }

    private lateinit var webView: WebView
    private lateinit var loadingOverlay: View
    private lateinit var serverManager: CodexServerManager
    private val extensionManager: ExtensionManager by lazy { ExtensionManager(this) }
    private val paywallBypassScript: String by lazy {
        try {
            assets.open("paywall-bypass.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load paywall-bypass.js: ${e.message}")
            ""
        }
    }
    private lateinit var btnSettings: Button
    private lateinit var btnWebviewSettings: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        btnSettings = findViewById(R.id.btnSettings)
        btnWebviewSettings = findViewById(R.id.btnWebviewSettings)

        serverManager = CodexServerManager(this)

        // Settings button on loading screen — visible after server is ready
        btnSettings.setOnClickListener {
            openSettings()
        }

        // Settings button floating on WebView
        btnWebviewSettings.setOnClickListener {
            openSettings()
        }

        requestBatteryOptimizationExemption()
        startForegroundService()
        setupWebView()
        showMainScreen()
        startBackgroundSetup()
    }

    /**
     * Show the main AnyClaw UI immediately without waiting for any server.
     * The bundled web assets are loaded directly from the APK.
     */
    private fun showMainScreen() {
        runOnUiThread {
            showLoading(false)
            webView.visibility = View.VISIBLE
            btnWebviewSettings.visibility = View.VISIBLE
            webView.loadUrl("file:///android_asset/web/index.html")
        }
    }

    /**
     * Start the environment setup in the background. The UI is already
     * visible; this only prepares the full codex-web-local server so the
     * WebView can switch to it once it is ready.
     */
    private fun startBackgroundSetup() {
        Thread {
            try {
                runSetup()
            } catch (e: Exception) {
                Log.e(TAG, "Background setup failed", e)
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this,
                        "Background setup failed: ${e.message}",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }.start()
    }

    /**
     * Open the native Settings activity.
     */
    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    /**
     * Load the bundled fallback HTML page directly into the WebView when
     * the bundled web server cannot be started. This avoids relying on
     * raw-resource URLs which are not supported on all Android versions.
     */
    private fun loadFallbackPage() {
        try {
            val html = resources.openRawResource(R.raw.bundled_fallback).bufferedReader().use { it.readText() }
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load fallback page: ${e.message}")
            webView.loadData(
                "<html><body style='background:#020617;color:#e2e8f0;padding:24px;'>" +
                    "<h1>AnyClaw</h1><p>Open Settings to continue.</p></body></html>",
                "text/html",
                "UTF-8",
            )
        }
    }

    /**
     * Inject the paywall-bypass script into the WebView. This runs on every
     * page start and finish so dynamically loaded SPAs also get the bypass.
     */
    private fun injectPaywallBypass(view: WebView) {
        if (paywallBypassScript.isBlank()) return
        try {
            view.evaluateJavascript(paywallBypassScript, null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inject paywall bypass: ${e.message}")
        }
    }

    /**
     * Ensure the Freebuff extension is installed in the global OpenClaw
     * extensions directory. If it is bundled in the APK assets, extract it.
     */
    private fun ensureFreebuffExtension() {
        try {
            val paths = BootstrapInstaller.getPaths(this)
            val freebuffDir = File(paths.homeDir, ".openclaw/extensions/freebuff")
            val packageFile = File(freebuffDir, "package.json")
            if (packageFile.exists()) {
                Log.d(TAG, "Freebuff extension already installed")
                return
            }

            val assetList = assets.list("extensions/freebuff") ?: return
            if (assetList.isEmpty()) {
                Log.w(TAG, "Freebuff extension assets not found")
                return
            }

            freebuffDir.mkdirs()
            copyAssetFolder("extensions/freebuff", freebuffDir)
            if (File(freebuffDir, "package.json").exists()) {
                Log.i(TAG, "Freebuff extension installed to ${freebuffDir.absolutePath}")
            } else {
                Log.w(TAG, "Freebuff extension extraction did not produce package.json")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install Freebuff extension: ${e.message}")
        }
    }

    /**
     * Patch the extracted codex-web-local JS bundles to remove common
     * paywall/premium checks. This is a safety net in case the injected
     * bypass script is not enough.
     */
    private fun patchServerBundlePaywallChecks() {
        try {
            val paths = BootstrapInstaller.getPaths(this)
            val bundleDir = File(paths.prefixDir, "lib/node_modules/codex-web-local/dist")
            if (!bundleDir.isDirectory) {
                Log.d(TAG, "Server bundle directory not found, skipping paywall patch")
                return
            }

            bundleDir.walkTopDown()
                .filter { it.isFile && (it.name.endsWith(".js") || it.name.endsWith(".mjs")) }
                .forEach { file ->
                    try {
                        var content = file.readText()
                        val original = content

                        // Common minified/unminified paywall checks
                        content = content.replace(Regex("isPremium\\s*:\\s*!1"), "isPremium:!0")
                        content = content.replace(Regex("isPremium\\s*:\\s*false"), "isPremium:true")
                        content = content.replace(Regex("paywallActive\\s*:\\s*!0"), "paywallActive:!1")
                        content = content.replace(Regex("paywallActive\\s*:\\s*true"), "paywallActive:false")
                        content = content.replace(Regex("hasSubscription\\s*:\\s*!1"), "hasSubscription:!0")
                        content = content.replace(Regex("hasSubscription\\s*:\\s*false"), "hasSubscription:true")
                        content = content.replace(Regex("isHermesUnlocked\\s*:\\s*!1"), "isHermesUnlocked:!0")
                        content = content.replace(Regex("isHermesUnlocked\\s*:\\s*false"), "isHermesUnlocked:true")

                        if (content != original) {
                            file.writeText(content)
                            Log.d(TAG, "Patched paywall checks in ${file.absolutePath}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to patch ${file.name}: ${e.message}")
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to patch server bundle paywall checks: ${e.message}")
        }
    }

    /**
     * Recursively copy an asset directory to a target File.
     */
    private fun copyAssetFolder(assetPath: String, targetDir: File) {
        val list = assets.list(assetPath) ?: return
        targetDir.mkdirs()
        for (entry in list) {
            val subAsset = "$assetPath/$entry"
            val subTarget = File(targetDir, entry)
            val subList = assets.list(subAsset)
            if (subList != null && subList.isNotEmpty()) {
                copyAssetFolder(subAsset, subTarget)
            } else {
                assets.open(subAsset).use { input ->
                    subTarget.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle OAuth redirect from codex login
        val data = intent.data
        if (data != null && data.scheme == "codex" && data.host == "auth") {
            Log.d(TAG, "OAuth redirect received: $data")
            // The codex login process should detect this via the local server
            // For now, just log it and bring the app to foreground
            updateStatus("Login redirect received, completing authentication...")
        }
        setIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        serverManager.stopServer()
        stopService(Intent(this, CodexForegroundService::class.java))
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) return

        try {
            @Suppress("BatteryLife")
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not request battery optimization exemption: ${e.message}")
        }
    }

    private fun startForegroundService() {
        val intent = Intent(this, CodexForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            setSupportZoom(false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                url: String,
            ): Boolean = false

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                injectPaywallBypass(view)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                injectPaywallBypass(view)
            }
        }

        webView.addJavascriptInterface(CodebuffBridge(), "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                Log.d(TAG, "[WebView] ${msg.sourceId()}:${msg.lineNumber()} ${msg.message()}")
                return true
            }
        }
    }

    private fun runSetup() {
        // All setup is non-blocking. The main UI is already visible and loaded
        // directly from APK assets. We only prepare the full server in the
        // background so advanced features can switch to it once it is ready.

        // Step 1: Extract bootstrap
        if (!BootstrapInstaller.isBootstrapInstalled(this)) {
            BootstrapInstaller.install(this) { msg -> Log.d(TAG, "[bootstrap] $msg") }
        }

        // Step 1b: Install proot (needed for dpkg/apt-get path remapping)
        if (!serverManager.isProotInstalled()) {
            val prootOk = serverManager.installProot { msg -> Log.d(TAG, "[proot] $msg") }
            if (!prootOk) {
                Log.w(TAG, "proot install failed — continuing without package management")
            }
        }

        // Step 2: Install Node.js
        if (!serverManager.isNodeInstalled()) {
            val nodeOk = serverManager.installNode { msg -> Log.d(TAG, "[node] $msg") }
            if (!nodeOk) {
                Log.w(TAG, "Node.js install failed — continuing without full server")
                return
            }
        }

        // Step 2b: Install Python
        if (!serverManager.isPythonInstalled()) {
            serverManager.installPython { msg -> Log.d(TAG, "[python] $msg") }
        }

        // Step 2c: Install bionic-compat.js (Android platform shim for Node.js)
        serverManager.ensureBionicCompat()

        // Step 2d: Install OpenClaw
        if (!serverManager.isOpenClawInstalled()) {
            serverManager.installOpenClawDeps { msg -> Log.d(TAG, "[deps] $msg") }
            serverManager.installOpenClaw { msg -> Log.d(TAG, "[openclaw] $msg") }
        }

        // Step 3: Install Codex CLI
        if (!serverManager.isCodexInstalled()) {
            val codexOk = serverManager.installCodex { msg -> Log.d(TAG, "[codex] $msg") }
            if (!codexOk) {
                Log.w(TAG, "Codex CLI install failed — continuing without full server")
                return
            }
        }

        // Ensure codex wrapper script exists
        serverManager.ensureCodexWrapperScript()

        // Step 3a: Extract web UI from APK assets
        serverManager.installServerBundle { msg -> Log.d(TAG, "[server-bundle] $msg") }
        patchServerBundlePaywallChecks()

        // Step 3b: Install native platform binary
        if (!serverManager.isPlatformBinaryInstalled()) {
            serverManager.installPlatformBinary { msg -> Log.d(TAG, "[platform-binary] $msg") }
        }

        // Step 3c: Write full-access config and create default workspace
        serverManager.ensureFullAccessConfig()
        serverManager.ensureDefaultWorkspace()

        // Step 4: Start CONNECT proxy (needed for native binary DNS/TLS)
        if (!serverManager.startProxy()) {
            Log.w(TAG, "Failed to start network proxy — continuing without it")
        }

        // Step 5: Authentication (optional - user can sign in or skip)
        if (!serverManager.isLoggedIn()) {
            val shouldSignIn = promptSignInOrSkip()
            if (shouldSignIn) {
                val authOk = loginWithTimeout(timeoutMs = 120_000)
                if (!authOk && !serverManager.isLoggedIn()) {
                    val apiKey = requestApiKey()
                    if (apiKey.isNotBlank()) {
                        serverManager.loginWithApiKey(apiKey)
                    }
                }
            }
        }

        // Step 6: Health check (skip if no credentials)
        if (serverManager.isLoggedIn()) {
            serverManager.healthCheck { msg -> Log.d(TAG, "[health] $msg") }
        }

        // Step 6b: Ensure Freebuff extension is installed
        ensureFreebuffExtension()

        // Step 7: Configure and start OpenClaw (only if explicitly enabled)
        val prefs = getSharedPreferences("AnyClawPrefs", Context.MODE_PRIVATE)
        val enableOpenClaw = prefs.getBoolean("enable_openclaw", false)

        if (serverManager.isOpenClawInstalled()) {
            val extensions = extensionManager.loadExtensions()
            Log.d(TAG, "Found ${extensions.size} extension(s)")

            if (enableOpenClaw && serverManager.isLoggedIn()) {
                serverManager.configureOpenClawAuth()
                serverManager.startOpenClawGateway()
                serverManager.startOpenClawControlUiServer()
            }
        }

        // Step 8: Start the full codex-web-local server in the background.
        // The bundled UI loaded from assets remains usable regardless.
        if (serverManager.isCodexInstalled()) {
            val fullStarted = serverManager.startServer(CodexServerManager.FULL_SERVER_PORT)
            if (fullStarted) {
                val fullReady = serverManager.waitForServer(
                    port = CodexServerManager.FULL_SERVER_PORT,
                    timeoutMs = 60_000,
                )
                if (fullReady) {
                    runOnUiThread {
                        // Only switch to the full server if the user is still on the
                        // bundled local UI. This avoids interrupting an active session.
                        val currentUrl = webView.url
                        if (currentUrl == null || currentUrl.startsWith("file:///android_asset/")) {
                            Log.i(TAG, "Full codex-web-local server ready; switching WebView")
                            webView.loadUrl("http://127.0.0.1:${CodexServerManager.FULL_SERVER_PORT}/")
                        } else {
                            Log.i(TAG, "Full codex-web-local server ready; user already left local UI, not switching")
                        }
                    }
                } else {
                    Log.w(TAG, "Full codex-web-local server did not become ready; keeping bundled UI")
                }
            } else {
                Log.w(TAG, "Full codex-web-local server failed to start; keeping bundled UI")
            }
        }
    }

    /**
     * Run the OAuth login flow with a timeout so the setup thread cannot
     * hang indefinitely if the user never completes the browser login.
     * The background login thread is interrupted on timeout to avoid leaks.
     */
    private fun loginWithTimeout(timeoutMs: Long): Boolean {
        val future = java.util.concurrent.CompletableFuture<Boolean>()
        val loginThread = Thread {
            try {
                val ok = serverManager.loginWithUrl(
                    onLoginUrl = { url ->
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    },
                    onProgress = { msg -> updateDetail(msg) },
                )
                future.complete(ok)
            } catch (e: Exception) {
                Log.e(TAG, "loginWithUrl error: ${e.message}")
                future.complete(false)
            }
        }
        loginThread.start()

        return try {
            future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            Log.w(TAG, "Login timed out after ${timeoutMs}ms")
            loginThread.interrupt()
            false
        } catch (e: Exception) {
            Log.w(TAG, "Login failed: ${e.message}")
            loginThread.interrupt()
            false
        }
    }

    /**
     * Show a dialog asking the user to sign in or skip authentication.
     * Returns true if the user chose to sign in, false if they chose to skip.
     * Uses a CountDownLatch for safer cross-thread signalling.
     */
    private fun promptSignInOrSkip(): Boolean {
        val latch = java.util.concurrent.CountDownLatch(1)
        val result = java.util.concurrent.atomic.AtomicBoolean(false)

        runOnUiThread {
            if (isFinishing || isDestroyed) {
                latch.countDown()
                return@runOnUiThread
            }
            AlertDialog.Builder(this)
                .setTitle("Sign in to AnyClaw")
                .setMessage("Sign in with your OpenAI account to use Codex, or skip and configure API keys later in Settings.")
                .setCancelable(false)
                .setPositiveButton("Sign In") { _, _ ->
                    result.set(true)
                    latch.countDown()
                }
                .setNegativeButton("Skip") { _, _ ->
                    result.set(false)
                    latch.countDown()
                }
                .setOnDismissListener { latch.countDown() }
                .show()
        }

        try {
            latch.await(5, java.util.concurrent.TimeUnit.MINUTES)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return result.get()
    }

    /**
     * Fallback: prompt for API key if browser login fails.
     */
    private fun requestApiKey(): String {
        var result = ""
        val lock = Object()

        runOnUiThread {
            val input = EditText(this).apply {
                hint = getString(R.string.api_key_hint)
                setSingleLine(true)
            }
            val padding = (24 * resources.displayMetrics.density).toInt()
            val container = android.widget.FrameLayout(this).apply {
                setPadding(padding, padding / 2, padding, 0)
                addView(input)
            }

            AlertDialog.Builder(this)
                .setTitle(R.string.api_key_title)
                .setMessage(R.string.api_key_message)
                .setView(container)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ ->
                    result = input.text.toString().trim()
                    synchronized(lock) { lock.notifyAll() }
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    synchronized(lock) { lock.notifyAll() }
                }
                .show()
        }

        synchronized(lock) {
            lock.wait(300_000)
        }
        return result
    }

    // ── UI helpers ──────────────────────────────────────────────────────────

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(R.string.retry) { _, _ ->
                startSetupFlow()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    /**
     * Bridge interface for Codebuff/Freebuff AI agents communication between WebView and Android.
     * All paywall/subscription/rate-limit restrictions are removed.
     */
    inner class CodebuffBridge {
        @android.webkit.JavascriptInterface
        fun sendCodeTask(agentId: String, task: String) {
            Log.d(TAG, "Freebuff task received: agent=$agentId, task=${task.take(100)}")
            try {
                val agentManager = CodebuffAgentManager(this@MainActivity)
                val result = agentManager.sendTask(agentId, task)
                runOnUiThread {
                    val msg = result ?: "Task completed by $agentId"
                    val js = "document.getElementById('codebuff-messages').innerHTML += '<div style=\"padding:8px;background:#45475a;border-radius:4px;margin:4px 0\"><strong>Freebuff:</strong> " + msg.replace("'", "\\'") + "</div>';"
                    webView.evaluateJavascript(js, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Freebuff task error: ${e.message}")
            }
        }

        @android.webkit.JavascriptInterface
        fun getAgentList(): String {
            return """[{"id":"editor","name":"Code Editor","icon":"✏️","isFree":true},{"id":"basher","name":"Terminal","icon":"⚡","isFree":true},{"id":"file_explorer","name":"File Explorer","icon":"📁","isFree":true},{"id":"reviewer","name":"Code Reviewer","icon":"🔍","isFree":true},{"id":"researcher","name":"Researcher","icon":"🔬","isFree":true}]"""
        }

        @android.webkit.JavascriptInterface
        fun isHermesUnlocked(): Boolean {
            return true
        }

        @android.webkit.JavascriptInterface
        fun getFreebuffStatus(): String {
            return """{"unlocked":true,"hermesUnlocked":true,"paywallBypassed":true,"subscriptionRequired":false,"credits":"unlimited","sessionLimit":"unlimited","models":["openai/gpt-5-nano","openai/gpt-5-mini","anthropic/claude-sonnet-4"],"mode":"FREE","features":{"code_generation":true,"terminal_access":true,"file_explorer":true,"code_review":true,"research":true,"hermes_web_ui":true}}"""
        }

        @android.webkit.JavascriptInterface
        fun isPaywallActive(): Boolean {
            return false
        }

        @android.webkit.JavascriptInterface
        fun getAvailableModels(): String {
            return """[{"id":"openai/gpt-5-nano","name":"GPT-5 Nano","provider":"OpenAI","isFree":true,"isPremium":false},{"id":"openai/gpt-5-mini","name":"GPT-5 Mini","provider":"OpenAI","isFree":true,"isPremium":false},{"id":"anthropic/claude-sonnet-4","name":"Claude Sonnet 4","provider":"Anthropic","isFree":true,"isPremium":false}]"""
        }

        @android.webkit.JavascriptInterface
        fun getUnlockMessage(): String {
            return "Freebuff is fully unlocked. No paywall, no subscription, no rate limits."
        }

        @android.webkit.JavascriptInterface
        fun saveProviderKey(providerId: String, apiKey: String): Boolean {
            return try {
                val paths = BootstrapInstaller.getPaths(this@MainActivity)
                val configDir = File(paths.homeDir, ".codex")
                configDir.mkdirs()
                
                when (providerId) {
                    "openai", "codex" -> {
                        val authFile = File(configDir, "auth.json")
                        authFile.writeText("""{"token":"$apiKey","type":"api_key"}""")
                    }
                    "openrouter" -> {
                        val authFile = File(configDir, "auth.json")
                        authFile.writeText("""{"token":"$apiKey","type":"api_key","provider":"openrouter"}""")
                    }
                    else -> {}
                }
                Log.i(TAG, "Saved API key for $providerId")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save provider key: ${e.message}")
                false
            }
        }

        @android.webkit.JavascriptInterface
        fun getConfiguredProviders(): String {
            val providers = mutableListOf<Map<String, Any>>()
            for (provider in listOf("openai", "openrouter", "opencode")) {
                val hasKey = try {
                    val paths = BootstrapInstaller.getPaths(this@MainActivity)
                    val authFile = File(paths.homeDir, ".codex/auth.json")
                    authFile.exists()
                } catch (_: Exception) { false }
                if (hasKey) providers.add(mapOf("id" to provider, "connected" to true))
            }
            return providers.toString()
        }

        @android.webkit.JavascriptInterface
        fun getLoadedExtensions(): String {
            return try {
                extensionManager.loadExtensions()
                extensionManager.getExtensionsJson()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get extensions: ${e.message}")
                "[]"
            }
        }

        @android.webkit.JavascriptInterface
        fun loadExtensions(): String {
            return try {
                val extensions = extensionManager.loadExtensions()
                "{\"count\":${extensions.size},\"ok\":true}"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load extensions: ${e.message}")
                "{\"count\":0,\"ok\":false}"
            }
        }

        @android.webkit.JavascriptInterface
        fun bypassPaywall(): Boolean {
            return true
        }

        @android.webkit.JavascriptInterface
        fun isSubscriptionRequired(): Boolean {
            return false
        }

        @android.webkit.JavascriptInterface
        fun getHermesStatus(): String {
            return """{"unlocked":true,"paywallBypassed":true,"message":"Hermes Web UI is unlocked"}"""
        }
    }
}