package com.guet.foodshare

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Latest and network-error paths are deliberately silent. Installation is confirmed by Android. */
class AppUpdater(private val activity: Activity, private val api: Api) {
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs = activity.getSharedPreferences("updates", Activity.MODE_PRIVATE)
    private var checking = false
    private var installing = false
    fun check() {
        if (checking) return
        checking = true
        executor.execute {
            try {
                val data = api.request("/api/app-update")
                if (!data.optBoolean("available") || data.getLong("versionCode") <= BuildConfig.VERSION_CODE) return@execute
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) AlertDialog.Builder(activity)
                        .setTitle("桂电美食分享有新版本")
                        .setMessage("当前 ${BuildConfig.VERSION_NAME} → ${data.getString("versionName")}\n点击更新将下载并校验安装包，然后打开系统安装界面。")
                        .setNegativeButton("稍后", null)
                        .setPositiveButton("更新") { _, _ -> download(data) }.show()
                }
            } catch (_: Exception) { /* no popup on the latest version or an unavailable release */ }
            finally { checking = false }
        }
    }
    @Suppress("DEPRECATION")
    private fun download(data: JSONObject) {
        val progress = ProgressDialog(activity).apply { setTitle("正在下载更新"); setMessage("下载后自动校验完整性与应用签名"); setProgressStyle(ProgressDialog.STYLE_HORIZONTAL); max = 100; setCancelable(false); show() }
        executor.execute {
            val file = File(activity.cacheDir, "updates/guet-food-share.apk").apply { parentFile!!.mkdirs() }
            try {
                val path = data.getString("downloadPath")
                require(Regex("^/api/app-download/[0-9]+$").matches(path)) { "更新下载地址无效" }
                val expected = data.getLong("size")
                require(expected in 1..250L*1024*1024) { "安装包大小无效" }
                val conn = URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection
                conn.connectTimeout = 20000; conn.readTimeout = 60000; conn.instanceFollowRedirects = false
                val digest = MessageDigest.getInstance("SHA-256")
                try {
                    require(conn.responseCode == 200) { "更新下载失败" }
                    var total = 0L
                    conn.inputStream.use { input -> file.outputStream().use { output ->
                        val buffer = ByteArray(64*1024)
                        while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= expected) { "安装包长度异常" }; digest.update(buffer,0,n); output.write(buffer,0,n); activity.runOnUiThread { progress.progress = (total*100/expected).toInt() } }
                    } }
                    require(total == expected) { "安装包下载不完整" }
                } finally { conn.disconnect() }
                val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                require(hash == data.getString("sha256")) { "安装包校验失败" }
                val archive = activity.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES) ?: error("无法识别安装包")
                val installed = activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_SIGNATURES)
                val version = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
                require(archive.packageName == activity.packageName && version == data.getLong("versionCode") && version > BuildConfig.VERSION_CODE) { "安装包版本或包名不符" }
                val archiveSignatures=archive.signatures?.toSet().orEmpty()
                val installedSignatures=installed.signatures?.toSet().orEmpty()
                require(archiveSignatures.isNotEmpty() && archiveSignatures == installedSignatures) { "应用签名不同，已阻止更新" }
                prefs.edit().putString("pending_apk", file.absolutePath).apply()
                activity.runOnUiThread { progress.dismiss(); resumeInstall() }
            } catch (e: Exception) {
                file.delete()
                activity.runOnUiThread { progress.dismiss(); if (!activity.isFinishing && !activity.isDestroyed) AlertDialog.Builder(activity).setTitle("更新未完成").setMessage(e.message).setPositiveButton("关闭",null).show() }
            }
        }
    }
    fun resumeInstall() {
        if (installing) return
        val path = prefs.getString("pending_apk",null) ?: return
        val file = File(path); if (!file.exists()) { prefs.edit().remove("pending_apk").apply(); return }
        if (!activity.packageManager.canRequestPackageInstalls()) {
            installing = true
            AlertDialog.Builder(activity).setTitle("允许安装更新")
                .setMessage("请在系统页面允许桂电美食分享安装应用，返回后继续安装。")
                .setNegativeButton("取消") { _, _ -> installing=false; prefs.edit().remove("pending_apk").apply() }
                .setPositiveButton("前往设置") { _, _ -> installing=false; activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.packageName))) }.show()
            return
        }
        installing = true
        prefs.edit().remove("pending_apk").apply()
        val uri = FileProvider.getUriForFile(activity, activity.packageName+".updates", file)
        try { activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        finally { installing=false }
    }
    fun close() { executor.shutdown() }
}
