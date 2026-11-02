package com.guet.foodshare

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.json.JSONObject

/** Official product links use an explicit coordinate system, never personal keys. */
object Navigation {
    private fun url(base:String,params:Map<String,String>)=Uri.parse(base).buildUpon().apply{params.forEach{(k,v)->appendQueryParameter(k,v)}}.build()
    private fun open(activity:Activity,uri:Uri){try{activity.startActivity(Intent(Intent.ACTION_VIEW,uri))}catch(_:RuntimeException){Toast.makeText(activity,"没有可处理此链接的应用，请安装浏览器或复制地址。",Toast.LENGTH_LONG).show()}}
    fun home(activity:Activity,baidu:Boolean=false)=open(activity,Uri.parse(if(baidu)"https://map.baidu.com/" else "https://www.amap.com/"))
    fun compatibility(activity:Activity){AlertDialog.Builder(activity).setTitle("导航兼容性").setMessage("导航不需要个人 API。高德使用官方应用/网页路线入口，百度优先尝试已安装客户端，未安装则打开网页。浏览器、应用版本或权限可能拦截跳转，请核对起点和目的地。腾讯、苹果、Google等暂未保证直接导航，可复制店名和地址搜索。系统地图的定位权限由对应应用自行申请。").setPositiveButton("知道了",null).show()}
    fun choose(activity:Activity,p:JSONObject){
        val loc=p.getJSONObject("location");val lng=loc.getDouble("lng");val lat=loc.getDouble("lat");val name=p.getString("name").replace('|',' ').replace(',',' ').trim()
        val destination="latlng:$lat,$lng|name:$name"
        val amap=url("https://uri.amap.com/navigation",mapOf("to" to "$lng,$lat,$name","mode" to "walk","coordinate" to "gaode","callnative" to "1","src" to "guet-food-share"))
        val baidu=url("https://api.map.baidu.com/direction",mapOf("origin" to "我的位置","destination" to destination,"mode" to "walking","coord_type" to "gcj02","output" to "html","src" to "andr.guet.foodshare"))
        AlertDialog.Builder(activity).setTitle("选择导航方式").setItems(arrayOf("高德路线（应用 / 网页）","百度路线（应用 / 网页）","复制店名和地址，供其他地图搜索","兼容性说明")){_,which->when(which){
            0->open(activity,amap)
            1->{val native=url("baidumap://map/direction",mapOf("destination" to destination,"mode" to "walking","coord_type" to "gcj02","src" to "andr.guet.foodshare"));try{activity.startActivity(Intent(Intent.ACTION_VIEW,native).setPackage("com.baidu.BaiduMap"))}catch(_:RuntimeException){open(activity,baidu)}}
            2->{(activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("店名和地址",name+" · "+p.getString("address")));Toast.makeText(activity,"已复制，请在其他地图核对搜索结果",Toast.LENGTH_LONG).show()}
            3->compatibility(activity)
        }}.setNegativeButton("取消",null).show()
    }
    fun position(activity:Activity,lng:Double,lat:Double,accuracy:Float){
        val amap=url("https://uri.amap.com/marker",mapOf("position" to "$lng,$lat","name" to "我的位置","coordinate" to "wgs84","callnative" to "0","src" to "guet-food-share"))
        val baidu=url("https://api.map.baidu.com/marker",mapOf("location" to "$lat,$lng","title" to "我的位置","content" to "设备定位结果，请核对精度","coord_type" to "wgs84","output" to "html","src" to "andr.guet.foodshare"))
        AlertDialog.Builder(activity).setTitle("查看设备位置").setMessage("${if(accuracy.isFinite()&&accuracy>0)"设备估计精度约 ${accuracy.toInt()} 米" else "设备未提供定位精度"}。坐标不发送到本站；选择后仅交给对应外部地图。").setPositiveButton("高德地图"){_,_->open(activity,amap)}.setNeutralButton("百度地图"){_,_->open(activity,baidu)}.setNegativeButton("取消",null).show()
    }
}
