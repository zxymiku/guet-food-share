package com.guet.foodshare

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class Api(private val token: () -> String?) {
    fun request(path: String, method: String = "GET", data: ByteArray? = null, mime: String = "application/json"): JSONObject {
        val conn = URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 25000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/json")
            token()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (data != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", mime)
                conn.setFixedLengthStreamingMode(data.size)
                conn.outputStream.use { it.write(data) }
            }
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
            val result = JSONObject(text)
            if (status !in 200..299) throw IllegalStateException(result.optString("error", "请求失败 ($status)"))
            return result
        } finally { conn.disconnect() }
    }
    fun post(path: String, data: JSONObject) = request(path, "POST", data.toString().toByteArray())
    fun uploadVideo(context: Context, uri: Uri): JSONObject {
        val mime=context.contentResolver.getType(uri) ?: "video/mp4"
        require(mime=="video/mp4"||mime=="video/webm") { "视频仅支持 MP4 / WebM" }
        val size=context.contentResolver.openAssetFileDescriptor(uri,"r")!!.use { it.length }
        require(size in 12..50L*1024*1024) { "视频需小于 50 MB，且能读取文件大小" }
        val conn=URL(BuildConfig.API_BASE_URL+"/api/media").openConnection() as HttpURLConnection
        try {
            conn.requestMethod="POST";conn.doOutput=true;conn.connectTimeout=20000;conn.readTimeout=120000;conn.instanceFollowRedirects=false
            conn.setRequestProperty("Content-Type",mime);conn.setRequestProperty("X-File-Size",size.toString());token()?.let {conn.setRequestProperty("Authorization","Bearer $it")};conn.setFixedLengthStreamingMode(size)
            context.contentResolver.openInputStream(uri)!!.use { input -> conn.outputStream.use { output -> input.copyTo(output,64*1024) } }
            val status=conn.responseCode;val data=JSONObject((if(status in 200..299)conn.inputStream else conn.errorStream).bufferedReader().use {it.readText()})
            if(status !in 200..299)throw IllegalStateException(data.optString("error","视频上传失败"));return data
        } finally {conn.disconnect()}
    }
    fun photo(path: String): ByteArray {
        require(path.startsWith("/api/photos/"))
        val conn = URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000; conn.readTimeout = 25000
            token()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (conn.responseCode != 200) throw IllegalStateException("照片加载失败")
            return conn.inputStream.use { it.readBytes() }
        } finally { conn.disconnect() }
    }
}
