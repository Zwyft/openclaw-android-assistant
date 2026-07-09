package com.codex.mobile

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages the lifecycle of the Node.js codex-web-local server process running
 * inside the Termux bootstrap environment. Handles installation of Node.js,
 * Codex CLI, the platform-specific native binary, authentication via
 * `codex login`, and the codex-web-local web server.
 */
class CodexServerManager(private val context: Context) {

    companion object {
        private const val TAG = "CodexServerManager"
        const val SERVER_PORT = 18923
        private const val PROXY_PORT = 18924
        private const val CODEX_VERSION = "0.104.0"
        const val OPENCLAW_GATEWAY_PORT = 18789
        const val OPENCLAW_CONTROL_UI_PORT = 19001
    }

    private var serverProcess: Process? = null
    private var proxyProcess: Process? = null
    private var openClawGatewayProcess: Process? = null
    private var openClawControlUiProcess: Process? = null
    private var authCallbackServer: AuthCallbackServer? = null

    val isRunning: Boolean
        get() {
            val proc = serverProcess ?: return false
            return try {
                proc.exitValue()
                false
            } catch (_: IllegalThreadStateException) {
                true
            }
        }

    // ── Shell helpers ──────────────────────────────────────────────────────

    /**
     * Run a shell command inside the Termux prefix environment.
     * Returns the exit code.
     */
    fun runInPrefix(
        command: String,
        onOutput: ((String) -> Unit)? = null,
    ): Int {
        val paths = BootstrapInstaller.getPaths(context)
        val env = buildEnvironment(paths)

        val shell = "${paths.prefixDir}/bin/sh"
        val pb = ProcessBuilder(shell, "-c", command)
        pb.environment().clear()
        pb.environment().putAll(env)
        pb.directory(File(paths.homeDir))
        pb.redirectErrorStream(true)

        val proc = pb.start()
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        var line = reader.readLine()
        while (line != null) {
            Log.d(TAG, line)
            onOutput?.invoke(line)
            line = reader.readLine()
        }
        return proc.waitFor()
    }

    /**
     * Run a command and capture its stdout as a single trimmed string.
     */
    private fun runCapture(command: String): String {
        val sb = StringBuilder()
        runInPrefix(command) { sb.appendLine(it) }
        return sb.toString().trim()
    }

    // ── Install checks ─────────────────────────────────────────────────────

