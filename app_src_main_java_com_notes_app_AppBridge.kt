package com.notes.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast

/**
 * Exposed to the page as window.NotesNative.
 * The page creates downloads with <a download href="blob:...">; WebView ignores those,
 * so INJECT_JS intercepts the click, reads the blob and hands the bytes to saveFile().
 */
class AppBridge(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())

    private fun toastNow(msg: String) {
        main.post { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
    }

    @JavascriptInterface
    fun toast(msg: String) = toastNow(msg)

    @JavascriptInterface
    fun setClipboard(text: String) {
        main.post {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("note", text))
        }
    }

    @JavascriptInterface
    fun getClipboard(): String {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            if (clip == null || clip.itemCount == 0) "" else clip.getItemAt(0).coerceToText(context).toString()
        } catch (e: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun saveFile(name: String, mime: String, base64: String) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "notes.txt" }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safe)
                put(MediaStore.Downloads.MIME_TYPE, mime.ifBlank { "application/octet-stream" })
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create file")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            toastNow("Saved to Downloads: $safe")
        } catch (e: Exception) {
            toastNow("Save failed: ${e.message}")
        }
    }

    companion object {
        val INJECT_JS = """
(function () {
  if (window.__nativeDl) return;
  window.__nativeDl = true;
  var origClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    var a = this;
    if (a.hasAttribute('download') && a.href && window.NotesNative) {
      var name = a.getAttribute('download') || 'download';
      fetch(a.href)
        .then(function (r) { return r.blob(); })
        .then(function (b) {
          var fr = new FileReader();
          fr.onload = function () {
            var s = String(fr.result);
            window.NotesNative.saveFile(name, b.type || 'application/octet-stream', s.substring(s.indexOf(',') + 1));
          };
          fr.readAsDataURL(b);
        })
        .catch(function () { window.NotesNative.toast('Save failed'); });
      return;
    }
    return origClick.apply(this, arguments);
  };
})();
"""
    }
}
