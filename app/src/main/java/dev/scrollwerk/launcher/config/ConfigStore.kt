package dev.scrollwerk.launcher.config

import java.io.File

data class LoadedConfig(val config: LauncherConfig, val error: String? = null)

/**
 * File-backed config. Lives in the app's external files dir so it can be edited with
 * `adb pull` / `adb push` without root:
 * `/sdcard/Android/data/dev.scrollwerk.launcher/files/config.json`.
 */
class ConfigStore(private val file: File) {

    /** Writes [config] back (used by in-app settings such as the theme switcher). */
    fun save(config: LauncherConfig): Boolean = runCatching {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(LauncherConfig.encode(config))
        check(tmp.renameTo(file)) { "rename failed" }
    }.isSuccess

    /** Loads the file. On a parse error, keeps [lastGood] and reports the error. */
    fun load(lastGood: LauncherConfig? = null): LoadedConfig {
        if (!file.exists()) {
            val defaults = LauncherConfig()
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(LauncherConfig.encode(defaults))
            }
            return LoadedConfig(defaults)
        }
        val text = runCatching { file.readText() }
            .getOrElse { return LoadedConfig(lastGood ?: LauncherConfig(), "Can't read ${file.name}: ${it.message}") }
        return LauncherConfig.parse(text).fold(
            onSuccess = { LoadedConfig(it) },
            onFailure = {
                LoadedConfig(
                    lastGood ?: LauncherConfig(),
                    "Error in ${file.name}: ${it.message?.lineSequence()?.firstOrNull()}",
                )
            },
        )
    }
}
