package com.codex.mobile

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.io.File

/**
 * Native Kotlin Settings screen for AnyClaw.
 *
 * Features:
 * - Manage API keys for OpenAI, OpenRouter, OpenCode
 * - View and control server status (start/stop)
 * - Check environment installation status
 * - Reinstall the Linux environment
 * - View version and about info
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
    }

    private lateinit var serverManager: CodexServerManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRefreshing = false

    // OpenAI Key
    private lateinit var inputOpenAIKey: TextInputEditText
    private lateinit var statusOpenAI: TextView
    private lateinit var btnSaveOpenAI: Button
    private lateinit var btnClearOpenAI: Button

    // OpenRouter Key
    private lateinit var inputOpenRouterKey: TextInputEditText
    private lateinit var statusOpenRouter: TextView
    private lateinit var btnSaveOpenRouter: Button
    private lateinit var btnClearOpenRouter: Button

    // OpenCode Key
    private lateinit var inputOpenCodeKey: TextInputEditText
    private lateinit var statusOpenCode: TextView
    private lateinit var btnSaveOpenCode: Button
    private lateinit var btnClearOpenCode: Button

    // Server
    private lateinit var labelServerStatus: TextView
    private lateinit var serverStatusDot: TextView
    private lateinit var serverPortValue: TextView
    private lateinit var btnStartServer: Button
    private lateinit var btnStopServer: Button

    // Environment
    private lateinit var envBootstrapStatus: TextView
    private lateinit var envNodeStatus: TextView
    private lateinit var envPythonStatus: TextView
    private lateinit var envCodexStatus: TextView
    private lateinit var btnReinstallEnv: Button

    // OpenClaw toggle
    private lateinit var switchOpenClaw: SwitchMaterial
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Set dark status bar
        window.statusBarColor = 0xFF020617.toInt()

        serverManager = CodexServerManager(this)

        initViews()
        setupClickListeners()
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun initViews() {
        // OpenAI
        inputOpenAIKey = findViewById(R.id.inputOpenAIKey)
        statusOpenAI = findViewById(R.id.statusOpenAI)
        btnSaveOpenAI = findViewById(R.id.btnSaveOpenAI)
        btnClearOpenAI = findViewById(R.id.btnClearOpenAI)

        // OpenRouter
        inputOpenRouterKey = findViewById(R.id.inputOpenRouterKey)
        statusOpenRouter = findViewById(R.id.statusOpenRouter)
        btnSaveOpenRouter = findViewById(R.id.btnSaveOpenRouter)
        btnClearOpenRouter = findViewById(R.id.btnClearOpenRouter)

        // OpenCode
        inputOpenCodeKey = findViewById(R.id.inputOpenCodeKey)
        statusOpenCode = findViewById(R.id.statusOpenCode)
        btnSaveOpenCode = findViewById(R.id.btnSaveOpenCode)
        btnClearOpenCode = findViewById(R.id.btnClearOpenCode)

        // Server
        labelServerStatus = findViewById(R.id.labelServerStatus)
        serverStatusDot = findViewById(R.id.serverStatusDot)
        serverPortValue = findViewById(R.id.serverPortValue)
        btnStartServer = findViewById(R.id.btnStartServer)
        btnStopServer = findViewById(R.id.btnStopServer)

        // Environment
        envBootstrapStatus = findViewById(R.id.envBootstrapStatus)
        envNodeStatus = findViewById(R.id.envNodeStatus)
        envPythonStatus = findViewById(R.id.envPythonStatus)
        envCodexStatus = findViewById(R.id.envCodexStatus)
        btnReinstallEnv = findViewById(R.id.btnReinstallEnv)

        // OpenClaw toggle
        switchOpenClaw = findViewById(R.id.switchOpenClaw)
        prefs = getSharedPreferences("AnyClawPrefs", Context.MODE_PRIVATE)
    }

    private fun setupClickListeners() {
        // OpenAI
        btnSaveOpenAI.setOnClickListener { saveApiKey("openai") }
        btnClearOpenAI.setOnClickListener { clearApiKey("openai") }

        // OpenRouter
        btnSaveOpenRouter.setOnClickListener { saveApiKey("openrouter") }
        btnClearOpenRouter.setOnClickListener { clearApiKey("openrouter") }

        // OpenCode
        btnSaveOpenCode.setOnClickListener { saveApiKey("opencode") }
        btnClearOpenCode.setOnClickListener { clearApiKey("opencode") }

        // Server
        btnStartServer.setOnClickListener { startServer() }
        btnStopServer.setOnClickListener { stopServer() }

        // Environment
        btnReinstallEnv.setOnClickListener { confirmReinstall() }

        // OpenClaw toggle
        switchOpenClaw.isChecked = prefs.getBoolean("enable_openclaw", false)
        switchOpenClaw.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_openclaw", isChecked).apply()
            Toast.makeText(this, if (isChecked) "OpenClaw enabled — restart app to apply" else "OpenClaw disabled — restart app to apply", Toast.LENGTH_SHORT).show()
        }
    }

    // ── API Key Management ─────────────────────────────────────────────────

    /**
     * Save an API key for the given provider.
     * Writes to ~/.codex/auth.json matching the same format as CodebuffBridge.saveProviderKey().
     */
    private fun saveApiKey(providerId: String) {
        val key = when (providerId) {
            "openai" -> inputOpenAIKey.text.toString().trim()
            "openrouter" -> inputOpenRouterKey.text.toString().trim()
            "opencode" -> inputOpenCodeKey.text.toString().trim()
            else -> return
        }

        if (key.isEmpty()) {
            Toast.makeText(this, R.string.api_key_placeholder, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val paths = BootstrapInstaller.getPaths(this)
            val configDir = File(paths.homeDir, ".codex")
            configDir.mkdirs()

            when (providerId) {
                "openai", "codex" -> {
                    val authFile = File(configDir, "auth.json")
                    authFile.writeText("""{"token":"$key","type":"api_key"}""")
                }
                "openrouter" -> {
                    val authFile = File(configDir, "auth.json")
                    authFile.writeText("""{"token":"$key","type":"api_key","provider":"openrouter"}""")
                }
                "opencode" -> {
                    // OpenCode stores its key separately
                    val authFile = File(configDir, "opencode_auth.json")
                    authFile.writeText("""{"token":"$key","type":"api_key"}""")
                }
            }

            Log.i(TAG, "Saved API key for $providerId")
            Toast.makeText(this, R.string.toast_key_saved, Toast.LENGTH_SHORT).show()
            refreshProviderStatus(providerId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save provider key: ${e.message}")
            Toast.makeText(this, R.string.key_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Clear an API key for the given provider.
     */
    private fun clearApiKey(providerId: String) {
        try {
            val paths = BootstrapInstaller.getPaths(this)
            val configDir = File(paths.homeDir, ".codex")

            when (providerId) {
                "openai", "openrouter" -> {
                    val authFile = File(configDir, "auth.json")
                    if (authFile.exists()) authFile.delete()
                }
                "opencode" -> {
                    val authFile = File(configDir, "opencode_auth.json")
                    if (authFile.exists()) authFile.delete()
                }
            }

            when (providerId) {
                "openai" -> inputOpenAIKey.text?.clear()
                "openrouter" -> inputOpenRouterKey.text?.clear()
                "opencode" -> inputOpenCodeKey.text?.clear()
            }

            Log.i(TAG, "Cleared API key for $providerId")
            Toast.makeText(this, R.string.toast_key_cleared, Toast.LENGTH_SHORT).show()
            refreshProviderStatus(providerId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear provider key: ${e.message}")
        }
    }

    /**
     * Refresh the status display for a specific provider.
     * File I/O happens on the calling thread; UI updates are posted to the main thread.
     */
    private fun refreshProviderStatus(providerId: String) {
        val status = readProviderStatus(providerId)
        runOnUiThread { applyProviderStatus(providerId, status) }
    }

    // ── Server Management ──────────────────────────────────────────────────

    /**
     * Refresh the server status display.
     * Reads server state on the calling thread and posts UI updates to the main thread.
     */
    private fun refreshServerStatus() {
        val running = serverManager.isRunning
        val port = serverManager.runningServerPort
        runOnUiThread { applyServerStatus(running, port) }
    }

    /**
     * Start the codex-web-local server.
     */
    private fun startServer() {
        labelServerStatus.text = getString(R.string.server_starting)
        serverStatusDot.setBackgroundResource(R.drawable.circle_gray)
        btnStartServer.isEnabled = false

        Thread {
            try {
                // Ensure proxy is running
                if (!serverManager.startProxy()) {
                    mainHandler.post {
                        Toast.makeText(this, "Failed to start proxy", Toast.LENGTH_LONG).show()
                        refreshServerStatus()
                    }
                    return@Thread
                }

                // Start the server
                val started = serverManager.startServer()
                if (started) {
                    val ready = serverManager.waitForServer(timeoutMs = 60_000)
                    mainHandler.post {
                        if (ready) {
                            Toast.makeText(this, R.string.toast_server_started, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Server start timed out", Toast.LENGTH_LONG).show()
                        }
                        refreshServerStatus()
                    }
                } else {
                    mainHandler.post {
                        Toast.makeText(this, "Failed to start server", Toast.LENGTH_LONG).show()
                        refreshServerStatus()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting server: ${e.message}")
                mainHandler.post {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    refreshServerStatus()
                }
            }
        }.start()
    }

    /**
     * Stop the server.
     */
    private fun stopServer() {
        Thread {
            serverManager.stopServer()
            mainHandler.post {
                Toast.makeText(this, R.string.toast_server_stopped, Toast.LENGTH_SHORT).show()
                refreshServerStatus()
            }
        }.start()
    }

    // ── Environment Status ─────────────────────────────────────────────────

    /**
     * Refresh environment installation status.
     * Reads state on the calling thread and posts UI updates to the main thread.
     */
    private fun refreshEnvironmentStatus() {
        val status = readEnvironmentStatus()
        runOnUiThread { applyEnvironmentStatus(status) }
    }

    /**
     * Show a confirmation dialog before reinstalling the environment.
     */
    private fun confirmReinstall() {
        AlertDialog.Builder(this)
            .setTitle(R.string.env_reinstall)
            .setMessage(R.string.env_reinstall_confirm)
            .setPositiveButton(R.string.ok) { _, _ -> reinstallEnvironment() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Reinstall the Linux environment. This deletes the old bootstrap
     * and extracts it fresh from the APK assets.
     */
    private fun reinstallEnvironment() {
        btnReinstallEnv.isEnabled = false
        btnReinstallEnv.text = "Reinstalling…"

        Thread {
            try {
                val paths = BootstrapInstaller.getPaths(this)

                // Stop server first
                serverManager.stopServer()

                // Delete old bootstrap
                val prefixDir = File(paths.prefixDir)
                if (prefixDir.exists()) {
                    deleteRecursive(prefixDir)
                }

                // Delete home contents but keep .codex config
                val homeDir = File(paths.homeDir)
                if (homeDir.exists()) {
                    homeDir.listFiles()?.forEach { file ->
                        if (file.name != ".codex") {
                            deleteRecursive(file)
                        }
                    }
                }

                // Reinstall
                BootstrapInstaller.install(this) { msg ->
                    Log.d(TAG, "Reinstall progress: $msg")
                }

                mainHandler.post {
                    btnReinstallEnv.isEnabled = true
                    btnReinstallEnv.text = getString(R.string.env_reinstall)
                    Toast.makeText(this, "Environment reinstalled", Toast.LENGTH_LONG).show()
                    refreshEnvironmentStatus()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reinstall failed: ${e.message}")
                mainHandler.post {
                    btnReinstallEnv.isEnabled = true
                    btnReinstallEnv.text = getString(R.string.env_reinstall)
                    Toast.makeText(this, "Reinstall failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // ── Full Refresh ───────────────────────────────────────────────────────

    /**
     * Refresh all UI sections.
     * File I/O is performed on a background thread and the resulting
     * UI updates are posted back to the main thread to avoid
     * CalledFromWrongThreadException crashes.
     */
    private fun refreshAll() {
        if (isRefreshing) return
        isRefreshing = true

        Thread {
            val openaiStatus = readProviderStatus("openai")
            val openrouterStatus = readProviderStatus("openrouter")
            val opencodeStatus = readProviderStatus("opencode")
            val serverRunning = serverManager.isRunning
            val serverPort = serverManager.runningServerPort
            val envStatus = readEnvironmentStatus()

            mainHandler.post {
                applyProviderStatus("openai", openaiStatus)
                applyProviderStatus("openrouter", openrouterStatus)
                applyProviderStatus("opencode", opencodeStatus)
                applyServerStatus(serverRunning, serverPort)
                applyEnvironmentStatus(envStatus)
                isRefreshing = false
            }
        }.start()
    }

    private data class ProviderStatus(val hasKey: Boolean, val error: Boolean = false)

    private fun readProviderStatus(providerId: String): ProviderStatus {
        try {
            val paths = BootstrapInstaller.getPaths(this)
            val configDir = File(paths.homeDir, ".codex")

            val hasKey = when (providerId) {
                "openai" -> {
                    val authFile = File(configDir, "auth.json")
                    authFile.exists() && authFile.readText().contains("\"token\"")
                }
                "openrouter" -> {
                    val authFile = File(configDir, "auth.json")
                    authFile.exists() && authFile.readText().contains("\"openrouter\"")
                }
                "opencode" -> {
                    val authFile = File(configDir, "opencode_auth.json")
                    authFile.exists()
                }
                else -> false
            }
            return ProviderStatus(hasKey)
        } catch (e: Exception) {
            return ProviderStatus(false, true)
        }
    }

    private fun applyProviderStatus(providerId: String, status: ProviderStatus) {
        val (statusView, inputView) = when (providerId) {
            "openai" -> Pair(statusOpenAI, inputOpenAIKey)
            "openrouter" -> Pair(statusOpenRouter, inputOpenRouterKey)
            "opencode" -> Pair(statusOpenCode, inputOpenCodeKey)
            else -> return
        }

        if (status.hasKey) {
            statusView.text = getString(R.string.provider_connected)
            statusView.setTextColor(0xFF22c55e.toInt())
            inputView.setText("••••••••••••••••")
        } else {
            statusView.text = getString(R.string.key_not_set)
            statusView.setTextColor(0xFFf87171.toInt())
            inputView.text?.clear()
        }
    }

    private fun applyServerStatus(running: Boolean, port: Int?) {
        if (running) {
            labelServerStatus.text = getString(R.string.server_running)
            serverStatusDot.setBackgroundResource(R.drawable.circle_green)
            btnStartServer.isEnabled = false
            btnStopServer.isEnabled = true
        } else {
            labelServerStatus.text = getString(R.string.server_stopped)
            serverStatusDot.setBackgroundResource(R.drawable.circle_red)
            btnStartServer.isEnabled = true
            btnStopServer.isEnabled = false
        }
        serverPortValue.text = port?.toString() ?: "-"
    }

    private data class EnvironmentStatus(
        val bootstrapInstalled: Boolean,
        val nodeInstalled: Boolean,
        val pythonInstalled: Boolean,
        val codexInstalled: Boolean
    )

    private fun readEnvironmentStatus(): EnvironmentStatus {
        return try {
            val paths = BootstrapInstaller.getPaths(this)
            EnvironmentStatus(
                bootstrapInstalled = BootstrapInstaller.isBootstrapInstalled(this),
                nodeInstalled = serverManager.isNodeInstalled(),
                pythonInstalled = serverManager.isPythonInstalled(),
                codexInstalled = serverManager.isCodexInstalled()
            )
        } catch (e: Exception) {
            EnvironmentStatus(false, false, false, false)
        }
    }

    private fun applyEnvironmentStatus(status: EnvironmentStatus) {
        if (status.bootstrapInstalled) {
            envBootstrapStatus.text = getString(R.string.env_bootstrap_installed)
            envBootstrapStatus.setTextColor(0xFF22c55e.toInt())
        } else {
            envBootstrapStatus.text = getString(R.string.env_bootstrap_not_installed)
            envBootstrapStatus.setTextColor(0xFFf87171.toInt())
        }

        if (status.nodeInstalled) {
            envNodeStatus.text = getString(R.string.env_bootstrap_installed)
            envNodeStatus.setTextColor(0xFF22c55e.toInt())
        } else {
            envNodeStatus.text = getString(R.string.env_not_available)
            envNodeStatus.setTextColor(0xFF94a3b8.toInt())
        }

        if (status.pythonInstalled) {
            envPythonStatus.text = getString(R.string.env_bootstrap_installed)
            envPythonStatus.setTextColor(0xFF22c55e.toInt())
        } else {
            envPythonStatus.text = getString(R.string.env_not_available)
            envPythonStatus.setTextColor(0xFF94a3b8.toInt())
        }

        if (status.codexInstalled) {
            envCodexStatus.text = getString(R.string.env_bootstrap_installed)
            envCodexStatus.setTextColor(0xFF22c55e.toInt())
        } else {
            envCodexStatus.text = getString(R.string.env_not_available)
            envCodexStatus.setTextColor(0xFF94a3b8.toInt())
        }
    }

    // ── Utility ────────────────────────────────────────────────────────────

    private fun deleteRecursive(fileOrDir: File) {
        if (fileOrDir.isDirectory) {
            fileOrDir.listFiles()?.forEach { child -> deleteRecursive(child) }
        }
        fileOrDir.delete()
    }
}
