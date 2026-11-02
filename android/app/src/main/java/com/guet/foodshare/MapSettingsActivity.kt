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
        box.addView(ArkUi.section(this,"MAP / 地图与导航","普通查看，无需个人 API","开发者公共底图和外部导航，个人 API 保留为高级选项。"))
        box.addView(ArkUi.button(this,"在外部地图查看我的位置",true){setResult(RESULT_OK,Intent().putExtra("externalPosition",true));finish()})
        box.addView(ArkUi.button(this,"直接打开高德地图"){Navigation.home(this)})
        box.addView(ArkUi.button(this,"直接打开百度地图"){Navigation.home(this,true)})
        box.addView(ArkUi.button(this,"导航兼容性与其他地图"){Navigation.compatibility(this)})
        box.addView(ArkUi.button(this,"启用开发者公共底图（不填个人 Key）"){
            AlertDialog.Builder(this).setTitle("启用公共底图？").setMessage("开发者配置完成后即可浏览。启用会连接高德地图 SDK，定位需另行授权，请阅读高德隐私政策。若公共底图尚未配置，仍可用外部地图。").setNeutralButton("隐私政策"){_,_->open("https://lbs.amap.com/pages/privacy/")}.setNegativeButton("取消",null).setPositiveButton("同意并启用"){_,_->getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("map_consent",true).putBoolean("map_use_public",true).apply();setResult(RESULT_OK);finish()}.show()
        })
        val advanced=ArkUi.column(this).apply{visibility=View.GONE}
        box.addView(ArkUi.button(this,"高级个人 API（可不填写）"){advanced.visibility=if(advanced.visibility==View.VISIBLE)View.GONE else View.VISIBLE})
        advanced.addView(ArkUi.label(this,"公共底图由开发者管理额度与费用，用户无需申请。个人 API 是自管额度及投稿辅助功能的可选项，大量使用可能需付费。"))
        advanced.addView(ArkUi.label(this,"个人 API 不会上传到本项目服务器、GitHub、投稿或导出文件；地图认证与地址解析会直接发送给高德。本机使用 Android 系统密钥库加密保存。项目代码已经开源，欢迎审查。",14f,ArkUi.muted))
        advanced.addView(ArkUi.kicker(this,"PERSONAL API / 高级选项，可不填写"))
        advanced.addView(ArkUi.button(this,"01 / 阅读完整申请教程（离线可用）"){showGuide()})
        advanced.addView(ArkUi.kicker(this,"02 / 申请 ANDROID KEY"))
        advanced.addView(ArkUi.label(this,"以下信息来自当前安装版本。复制到高德控制台，避免混用正式版与调试版签名。"))
        val fingerprint=certificateSha1()
        advanced.addView(ArkUi.label(this,"包名\n$packageName\n\n当前签名 SHA-1\n$fingerprint",14f).apply{setTextIsSelectable(true)})
        advanced.addView(ArkUi.button(this,"复制包名"){copy(packageName)})
        advanced.addView(ArkUi.button(this,"复制当前 SHA-1"){copy(fingerprint)})
        advanced.addView(ArkUi.kicker(this,"03 / 填写你的凭据"))
        advanced.addView(ArkUi.label(this,"Android 平台 Key *"))
        val androidKey=ArkUi.field(this,"粘贴 32 位 Android Key",true).apply{setText(store.amapAndroidKey.orEmpty())};advanced.addView(androidKey)
        advanced.addView(ArkUi.label(this,"Web 服务 Key（可选，仅用于自动填写地址）"))
        val restKey=ArkUi.field(this,"粘贴 32 位 Web 服务 Key，或留空",true).apply{setText(store.amapRestKey.orEmpty())};advanced.addView(restKey)
        advanced.addView(ArkUi.label(this,"不要填 Web 端 JSAPI Key 或 securityJsCode。仅 Android Key 即可加载地图、选点和手动填写地址。移动网络 IP 经常变化；Web 服务 Key 的 IP 限制与配额请依教程设置。",14f,ArkUi.muted))
        val consent=CheckBox(this).apply{text="我已阅读高德服务说明，同意启用高德地图 SDK。";textSize=14f;isChecked=getSharedPreferences("settings",MODE_PRIVATE).getBoolean("map_consent",false)};advanced.addView(consent)
        advanced.addView(ArkUi.button(this,"阅读高德隐私政策 ↗"){open("https://lbs.amap.com/pages/privacy/")})
        val error=ArkUi.label(this,"",14f,ArkUi.signal);error.accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE;advanced.addView(error)
        advanced.addView(ArkUi.button(this,"保存并启用地图",true){
            val key=androidKey.text.toString().trim();val rest=restKey.text.toString().trim()
            if(!key.matches(Regex("[0-9a-fA-F]{32}")) || (rest.isNotEmpty()&&!rest.matches(Regex("[0-9a-fA-F]{32}")))){error.text="Key 应为 32 位字母数字（十六进制），请检查平台类型与完整性。";return@button}
            if(!consent.isChecked){error.text="请先阅读并同意地图服务说明。";return@button}
            try{store.amapAndroidKey=key;store.amapRestKey=rest.ifBlank{null}}catch(_:Exception){error.text="本机加密保存失败，请检查系统安全设置后重试。";return@button}
            getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("map_consent",true).putBoolean("map_use_public",false).apply();setResult(RESULT_OK);finish()
        })
        advanced.addView(ArkUi.button(this,"清除本机 API 并关闭地图"){
            AlertDialog.Builder(this).setTitle("清除本机地图凭据？").setMessage("清除个人凭据并关闭嵌入地图；公开地点和登录状态不受影响，仍可直接使用外部地图。").setNegativeButton("取消",null).setPositiveButton("清除"){_,_->store.amapAndroidKey=null;store.amapRestKey=null;getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("map_consent",false).putBoolean("map_use_public",false).apply();setResult(RESULT_OK);finish()}.show()
        })
        box.addView(advanced)
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
