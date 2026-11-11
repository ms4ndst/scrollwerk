package dev.scrollwerk.launcher.config

import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * Calls [onChange] on the main thread shortly after a file in [dir] that passes [accept] is
 * written, replaced (an `adb push`, an editor's save-and-rename) or deleted. Close the result
 * to stop watching.
 */
fun watchFiles(dir: File, accept: (String) -> Boolean, onChange: () -> Unit): AutoCloseable {
    dir.mkdirs()
    val main = Handler(Looper.getMainLooper())
    val fire = Runnable(onChange)
    val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM or
        FileObserver.CREATE or FileObserver.DELETE
    val observer = object : FileObserver(dir, mask) {
        override fun onEvent(event: Int, path: String?) {
            if (path == null || !accept(path)) return
            // Editors and adb write in bursts; settle first.
            main.removeCallbacks(fire)
            main.postDelayed(fire, DEBOUNCE_MS)
        }
    }
    observer.startWatching()
    return AutoCloseable {
        observer.stopWatching()
        main.removeCallbacks(fire)
    }
}

private const val DEBOUNCE_MS = 150L
