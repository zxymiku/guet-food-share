package com.guet.foodshare

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Direct AMap connection. Never attach the project's bearer token or log the URL. */
object PersonalGeocoder {
    fun lookup(key:String,lng:Double,lat:Double):JSONObject{
        require(key.matches(Regex("[0-9a-fA-F]{32}"))) { "Web 服务 Key 格式不正确" }
        val connection=URL("https://restapi.amap.com/v3/geocode/regeo?key=$key&location=$lng,$lat&extensions=all&radius=100&poitype=050000%7C060000").openConnection() as HttpURLConnection
        try{
            connection.connectTimeout=10000;connection.readTimeout=12000;connection.instanceFollowRedirects=false
            check(connection.responseCode==200){"高德地址服务连接失败，请手动填写"}
            val data=JSONObject(connection.inputStream.bufferedReader().use{it.readText()})
            check(data.optString("status")=="1"){"地址解析失败，请检查个人 Web 服务 Key 的平台、权限、IP 限制和额度"}
            val geo=data.getJSONObject("regeocode")
            return JSONObject().put("address",geo.optString("formatted_address")).put("pois",geo.optJSONArray("pois")?:JSONArray())
        }catch(e:Exception){
            // Network exceptions may contain request URLs; never surface credentials.
            throw IllegalStateException("无法自动填写地址，请检查个人 Web 服务 Key、网络和额度，或手动填写")
        }finally{connection.disconnect()}
    }
}