    fun isProotInstalled(): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        return File(paths.prefixDir, "bin/proot").exists()
    }

    fun isNodeInstalled(): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        return File(paths.prefixDir, "bin/node").exists()
    }

    fun isCodexInstalled(): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        return File(paths.prefixDir, "lib/node_modules/@openai/codex/bin/codex.js").exists()
    }

    fun isServerBundleInstalled(): Boolean = false

    /**
     * The native Rust binary that the JS launcher delegates to.
     * Required for `codex app-server`, `codex login`, `codex exec`, etc.
     */
    fun isPlatformBinaryInstalled(): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        return File(
            paths.prefixDir,
            "lib/node_modules/@openai/codex-linux-arm64/vendor/aarch64-unknown-linux-musl/codex/codex",
        ).exists()
    }

    // ── Installation ────────────────────────────────────────────────────────

    fun installNode(onProgress: (String) -> Unit): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        val prefix = paths.prefixDir

        onProgress("Downloading Node.js packages…")

        val downloadCmd = """
            cd $prefix/tmp &&
            apt-get update --allow-insecure-repositories 2>&1;
            apt-get download --allow-unauthenticated c-ares libicu libsqlite nodejs-lts npm 2>&1
        """.trimIndent()

        val dlCode = runInPrefix(downloadCmd, onOutput = { onProgress(it) })
        if (dlCode != 0) {
            Log.e(TAG, "apt-get download failed with code $dlCode")
        }

        onProgress("Extracting Node.js packages…")
        val termuxPrefix = "/data/data/com.termux/files/usr"
        val extractCmd = """
            cd $prefix/tmp &&
            mkdir -p _stage &&
            for deb in *.deb; do
                echo "Extracting ${'$'}deb..." &&
                dpkg-deb -x "${'$'}deb" _stage/ 2>&1
            done &&
            if [ -d "_stage$termuxPrefix" ]; then
                cp -a _stage$termuxPrefix/* "$prefix/" 2>&1
            elif [ -d "_stage/usr" ]; then
                cp -a _stage/usr/* "$prefix/" 2>&1
            fi &&
            rm -rf _stage *.deb 2>/dev/null
            echo "done"
        """.trimIndent()

        val extractCode = runInPrefix(extractCmd, onOutput = { onProgress(it) })
        if (extractCode != 0) {
            Log.e(TAG, "dpkg-deb extract failed with code $extractCode")
            return false
        }

        onProgress("Fixing script paths…")
        val fixCmd = """
            chmod 700 "$prefix/bin/node" 2>/dev/null

            CODEX_JS="$prefix/lib/node_modules/@openai/codex/bin/codex.js"
            if [ -f "${CODEX_JS}" ]; then
                sed -i 's|#!/usr/bin/env node|#!'"$prefix/bin/node"'|' "${CODEX_JS}" 2>/dev/null || true
            fi
            echo "done"
        """.trimIndent()

        runInPrefix(fixCmd, onOutput = { onProgress(it) })
        onProgress("Node.js installation complete")
        return true
    }

    fun installPython(onProgress: (String) -> Unit): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        val prefix = paths.prefixDir

        onProgress("Installing Python packages…")
        val installCmd = """
            apt-get update --allow-insecure-repositories 2>&1;
            apt-get install --allow-unauthenticated python3 python3-pip -y 2>&1
        """.trimIndent()

        val code = runInPrefix(installCmd, onOutput = { onProgress(it) })
        return code == 0
    }

    fun isPythonInstalled(): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        return File(paths.prefixDir, "bin/python3").exists()
    }

    fun ensureBionicCompat() {
        val paths = BootstrapInstaller.getPaths(context)
        val home = paths.homeDir
        val bionicCompat = File(home, ".openclaw-android/patches/bionic-compat.js")
        if (bionicCompat.exists()) return

        bionicCompat.parentFile?.mkdirs()
        bionicCompat.writeText("""
            // Bionic compatibility layer for Node.js on Android
            const fs = require('fs');
            const path = require('path');
            
            // Fix DNS resolution on Android
            if (process.platform === 'android') {
                const dns = require('dns');
                dns.setDefaultResultOrder('ipv4first');
            }
        """.trimIndent())
        Log.i(TAG, "Created bionic-compat.js at ${bionicCompat.absolutePath}")
    }

    fun isOpenClawInstalled(): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        return File(paths.prefixDir, "lib/node_modules/@openclaw/cli/bin/openclaw.js").exists()
    }

    fun installOpenClawDeps(onProgress: (String) -> Unit): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        val prefix = paths.prefixDir

        onProgress("Installing OpenClaw build dependencies…")
        val depsCmd = """
            apt-get update --allow-insecure-repositories 2>&1;
            apt-get install --allow-unauthenticated git curl wget build-essential -y 2>&1
        """.trimIndent()

        return runInPrefix(depsCmd, onOutput = { onProgress(it) }) == 0
    }

    fun installOpenClaw(onProgress: (String) -> Unit): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        val prefix = paths.prefixDir

        onProgress("Installing OpenClaw CLI…")
        val installCmd = """
            npm install -g @openclaw/cli --prefix "$prefix" 2>&1 || echo "OpenClaw install completed with warnings"
        """.trimIndent()

        return runInPrefix(installCmd, onOutput = { onProgress(it) }) == 0
    }

    fun installCodex(onProgress: (String) -> Unit): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        val prefix = paths.prefixDir

        onProgress("Installing Codex CLI $CODEX_VERSION…")

        val installCmd = """
            npm install -g @openai/codex@$CODEX_VERSION --prefix "$prefix" 2>&1
        """.trimIndent()

        val code = runInPrefix(installCmd, onOutput = { onProgress(it) })
        if (code != 0) {
            Log.e(TAG, "npm install failed with code $code")
            return false
        }

        onProgress("Codex CLI installed successfully")
        return true
    }

    fun ensureCodexWrapperScript() {
        val paths = BootstrapInstaller.getPaths(context)
        val codexWrapper = File(paths.prefixDir, "bin/codex")
        if (codexWrapper.exists()) return

        codexWrapper.parentFile?.mkdirs()
        codexWrapper.writeText("""
            #!/bin/sh
            exec "${paths.prefixDir}/bin/node" "${paths.prefixDir}/lib/node_modules/@openai/codex/bin/codex.js" "${'$'}@"
        """.trimIndent())
        codexWrapper.setExecutable(true)
        Log.i(TAG, "Created codex wrapper script at ${codexWrapper.absolutePath}")
    }

    // ── Authentication ─────────────────────────────────────────────────────

    private fun codexBinPath(): String {
        val paths = BootstrapInstaller.getPaths(context)
        return "${paths.prefixDir}/lib/node_modules/@openai/codex-linux-arm64" +
            "/vendor/aarch64-unknown-linux-musl/codex/codex"
    }

    fun isLoggedIn(): Boolean {
        val output = runCapture("${codexBinPath()} login status 2>&1")
        Log.i(TAG, "Login status: $output")
        return !output.contains("Not logged in", ignoreCase = true)
    }

    /**
     * Pipe an API key into `codex login --with-api-key` via stdin.
     */
    fun loginWithApiKey(apiKey: String): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        val env = buildEnvironment(paths)

        val pb = ProcessBuilder(codexBinPath(), "login", "--with-api-key")
        pb.environment().clear()
        pb.environment().putAll(env)
        pb.directory(File(paths.homeDir))
        pb.redirectErrorStream(true)

        val proc = pb.start()

        // Write API key to stdin
        val writer = OutputStreamWriter(proc.outputStream)
        writer.write(apiKey + "\n")
        writer.flush()
        writer.close()

        // Read output
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        var line = reader.readLine()
        while (line != null) {
            Log.d(TAG, "[login-apikey] $line")
            line = reader.readLine()
        }

        val exitCode = proc.waitFor()
        Log.i(TAG, "codex login --with-api-key exited with code $exitCode")
        return exitCode == 0
    }

    /**
     * Simple login that just opens the browser URL and waits for completion.
     * Does NOT try to intercept redirect_uri - relies on user completing flow manually.
     */
    fun loginWithUrl(
        onLoginUrl: (url: String) -> Unit,
        onProgress: (String) -> Unit,
    ): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        val env = buildEnvironment(paths).toMutableMap()
        env["HTTPS_PROXY"] = "http://127.0.0.1:$PROXY_PORT"
        env["HTTP_PROXY"] = "http://127.0.0.1:$PROXY_PORT"

        val pb = ProcessBuilder(codexBinPath(), "login")
        pb.environment().clear()
        pb.environment().putAll(env)
        pb.directory(File(paths.homeDir))
        pb.redirectErrorStream(true)

        val proc = pb.start()
        val reader = BufferedReader(InputStreamReader(proc.inputStream))

        var urlFound = false
        var loginComplete = false

        var line = reader.readLine()
        while (line != null) {
            val clean = line.replace(Regex("\\x1b\\[[0-9;]*m"), "").trim()
            Log.d(TAG, "[login] $clean")
            onProgress(clean)

            // Look for any URL that looks like an auth URL
            if (!urlFound) {
                val urlMatch = Regex("""(https://[^\s]+)""").find(clean)
                if (urlMatch != null && urlMatch.value.contains("auth")) {
                    onLoginUrl(urlMatch.value)
                    urlFound = true
                    onProgress(" ")
                    onProgress("If browser login doesn't work, you can:")
                    onProgress("1. Copy the URL and paste it in a browser manually")
                    onProgress("2. After login, return to app or use API key")
                    onProgress(" ")
                }
            }

            // Check for login completion indicators
            if (clean.contains("Logged in", ignoreCase = true) ||
                clean.contains("Authentication successful", ignoreCase = true) ||
                clean.contains("Success", ignoreCase = true)) {
                loginComplete = true
            }

            line = reader.readLine()
        }

        val exitCode = proc.waitFor()
        Log.i(TAG, "codex login exited with code $exitCode")
        
        // Consider it successful if exit code is 0 OR if we saw success messages
        return exitCode == 0 || loginComplete
    }

    // ── Health check ────────────────────────────────────────────────────────

    /**
     * Send a minimal prompt ("hi") to Codex in non-interactive (exec) mode
     * via the CONNECT proxy. Confirms the API key is valid and the native
     * binary can reach OpenAI's servers.
     */
    fun healthCheck(): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        val env = buildEnvironment(paths).toMutableMap()
        env["HTTPS_PROXY"] = "http://127.0.0.1:$PROXY_PORT"
        env["HTTP_PROXY"] = "http://127.0.0.1:$PROXY_PORT"

        val pb = ProcessBuilder(codexBinPath(), "exec", "--non-interactive", "hi")
        pb.environment().clear()
        pb.environment().putAll(env)
        pb.directory(File(paths.homeDir))
        pb.redirectErrorStream(true)

        val proc = pb.start()
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        var line = reader.readLine()
        while (line != null) {
            Log.d(TAG, "[health] $line")
            line = reader.readLine()
        }

        val exitCode = proc.waitFor()
        Log.i(TAG, "health check exited with code $exitCode")
        return exitCode == 0
    }

    // ── Server lifecycle ───────────────────────────────────────────────────

    fun startServer(onProgress: (String) -> Unit): Boolean {
        if (serverProcess != null) return true

        val paths = BootstrapInstaller.getPaths(context)
        val env = buildEnvironment(paths).toMutableMap()
        env["HTTPS_PROXY"] = "http://127.0.0.1:$PROXY_PORT"
        env["HTTP_PROXY"] = "http://127.0.0.1:$PROXY_PORT"
        env["CODEX_APPROVAL_POLICY"] = "never"
        env["CODEX_SANDBOX_MODE"] = "danger-full-access"

        val serverScript = File(paths.prefixDir, "lib/node_modules/@openai/codex/bin/codex.js")
        val shell = "${paths.prefixDir}/bin/sh"

        val pb = ProcessBuilder(
            shell, "-c",
            "${paths.prefixDir}/bin/node $serverScript app-server --port $SERVER_PORT"
        )
        pb.environment().clear()
        pb.environment().putAll(env)
        pb.directory(File(paths.homeDir))
        pb.redirectErrorStream(true)

        val proc = pb.start()
        serverProcess = proc

        Thread {
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line = reader.readLine()
            while (line != null) {
                Log.d(TAG, "[server] $line")
                onProgress("[server] $line")
                line = reader.readLine()
            }
            Log.i(TAG, "Server process exited with code: ${proc.waitFor()}")
        }.start()

        return true
    }

    fun waitForServer(timeoutMs: Long = 60_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        val url = URL("http://127.0.0.1:$SERVER_PORT/")

        while (System.currentTimeMillis() < deadline) {
            try {
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..399) {
                    Log.i(TAG, "Server is ready (HTTP $code)")
                    return true
                }
            } catch (_: Exception) {
                // Not ready yet
            }
            Thread.sleep(500)
        }

        Log.e(TAG, "Server did not become ready within ${timeoutMs}ms")
        return false
    }

    fun stopServer() {
        val proc = serverProcess ?: return
        serverProcess = null

        try {
            proc.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying server process: ${e.message}")
        }

        try {
            proc.waitFor()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        stopOpenClaw()
        stopProxy()
        Log.i(TAG, "Server stopped")
    }

    private fun stopOpenClaw() {
        openClawGatewayProcess?.destroy()
        openClawGatewayProcess = null
        openClawControlUiProcess?.destroy()
        openClawControlUiProcess = null
    }

    fun startProxy(): Boolean {
        if (proxyProcess != null) return true

        val paths = BootstrapInstaller.getPaths(context)
        val proxyScript = File(paths.homeDir, "proxy.js")

        try {
            context.assets.open("proxy.js").use { input ->
                proxyScript.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract proxy.js asset: ${e.message}")
            return false
        }

        val pidFile = File(paths.homeDir, ".proxy.pid")
        if (pidFile.exists()) {
            try {
                val oldPid = pidFile.readText().trim()
                ProcessBuilder("kill", oldPid).start().waitFor()
                Thread.sleep(500)
            } catch (_: Exception) {}
            pidFile.delete()
        }

        val env = buildEnvironment(paths)
        val shell = "${paths.prefixDir}/bin/sh"
        val pb = ProcessBuilder(shell, "-c", "${paths.prefixDir}/bin/node $proxyScript")
        pb.environment().clear()
        pb.environment().putAll(env)
        pb.directory(File(paths.homeDir))
        pb.redirectErrorStream(true)

        val proc = pb.start()
        proxyProcess = proc

        Thread {
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line = reader.readLine()
            while (line != null) {
                Log.d(TAG, "[proxy] $line")
                line = reader.readLine()
            }
            Log.i(TAG, "Proxy exited with code: ${proc.waitFor()}")
        }.start()

        Thread.sleep(800)
        Log.i(TAG, "CONNECT proxy started on 127.0.0.1:$PROXY_PORT")
        return true
    }

    fun stopProxy() {
        val proc = proxyProcess ?: return
        proxyProcess = null

        try {
            proc.destroy()
            proc.waitFor()
        } catch (_: Exception) {}

        Log.i(TAG, "Proxy stopped")
    }

    fun startOpenClaw(onProgress: (String) -> Unit): Boolean {
        if (openClawGatewayProcess != null) return true

        val paths = BootstrapInstaller.getPaths(context)
        val env = buildEnvironment(paths).toMutableMap()
        env["OPENCLAW_PORT"] = OPENCLAW_GATEWAY_PORT.toString()

        val gatewayScript = File(paths.prefixDir, "lib/node_modules/@openclaw/cli/bin/openclaw.js")
        if (!gatewayScript.exists()) {
            Log.w(TAG, "OpenClaw gateway script not found")
            return false
        }

        val pb = ProcessBuilder(
            "${paths.prefixDir}/bin/node",
            gatewayScript.absolutePath,
            "gateway",
            "--port",
            OPENCLAW_GATEWAY_PORT.toString()
        )
        pb.environment().clear()
        pb.environment().putAll(env)
        pb.directory(File(paths.homeDir))
        pb.redirectErrorStream(true)

        val proc = pb.start()
        openClawGatewayProcess = proc

        Thread {
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line = reader.readLine()
            while (line != null) {
                Log.d(TAG, "[openclaw-gateway] $line")
                onProgress("[gateway] $line")
                line = reader.readLine()
            }
            Log.i(TAG, "OpenClaw gateway exited with code: ${proc.waitFor()}")
        }.start()

        onProgress("Starting OpenClaw control UI...")
        val controlUiScript = File(paths.prefixDir, "lib/node_modules/@openclaw/cli/bin/openclaw.js")
        val pb2 = ProcessBuilder(
            "${paths.prefixDir}/bin/node",
            controlUiScript.absolutePath,
            "control-ui",
            "--port",
            OPENCLAW_CONTROL_UI_PORT.toString()
        )
        pb2.environment().clear()
        pb2.environment().putAll(env)
        pb2.directory(File(paths.homeDir))
        pb2.redirectErrorStream(true)

        val proc2 = pb2.start()
        openClawControlUiProcess = proc2

        Thread {
            val reader = BufferedReader(InputStreamReader(proc2.inputStream))
            var line = reader.readLine()
            while (line != null) {
                Log.d(TAG, "[openclaw-control] $line")
                onProgress("[control] $line")
                line = reader.readLine()
            }
            Log.i(TAG, "OpenClaw control UI exited with code: ${proc2.waitFor()}")
        }.start()

        return true
    }

    fun stopOpenClaw() {
        openClawGatewayProcess?.destroy()
        openClawGatewayProcess = null
        openClawControlUiProcess?.destroy()
        openClawControlUiProcess = null
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun extractAssetDir(assetPath: String, targetDir: File) {
        val list = context.assets.list(assetPath) ?: return
        targetDir.mkdirs()
        for (entry in list) {
            val subAsset = "$assetPath/$entry"
            val subTarget = File(targetDir, entry)
            val subList = context.assets.list(subAsset)
            if (subList != null && subList.isNotEmpty()) {
                subTarget.mkdirs()
                extractAssetDir(subAsset, subTarget)
            } else {
                context.assets.open(subAsset).use { input ->
                    subTarget.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    fun ensureDefaultWorkspace() {
        val paths = BootstrapInstaller.getPaths(context)
        val workspaceDir = File(paths.homeDir, "codex")
        if (workspaceDir.exists()) return

        workspaceDir.mkdirs()
        runInPrefix("cd ${workspaceDir.absolutePath} && git init 2>&1")
        Log.i(TAG, "Created default workspace at $workspaceDir")
    }

    fun ensureFullAccessConfig() {
        val paths = BootstrapInstaller.getPaths(context)
        val configDir = File(paths.homeDir, ".codex")
        configDir.mkdirs()
        val configFile = File(configDir, "config.toml")
        val desired = """
            |approval_policy = "never"
            |sandbox_mode = "danger-full-access"
        """.trimMargin().trim() + "\n"

        if (configFile.exists()) {
            val current = configFile.readText()
            if (current.contains("approval_policy") && current.contains("danger-full-access")) {
                return
            }
        }
        configFile.writeText(desired)
        Log.i(TAG, "Wrote full-access config to $configFile")
    }

    private fun buildEnvironment(
        paths: BootstrapInstaller.Paths,
    ): Map<String, String> {
        val bionicCompat = "${paths.homeDir}/.openclaw-android/patches/bionic-compat.js"
        val bionicCompatOpt = if (File(bionicCompat).exists()) " -r $bionicCompat" else ""

        return mapOf(
            "PREFIX" to paths.prefixDir,
            "HOME" to paths.homeDir,
            "PATH" to "${paths.prefixDir}/bin:${paths.prefixDir}/bin/applets:/system/bin",
            "LD_LIBRARY_PATH" to "${paths.prefixDir}/lib",
            "LD_PRELOAD" to "${paths.prefixDir}/lib/libtermux-exec.so",
            "TERMUX_PREFIX" to paths.prefixDir,
            "TERMUX__PREFIX" to paths.prefixDir,
            "LANG" to "en_US.UTF-8",
            "TMPDIR" to paths.tmpDir,
            "TMP" to paths.tmpDir,
            "TEMP" to paths.tmpDir,
            "PROOT_TMP_DIR" to paths.tmpDir,
            "TERM" to "xterm-256color",
            "ANDROID_DATA" to "/data",
            "ANDROID_ROOT" to "/system",
            "APT_CONFIG" to "${paths.prefixDir}/etc/apt/apt.conf",
            "DPKG_ADMINDIR" to "${paths.prefixDir}/var/lib/dpkg",
            "SSL_CERT_FILE" to "${paths.prefixDir}/etc/tls/cert.pem",
            "SSL_CERT_DIR" to "/system/etc/security/cacerts",
            "CURL_CA_BUNDLE" to "${paths.prefixDir}/etc/tls/cert.pem",
            "GIT_SSL_CAINFO" to "${paths.prefixDir}/etc/tls/cert.pem",
            "GIT_CONFIG_NOSYSTEM" to "1",
            "GIT_EXEC_PATH" to "${paths.prefixDir}/libexec/git-core",
            "GIT_TEMPLATE_DIR" to "${paths.prefixDir}/share/git-core/templates",
            "OPENSSL_CONF" to "${paths.prefixDir}/etc/tls/openssl.cnf",
            "NODE_OPTIONS" to "--openssl-config=${paths.prefixDir}/etc/tls/openssl.cnf --unhandled-rejections=warn$bionicCompatOpt",
            "CONTAINER" to "1",
        )
    }
}
