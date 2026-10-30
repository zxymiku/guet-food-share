package com.guet.foodshare

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.widget.*
import java.security.MessageDigest

class MapSettingsActivity:Activity(){
    private lateinit var store:SecureStore
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        store=SecureStore(this)
        val box=ArkUi.column(this,20)
        box.setOnApplyWindowInsetsListener{v,i->v.setPadding(ArkUi.dp(this,20),ArkUi.dp(this,20)+i.systemWindowInsetTop,ArkUi.dp(this,20),ArkUi.dp(this,20)+i.systemWindowInsetBottom);i}
        box.addView(ArkUi.button(this,"← 返回地图"){finish()})
        box.addView(ArkUi.section(this,"PERSONAL MAP / 设置","地图由你掌握","使用自己的 API，读取大家共享的地点。"))
        box.addView(ArkUi.label(this,"高德个人 API 大量使用、超出免费额度或使用收费服务时需要缴费，具体以高德当前规则为准。因此请自行申请 API，并查看用量。"))
        box.addView(ArkUi.label(this,"个人 API 不会上传到本项目服务器、GitHub、投稿或导出文件；地图认证与地址解析会直接发送给高德。本机使用 Android 系统密钥库加密保存。项目代码已经开源，欢迎审查。",14f,ArkUi.muted))
        box.addView(ArkUi.button(this,"01 / 阅读完整申请教程（离线可用）"){showGuide()})
        box.addView(ArkUi.kicker(this,"02 / 申请 ANDROID KEY"))
        box.addView(ArkUi.label(this,"以下信息来自当前安装版本。复制到高德控制台，避免混用正式版与调试版签名。"))
        val fingerprint=certificateSha1()
        box.addView(ArkUi.label(this,"包名\n$packageName\n\n当前签名 SHA-1\n$fingerprint",14f).apply{setTextIsSelectable(true)})
        box.addView(ArkUi.button(this,"复制包名"){copy(packageName)})
        box.addView(ArkUi.button(this,"复制当前 SHA-1"){copy(fingerprint)})
        box.addView(ArkUi.kicker(this,"03 / 填写你的凭据"))
        box.addView(ArkUi.label(this,"Android 平台 Key *"))
        val androidKey=ArkUi.field(this,"粘贴 32 位 Android Key",true).apply{setText(store.amapAndroidKey.orEmpty())};box.addView(androidKey)
        box.addView(ArkUi.label(this,"Web 服务 Key（可选，仅用于自动填写地址）"))
        val restKey=ArkUi.field(this,"粘贴 32 位 Web 服务 Key，或留空",true).apply{setText(store.amapRestKey.orEmpty())};box.addView(restKey)
        box.addView(ArkUi.label(this,"不要填 Web 端 JSAPI Key 或 securityJsCode。仅 Android Key 即可加载地图、选点和手动填写地址。移动网络 IP 经常变化；Web 服务 Key 的 IP 限制与配额请依教程设置。",14f,ArkUi.muted))
        val consent=CheckBox(this).apply{text="我已阅读高德服务说明，同意启用高德地图 SDK。";textSize=14f;isChecked=getSharedPreferences("settings",MODE_PRIVATE).getBoolean("map_consent",false)};box.addView(consent)
        box.addView(ArkUi.button(this,"阅读高德隐私政策 ↗"){open("https://lbs.amap.com/pages/privacy/")})
        val error=ArkUi.label(this,"",14f,ArkUi.signal);error.accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE;box.addView(error)
        box.addView(ArkUi.button(this,"保存并启用地图",true){
            val key=androidKey.text.toString().trim();val rest=restKey.text.toString().trim()
            if(!key.matches(Regex("[0-9a-fA-F]{32}")) || (rest.isNotEmpty()&&!rest.matches(Regex("[0-9a-fA-F]{32}")))){error.text="Key 应为 32 位字母数字（十六进制），请检查平台类型与完整性。";return@button}
            if(!consent.isChecked){error.text="请先阅读并同意地图服务说明。";return@button}
            try{store.amapAndroidKey=key;store.amapRestKey=rest.ifBlank{null}}catch(_:Exception){error.text="本机加密保存失败，请检查系统安全设置后重试。";return@button}
            getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("map_consent",true).apply();setResult(RESULT_OK);finish()
        })
        box.addView(ArkUi.button(this,"清除本机 API 并关闭地图"){
            AlertDialog.Builder(this).setTitle("清除本机地图凭据？").setMessage("公开地点和登录状态不受影响。再次显示地图需重新填写 Key。").setNegativeButton("取消",null).setPositiveButton("清除"){_,_->store.amapAndroidKey=null;store.amapRestKey=null;getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("map_consent",false).apply();setResult(RESULT_OK);finish()}.show()
        })
        box.addView(ArkUi.kicker(this,"OPEN SOURCE / 可审查"))
        box.addView(ArkUi.button(this,"查看项目源代码 ↗"){open("https://github.com/zxymiku/guet-food-share")})
        box.addView(ArkUi.button(this,"查看 Web 源代码 ↗"){open("https://github.com/zxymiku/guet-food-share-web")})
        setContentView(ScrollView(this).apply{addView(box)});ArkUi.reveal(box)
    }
    private fun copy(value:String){(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("高德申请信息",value));Toast.makeText(this,"已复制",Toast.LENGTH_SHORT).show()}
    @Suppress("DEPRECATION") private fun certificateSha1():String{
        val bytes=if(Build.VERSION.SDK_INT>=28)packageManager.getPackageInfo(packageName,PackageManager.GET_SIGNING_CERTIFICATES).signingInfo!!.apkContentsSigners[0].toByteArray() else requireNotNull(packageManager.getPackageInfo(packageName,PackageManager.GET_SIGNATURES).signatures).first().toByteArray()
        return MessageDigest.getInstance("SHA-1").digest(bytes).joinToString(":"){"%02X".format(it.toInt() and 255)}
    }
    private fun open(url:String){runCatching{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}.onFailure{Toast.makeText(this,"没有可打开链接的应用",Toast.LENGTH_LONG).show()}}
    private fun showGuide(){
        val web=WebView(this).apply{
            settings.javaScriptEnabled=false;settings.allowFileAccess=false;settings.allowContentAccess=false
            webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean{if(request.url.scheme=="https"){open(request.url.toString());return true};return request.url.scheme!="file"}}
            loadUrl("file:///android_asset/amap-guide.html")
        }
        AlertDialog.Builder(this).setView(web).setPositiveButton("返回填写",null).setOnDismissListener{web.destroy()}.show().window?.setLayout(-1,-1)
    }
}
