package com.guet.foodshare

import android.content.Context
import com.amap.api.maps.model.LatLng
import org.json.JSONObject
import kotlin.math.*

/** Private collection contains only stable place IDs and explicit user choices. */
class Discovery(context:Context){
 private val prefs=context.getSharedPreferences("private_collection",Context.MODE_PRIVATE)
 private fun data()=runCatching{JSONObject(prefs.getString("marks","{}")?:"{}")}.getOrDefault(JSONObject())
 private var marks=data()
 fun mark(id:String)=marks.optJSONObject(id)?:JSONObject()
 fun update(id:String,favorite:Boolean?=null,visit:String?=null){val all=data();require(all.has(id)||all.length()<2000){"本机收藏最多 2000 个"};val row=all.optJSONObject(id)?:JSONObject();favorite?.let{row.put("favorite",it)};visit?.let{require(it in listOf("","want","visited"));row.put("visit",it)};all.put(id,row);check(prefs.edit().putString("marks",all.toString()).commit()){"保存失败，请检查设备空间"};marks=all}
 fun clear(){check(prefs.edit().clear().commit()){"清除失败"};marks=JSONObject()}
 companion object{
  fun distance(origin:LatLng,p:JSONObject):Double{
   val location=p.getJSONObject("location")
   val lat=location.getDouble("lat")
   val lng=location.getDouble("lng")
   val dlat=Math.toRadians(lat-origin.latitude)
   val dlng=Math.toRadians(lng-origin.longitude)
   val a=sin(dlat/2).pow(2)+cos(Math.toRadians(origin.latitude))*cos(Math.toRadians(lat))*sin(dlng/2).pow(2)
   return 6371*2*asin(sqrt(a.coerceIn(0.0,1.0)))
  }
  fun reputation(p:JSONObject):Double{val r=p.optJSONObject("rating");val n=r?.optInt("count")?:0;val avg=r?.optDouble("average",0.0)?:0.0;return (avg*n+17.5)/(n+5)}
 }
}
