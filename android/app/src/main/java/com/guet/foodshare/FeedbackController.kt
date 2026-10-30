package com.guet.foodshare

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

class FeedbackController(
    private val activity: Activity,
    private val api: Api,
    private val isLoggedIn: () -> Boolean,
    private val encodeScreenshot: (Uri) -> ByteArray
) {
    companion object { const val PICK_SCREENSHOTS = 102 }
    private val executor=Executors.newSingleThreadExecutor()
    private val images=mutableListOf<Uri>()
    private val uploaded=mutableMapOf<Uri,String>()
    private var preview: LinearLayout?=null
    private var caption: TextView?=null
    private fun dp(n:Int)=(n*activity.resources.displayMetrics.density).toInt()
    private fun toast(message:String){Toast.makeText(activity,message,Toast.LENGTH_LONG).show()}
    private fun ui(action:()->Unit){activity.runOnUiThread{if(!activity.isFinishing&&!activity.isDestroyed)action()}}
    private fun open(url:String){try{activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(_:Exception){toast("没有可打开链接的应用")}}
    fun show(){
        AlertDialog.Builder(activity).setTitle("意见反馈 · Android")
            .setItems(arrayOf("手动反馈 · 打开 GitHub","自动反馈 · 填写文字和截图")){_,which->
                if(which==0)open("https://github.com/zxymiku/guet-food-share/issues/new?title="+Uri.encode("[Android反馈] "))else automatic()
            }.setNegativeButton("取消",null).show()
    }
    private fun automatic(){
        images.clear();uploaded.clear()
        val content=ArkUi.column(activity,18);content.addView(ArkUi.section(activity,"反馈 / ANDROID","让它更好用","问题、建议与截图将送达项目仓库。"))
        fun label(value:String)=ArkUi.label(activity,value).apply{setPadding(0,dp(12),0,dp(5));content.addView(this)}
        label("反馈自动发布到 Android 主仓库；密码登录由管理员代发，GitHub 登录使用你自己的账号。")
        label("标题 *（最多 100 字）")
        val title=ArkUi.field(activity,"简要描述问题或建议").apply{setSingleLine();filters=arrayOf(android.text.InputFilter.LengthFilter(100));content.addView(this)}
        label("反馈详情 *（最多 4000 字）")
        val description=ArkUi.field(activity,"发生了什么？如何重现？希望怎样改进？").apply{setSingleLine(false);minLines=4;filters=arrayOf(android.text.InputFilter.LengthFilter(4000));content.addView(this)}
        caption=label("截图可选，最多 3 张 JPEG / PNG，每张 8 MB")
        val choose=Button(activity).apply{text="＋ 添加截图";setOnClickListener{
            activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("image/jpeg","image/png"));putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addCategory(Intent.CATEGORY_OPENABLE)},PICK_SCREENSHOTS)
        };content.addView(this)}
        preview=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;content.addView(this)}
        val consent=CheckBox(activity).apply{text="我同意将本次反馈文字、截图和应用版本公开发布到 GitHub Issue，已检查截图中需要遮挡的个人信息。";textSize=14f;content.addView(this)}
        val error=TextView(activity).apply{setTextColor(ArkUi.signal);textSize=14f;content.addView(this)}
        var requestId=UUID.randomUUID().toString();var previousFingerprint=""
        val dialog=AlertDialog.Builder(activity).setTitle("自动反馈 · Android").setView(ScrollView(activity).apply{addView(content)})
            .setNegativeButton("取消",null).setPositiveButton("提交并创建 Issue",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!isLoggedIn()){error.text="请先使用共享密码或 GitHub 登录后自动反馈。";return@setOnClickListener}
            val heading=title.text.toString().trim();val detail=description.text.toString().trim()
            if(heading.isEmpty()||detail.isEmpty()||!consent.isChecked){error.text="请填写标题、详情，并确认公开反馈授权。";return@setOnClickListener}
            val snapshot=images.toList();val fingerprint=JSONArray().put(heading).put(detail).put(snapshot.joinToString("\n")).toString()
            if(fingerprint!=previousFingerprint){requestId=UUID.randomUUID().toString();previousFingerprint=fingerprint}
            val submitId=requestId;val submit=dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val controls=listOf(title,description,choose,consent,submit);controls.forEach{it.isEnabled=false};submit.text="提交中…";error.text=""
            executor.execute{try{
                val screenshots=JSONArray()
                for(uri in snapshot){
                    val path=uploaded[uri]?:api.request("/api/feedback/screenshots","POST",encodeScreenshot(uri),"image/jpeg").getString("url").also{uploaded[uri]=it}
                    screenshots.put(path)
                }
                val result=api.post("/api/feedback",JSONObject().put("requestId",submitId).put("platform","android").put("title",heading).put("description",detail).put("screenshots",screenshots).put("appVersion",BuildConfig.VERSION_NAME).put("consent",true))
                val url=result.getString("issueUrl")
                require(url.startsWith("https://github.com/zxymiku/guet-food-share/issues/")){"反馈链接无效"}
                ui{dialog.dismiss();AlertDialog.Builder(activity).setTitle("反馈已提交").setMessage("已创建 Android Issue #${result.getInt("number")}").setNegativeButton("关闭",null).setPositiveButton("查看 Issue"){_,_->open(url)}.show()}
            }catch(e:Exception){ui{controls.forEach{it.isEnabled=true};submit.text="提交并创建 Issue";error.text=e.message?:"提交失败，请保留表单稍后重试。"}}}
        }}
        dialog.show()
    }
    fun onPicked(resultCode:Int,data:Intent?){
        if(resultCode!=Activity.RESULT_OK)return
        val picked=mutableListOf<Uri>()
        data?.clipData?.let{clip->for(i in 0 until clip.itemCount)picked.add(clip.getItemAt(i).uri)}?:data?.data?.let{picked.add(it)}
        if(picked.size>3){toast("最多添加 3 张反馈截图");return}
        if(picked.any{activity.contentResolver.getType(it) !in listOf("image/jpeg","image/png")} ){toast("反馈截图仅支持 JPEG / PNG");return}
        images.clear();images.addAll(picked);caption?.text="已选择 ${images.size} 张反馈截图";preview?.removeAllViews()
        for(uri in picked){
            val bitmap=runCatching{
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                activity.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
                var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>240)sample*=2
                activity.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}
            }.getOrNull()
            preview?.addView(ImageView(activity).apply{setImageBitmap(bitmap);scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription="待提交反馈截图"},LinearLayout.LayoutParams(dp(75),dp(75)).apply{marginEnd=dp(8)})
        }
    }
    fun close(){executor.shutdownNow()}
}
