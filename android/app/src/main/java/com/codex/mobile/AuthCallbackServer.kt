package com.codex.mobile

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket

/**
 * Lightweight HTTP server that catches OAuth callbacks from Chrome.
 * Listens on 127.0.0.1 and forwards the auth code to the login process.
 */
class AuthCallbackServer(
    private val port: Int = 18925,
    private val onAuthCode: (code: String, state: String?) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            try {
                serverSocket = ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))
                Log.d(TAG, "AuthCallbackServer listening on 127.0.0.1:$port")
                while (running) {
                    val client = serverSocket?.accept() ?: break
                    handleRequest(client)
                }
            } catch (e: Exception) {
                if (running) Log.e(TAG, "AuthCallbackServer error: ${e.message}")
            }
        }
        thread?.isDaemon = true
        thread?.start()
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        thread?.interrupt()
    }

    private fun handleRequest(client: java.net.Socket) {
        try {
            client.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(client.inputStream))
            val requestLine = reader.readLine() ?: return

            Log.d(TAG, "AuthCallbackServer request: $requestLine")

            // Parse the request path
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                sendResponse(client, 400, "Bad Request")
                return
            }

            val path = parts[1]

            // Handle OAuth callback
            if (path.startsWith("/callback")) {
                val uri = java.net.URI("http://localhost$path")
                val query = uri.query

                if (query != null) {
                    val params = parseQuery(query)
                    val code = params["code"]
                    val state = params["state"]

                    if (code != null) {
                        Log.i(TAG, "Auth code received, forwarding to login process")
                        onAuthCode(code, state)
                        sendResponse(client, 200, """
                            <html><body style="font-family:system-ui;text-align:center;padding:50px;background:#0a0a0a;color:#fff">
                            <h1 style="color:#22c55e">✓ Login Successful</h1>
                            <p>You can close this tab and return to AnyClaw.</p>
                            <script>setTimeout(function(){ window.close(); }, 2000);</script>
                            </body></html>
                        """.trimIndent())
                    } else {
                        sendResponse(client, 400, "Missing authorization code")
                    }
                } else {
                    sendResponse(client, 400, "Missing query parameters")
                }
            } else if (path == "/ping") {
                sendResponse(client, 200, "OK")
            } else {
                sendResponse(client, 404, "Not Found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling callback: ${e.message}")
            try { sendResponse(client, 500, "Internal Error") } catch (_: Exception) {}
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        return query.split("&").associate { param ->
            val kv = param.split("=", limit = 2)
            kv[0] to (kv.getOrNull(1) ?: "")
        }
    }

    private fun sendResponse(client: java.net.Socket, status: Int, body: String) {
        val writer = PrintWriter(client.outputStream, true)
        writer.println("HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}")
        writer.println("Content-Type: text/html; charset=utf-8")
        writer.println("Content-Length: ${body.toByteArray().size}")
        writer.println("Connection: close")
        writer.println()
        writer.print(body)
        writer.flush()
    }

    companion object {
        private const val TAG = "AuthCallbackServer"
    }
}
