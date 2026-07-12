package com.codex.mobile

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Discovers and loads OpenClaw extensions/plugins from the bundled
 * extensions directory and the workspace extensions directory.
 *
 * Extensions are expected to be npm-like packages containing an
 * `openclaw.plugin.json` or a `package.json` with an `openclaw` field.
 */
class ExtensionManager(private val context: Context) {

    companion object {
        private const val TAG = "ExtensionManager"
    }

    data class Extension(
        val id: String,
        val name: String,
        val version: String,
        val description: String,
        val path: String,
        val entryPoint: String?,
        val enabled: Boolean = true,
    )

    private val loadedExtensions = mutableListOf<Extension>()

    /**
     * Scan all extension roots and return a list of discovered extensions.
     * Does not load them into the runtime; use [loadExtensions] for that.
     */
    fun discoverExtensions(): List<Extension> {
        val paths = BootstrapInstaller.getPaths(context)
        val discovered = mutableListOf<Extension>()

        // 1. Bundled extensions shipped inside the Termux prefix
        val bundledRoot = File(paths.prefixDir, "lib/node_modules/openclaw/extensions")
        if (bundledRoot.isDirectory) {
            discovered.addAll(scanDirectory(bundledRoot))
        } else {
            Log.d(TAG, "Bundled extension root not found: ${bundledRoot.absolutePath}")
        }

        // 2. Global extensions installed by the user
        val globalRoot = File(paths.homeDir, ".openclaw/extensions")
        if (globalRoot.isDirectory) {
            discovered.addAll(scanDirectory(globalRoot))
        } else {
            Log.d(TAG, "Global extension root not found: ${globalRoot.absolutePath}")
        }

        // 3. Workspace extensions
        val workspaceRoot = File(paths.homeDir, "codex/.openclaw/extensions")
        if (workspaceRoot.isDirectory) {
            discovered.addAll(scanDirectory(workspaceRoot))
        } else {
            Log.d(TAG, "Workspace extension root not found: ${workspaceRoot.absolutePath}")
        }

        return discovered
    }

    private fun scanDirectory(root: File): List<Extension> {
        val result = mutableListOf<Extension>()
        if (!root.isDirectory) {
            Log.w(TAG, "Not a directory: ${root.absolutePath}")
            return result
        }

        val children = root.listFiles() ?: run {
            Log.w(TAG, "Cannot list directory: ${root.absolutePath}")
            return result
        }

        for (child in children) {
            if (!child.isDirectory) continue

            val pluginJson = File(child, "openclaw.plugin.json")
            val packageJson = File(child, "package.json")

            try {
                if (pluginJson.exists()) {
                    val json = JSONObject(pluginJson.readText())
                    result.add(
                        Extension(
                            id = json.optString("id", child.name),
                            name = json.optString("name", child.name),
                            version = json.optString("version", "0.0.0"),
                            description = json.optString("description", ""),
                            path = child.absolutePath,
                            entryPoint = json.optString("main", null),
                        )
                    )
                } else if (packageJson.exists()) {
                    val json = JSONObject(packageJson.readText())
                    val openclaw = json.optJSONObject("openclaw")
                    val id = openclaw?.optString("id", json.optString("name", child.name)) ?: child.name
                    val entryPoint = openclaw?.optString("main", json.optString("main", null))
                    result.add(
                        Extension(
                            id = id,
                            name = json.optString("name", child.name),
                            version = json.optString("version", "0.0.0"),
                            description = json.optString("description", ""),
                            path = child.absolutePath,
                            entryPoint = entryPoint,
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse extension metadata for ${child.name}: ${e.message}")
            }
        }

        return result
    }

    /**
     * Load all discovered extensions by writing them into the OpenClaw
     * config's `plugins.load.paths` list. This is a lightweight load that
     * makes OpenClaw aware of the extensions on next gateway start.
     */
    fun loadExtensions(): List<Extension> {
        val extensions = discoverExtensions()
        loadedExtensions.clear()
        loadedExtensions.addAll(extensions)

        if (extensions.isEmpty()) {
            Log.i(TAG, "No extensions discovered")
            return extensions
        }

        val paths = BootstrapInstaller.getPaths(context)
        val openclawDir = File(paths.homeDir, ".openclaw")
        openclawDir.mkdirs()
        val configFile = File(openclawDir, "openclaw.json")

        try {
            val config = if (configFile.exists()) {
                JSONObject(configFile.readText())
            } else {
                JSONObject()
            }

            val plugins = config.optJSONObject("plugins") ?: JSONObject().apply { config.put("plugins", this) }
            // OpenClaw config uses plugins.load.paths for explicit plugin load paths.
            val loadConfig = plugins.optJSONObject("load") ?: JSONObject().apply { plugins.put("load", this) }
            val loadPaths = loadConfig.optJSONArray("paths") ?: org.json.JSONArray().apply { loadConfig.put("paths", this) }

            for (ext in extensions) {
                val path = ext.path
                var alreadyListed = false
                for (i in 0 until loadPaths.length()) {
                    if (loadPaths.optString(i, null) == path) {
                        alreadyListed = true
                        break
                    }
                }
                if (!alreadyListed) {
                    loadPaths.put(path)
                }
            }

            configFile.writeText(config.toString(2))
            Log.i(TAG, "Loaded ${extensions.size} extension(s) into OpenClaw config")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write extension load paths: ${e.message}")
        }

        return extensions
    }

    fun getLoadedExtensions(): List<Extension> = loadedExtensions.toList()

    /**
     * Return a JSON string describing loaded extensions for the WebView.
     */
    fun getExtensionsJson(): String {
        val jsonArray = org.json.JSONArray()
        for (ext in loadedExtensions) {
            val obj = JSONObject()
            obj.put("id", ext.id)
            obj.put("name", ext.name)
            obj.put("version", ext.version)
            obj.put("description", ext.description)
            obj.put("path", ext.path)
            obj.put("enabled", ext.enabled)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }
}
