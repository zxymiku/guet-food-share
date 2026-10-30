package com.guet.foodshare

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.widget.*
import com.amap.api.maps.*
import com.amap.api.maps.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val orange = Color.rgb(217, 78, 44)
    private val executor = Executors.newFixedThreadPool(3)
    private lateinit var store: SecureStore
    private lateinit var api: Api
    private lateinit var updater: AppUpdater
    private lateinit var root: LinearLayout
    private lateinit var mapHost: FrameLayout
    private lateinit var list: LinearLayout
    private lateinit var loginButton: Button
    private var mapView: MapView? = null
    private var amap: AMap? = null
    private var places = listOf<JSONObject>()
    private var selected: LatLng? = null
    private var selectedMarker: Marker? = null
    private var picking = false
    private var submitDialog: AlertDialog? = null
    private val photos = mutableListOf<Uri>()
    private val videos = mutableListOf<Uri>()
    private var videoLabel: TextView? = null
    private var addressInput: EditText? = null
    private var nameInput: EditText? = null
    private var autoAddress: String? = null
    private var nearbyName: String? = null
    private var locatingForForm = false
    private var photoLabel: TextView? = null
    private var lngInput: EditText? = null
    private var latInput: EditText? = null
    private var searchQuery = ""
    private var categoryFilter = "全部"
    private val categories = arrayOf("中餐", "小吃", "面馆", "咖啡", "甜品", "其他")
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var locationListener: LocationListener? = null
    private var locationManager: LocationManager? = null
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 14f) = TextView(this).apply { text = value; textSize = size; setTextColor(Color.rgb(44, 50, 41)); setPadding(dp(8), dp(7), dp(8), dp(7)) }
    private fun button(value: String, action: () -> Unit) = Button(this).apply { text = value; textSize = 12f; setTextColor(orange); setOnClickListener { action() } }
    private fun toast(value: String) { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
    private fun async(action: () -> Unit) { executor.execute { try { action() } catch (e: Exception) { runOnUiThread { if (!isFinishing) toast(e.message ?: "请求失败，请重试") } } } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(252,251,247)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        store = SecureStore(this); api = Api { store.token }; updater = AppUpdater(this,api)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(252,251,247)); setPadding(dp(12),dp(8),dp(12),0) }
        root.setOnApplyWindowInsetsListener { view, insets -> view.setPadding(dp(12), dp(8) + insets.systemWindowInsetTop, dp(12), insets.systemWindowInsetBottom); insets }
        setContentView(root)
        val top = LinearLayout(this)
        top.addView(text("桂电美食分享", 23f), LinearLayout.LayoutParams(0,dp(56),1f))
        loginButton = button(if (store.token == null) "GitHub 登录" else "退出登录") { login() }
        top.addView(loginButton); root.addView(top)
        root.addView(text("桂林电子科技大学 · 校园好味道", 18f))
        val controls = LinearLayout(this)
        controls.addView(button("＋ 分享") { share() }); controls.addView(button("我的投稿") { mine() }); controls.addView(button("◎ 定位") { locate() }); root.addView(controls)
        mapHost = FrameLayout(this); root.addView(mapHost, LinearLayout.LayoutParams(-1,0,1.1f))
        mapHost.addView(text("同意地图服务说明后可显示高德地图。\n无需定位授权，也能手动选点。"))
        val search = EditText(this).apply { hint = "搜索店名、地址"; textSize = 13f; setSingleLine(); addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { searchQuery = s.toString(); renderPlaces() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }) }; root.addView(search)
        val filters = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("全部") + categories); onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { categoryFilter = (arrayOf("全部") + categories)[position]; renderPlaces() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        } }; root.addView(filters)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1,0,.85f))
        if (prefs.getBoolean("map_consent",false)) initMap(savedInstanceState) else privacy(savedInstanceState)
        refresh(); handleLogin(intent); updater.check()
    }
    private fun privacy(bundle: Bundle?) {
        AlertDialog.Builder(this).setTitle("高德地图服务说明")
            .setMessage("桂电美食分享使用高德地图显示门店与选点。启用地图后，高德 SDK 会按其隐私政策处理设备与网络信息。设备定位仅在点击并授权后获取，用于填写坐标和解析地址；确认提交后才保存投稿。投稿文字、门店位置和照片仅在你明确同意提交后保存，审核通过后公开。图片将在上传前重新编码移除原始元数据。图片、视频以密文保存在公开 GitHub 媒体仓库；审核通过后可在应用观看。文字与门店坐标会进入公开审核 PR。视频原始文件可能含设备元数据，请自行确认可公开。\n\n完整政策可点击下方链接阅读。")
            .setNeutralButton("查看高德政策") { _, _ -> open("https://lbs.amap.com/pages/privacy/"); privacy(bundle) }
            .setNegativeButton("暂不启用") { _, _ -> toast("仍可浏览地点和填写高德坐标投稿") }
            .setPositiveButton("同意并启用") { _, _ -> prefs.edit().putBoolean("map_consent",true).apply(); initMap(bundle) }.setCancelable(false).show()
    }
    private fun initMap(bundle: Bundle?) {
        if (mapView != null) return
        val key = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData.getString("com.amap.api.v2.apikey")
        if (key.isNullOrBlank()) { mapHost.removeAllViews(); mapHost.addView(text("尚未配置高德 Android Key。\n请在 local.properties 中设置后重新构建。")); return }
        MapsInitializer.updatePrivacyShow(this,true,true); MapsInitializer.updatePrivacyAgree(this,true)
        mapView = MapView(this).also { it.onCreate(bundle); mapHost.removeAllViews(); mapHost.addView(it) }
        amap = mapView!!.map.apply {
            moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(25.274215,110.299121),13f))
            setOnMapClickListener { point ->
                selected = point; selectedMarker?.remove(); selectedMarker = addMarker(MarkerOptions().position(point).title("待投稿门店位置"))
                if (picking) { picking = false; resolveAddress(point); lngInput?.setText("%.6f".format(java.util.Locale.US,point.longitude)); latInput?.setText("%.6f".format(java.util.Locale.US,point.latitude)); submitDialog?.show() }
                else toast("已选点，点击“分享”填写门店信息")
            }
            setOnMarkerClickListener { marker -> (marker.`object` as? JSONObject)?.let { detail(it); true } ?: false }
        }
        renderPlaces()
    }
    private fun refresh() { async { val result = api.request("/api/places").getJSONArray("places"); val data = (0 until result.length()).map { result.getJSONObject(it) }; runOnUiThread { places = data; renderPlaces() } } }
    private fun renderPlaces() {
        if (!::list.isInitialized) return
        list.removeAllViews(); amap?.clear(); selectedMarker = null
        val visible = places.filter { (categoryFilter == "全部" || it.getString("category") == categoryFilter) && (it.getString("name")+it.getString("address")).contains(searchQuery,true) }
        if (visible.isEmpty()) list.addView(text("还没有匹配的公开地点。分享你的第一家好店吧。"))
        visible.forEach { p ->
            list.addView(button("${p.getString("name")}  ·  ${p.getString("category")}\n${p.getString("address")}") { detail(p) })
            val loc = p.getJSONObject("location"); amap?.addMarker(MarkerOptions().position(LatLng(loc.getDouble("lat"),loc.getDouble("lng"))).title(p.getString("name")))?.`object` = p
        }
    }
    private fun detail(p: JSONObject) {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(8),dp(14),dp(8)) }
        content.addView(text(p.getString("address"))); content.addView(text(p.optString("description"))); content.addView(text("如何前往\n"+p.optString("directions")))
        val images = p.optJSONArray("photos") ?: JSONArray()
        for (i in 0 until images.length()) { val image = ImageView(this).apply { contentDescription = "门店照片"; adjustViewBounds = true }; content.addView(image,LinearLayout.LayoutParams(-1,dp(190))); async { val bytes = api.photo(images.getString(i)); val bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.size); runOnUiThread { image.setImageBitmap(bitmap) } } }
        val clips=p.optJSONArray("videos")?:JSONArray()
        for(i in 0 until clips.length()){content.addView(button("▶ 播放美食视频 ${i+1}"){val video=VideoView(this);val controller=MediaController(this);controller.setAnchorView(video);video.setMediaController(controller);video.setVideoURI(Uri.parse(BuildConfig.API_BASE_URL+clips.getString(i)),mapOf("Authorization" to "Bearer ${store.token.orEmpty()}"));AlertDialog.Builder(this).setView(video).setPositiveButton("关闭"){_,_->video.stopPlayback()}.setOnDismissListener{video.stopPlayback()}.show();video.setOnPreparedListener{video.start()}})}
        AlertDialog.Builder(this).setTitle(p.getString("name")).setView(ScrollView(this).apply { addView(content) }).setNegativeButton("关闭",null).setPositiveButton("高德导航") { _, _ ->
            val loc = p.getJSONObject("location"); open("https://uri.amap.com/navigation?to=${loc.getDouble("lng")},${loc.getDouble("lat")},${Uri.encode(p.getString("name"))}&mode=walk&coordinate=gaode&callnative=1")
        }.show()
    }
    private fun login() {
        if (store.token != null) { store.token = null; loginButton.text = "GitHub 登录"; return }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val verifier = Base64.encodeToString(bytes,Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        prefs.edit().putString("verifier",verifier).apply(); open(BuildConfig.API_BASE_URL+"/auth/github?challenge="+challenge)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleLogin(intent) }
    private fun handleLogin(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "guet-food-share" || uri.host != "login") return
        val code = uri.getQueryParameter("code") ?: return; val verifier = prefs.getString("verifier",null) ?: return
        prefs.edit().remove("verifier").apply()
        async { val result = api.post("/api/mobile/token",JSONObject().put("code",code).put("verifier",verifier)); store.token = result.getString("token"); runOnUiThread { loginButton.text = "退出登录"; toast("登录成功") } }
    }
    private fun open(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) } catch (_: Exception) { toast("没有可打开链接的应用") } }
    private fun field(parent: LinearLayout, title: String, hint: String = "", multiline: Boolean = false): EditText {
        parent.addView(text(title,12f)); return EditText(this).apply { this.hint = hint; textSize = 14f; setSingleLine(!multiline); if (multiline) minLines = 2; parent.addView(this) }
    }
    private fun share() {
        if (store.token == null) { toast("请先登录后投稿"); return }
        if (submitDialog?.isShowing == true) return
        photos.clear(); videos.clear()
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18),0,dp(18),0) }
        val name = field(form,"店名 *"); nameInput=name; nearbyName?.let {name.setText(it)}; val category = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,categories) }; form.addView(text("分类 *",12f)); form.addView(category)
        val address = field(form,"门店地址 *"); addressInput=address; autoAddress?.let {address.setText(it)}; lngInput = field(form,"高德经度 *","例如 110.299121"); latInput = field(form,"高德纬度 *","例如 25.274215")
        selected?.let { lngInput!!.setText(it.longitude.toString()); latInput!!.setText(it.latitude.toString()) }
        form.addView(button("在地图上选点") { if(amap==null)toast("请先启用并配置高德地图") else { picking=true; submitDialog?.hide(); toast("请点击地图上的门店位置") } })
        form.addView(button("◎ 定位并自动填写地址") { locatingForForm=true; locate() })
        val description = field(form,"推荐理由",multiline=true); val directions = field(form,"如何前往",multiline=true)
        photoLabel = text("照片可选，最多 6 张 JPEG / PNG（每张 8 MB）",11f); form.addView(photoLabel)
        form.addView(button("选择门店照片") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*"; putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("image/jpeg","image/png")); putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); addCategory(Intent.CATEGORY_OPENABLE) },100) })
        videoLabel=text("视频可选，最多 2 个 MP4/WebM（每个 50 MB）",11f);form.addView(videoLabel)
        form.addView(button("选择美食视频") {startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {type="video/*";putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("video/mp4","video/webm"));putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addCategory(Intent.CATEGORY_OPENABLE)},101)})
        val consent = CheckBox(this).apply { text="我有权分享文字、图片和视频，允许公开门店位置；文字和坐标进入公开审核 PR，媒体密文存入公开仓库，审核后在应用公开。视频可能含设备信息，请核对后提交。"; textSize=12f }; form.addView(consent)
        submitDialog = AlertDialog.Builder(this).setTitle("分享好味道").setView(ScrollView(this).apply { addView(form) }).setNegativeButton("取消") { _, _ -> picking=false }.setPositiveButton("提交审核",null).create()
        submitDialog!!.setOnShowListener { submitDialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val lng=lngInput!!.text.toString().toDoubleOrNull(); val lat=latInput!!.text.toString().toDoubleOrNull()
            if(name.text.isBlank()||address.text.isBlank()||lng==null||lat==null||!lng.isFinite()||!lat.isFinite()||lng !in 73.0..136.0||lat !in 3.0..54.0||!consent.isChecked){toast("请填写店名、地址、有效高德坐标并确认授权");return@setOnClickListener}
            val data=JSONObject().put("name",name.text.toString()).put("category",categories[category.selectedItemPosition]).put("address",address.text.toString()).put("description",description.text.toString()).put("directions",directions.text.toString()).put("consent",true).put("location",JSONObject().put("lng",lng).put("lat",lat).put("crs","GCJ-02"))
            val submit=submitDialog!!.getButton(AlertDialog.BUTTON_POSITIVE); submit.isEnabled=false; submit.text="提交中…"
            executor.execute { try {
                val uploaded=JSONArray(); photos.toList().forEach { uri -> uploaded.put(api.request("/api/photos","POST",encodePhoto(uri),"image/jpeg").getString("url")) }; data.put("photos",uploaded);val uploadedVideos=JSONArray();videos.toList().forEach {uri->uploadedVideos.put(api.uploadVideo(this,uri).getString("url"))};data.put("videos",uploadedVideos)
                val result=api.post("/api/submissions",data); runOnUiThread { submitDialog?.dismiss(); submitDialog=null; toast(result.getString("message")) }
            } catch(e:Exception) { runOnUiThread { submit.isEnabled=true;submit.text="提交审核";toast(e.message?:"提交失败") } } }
        } }; submitDialog!!.show()
    }
    private fun encodePhoto(uri: Uri): ByteArray {
        val raw=contentResolver.openInputStream(uri)!!.use { input ->
            val output=ByteArrayOutputStream(); val buffer=ByteArray(8192)
            while(true){val n=input.read(buffer);if(n<0)break;require(output.size()+n<=8*1024*1024){"照片不能超过 8 MB"};output.write(buffer,0,n)}
            output.toByteArray()
        }
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }; BitmapFactory.decodeByteArray(raw,0,raw.size,bounds)
        require(bounds.outWidth>0&&bounds.outHeight>0){"照片格式无法识别"}
        var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>2000)sample*=2
        val bitmap=BitmapFactory.decodeByteArray(raw,0,raw.size,BitmapFactory.Options().apply { inSampleSize=sample })?:error("照片解析失败")
        val orientation=runCatching { ExifInterface(ByteArrayInputStream(raw)).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix=Matrix().apply { when(orientation){
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL->setScale(-1f,1f)
            ExifInterface.ORIENTATION_ROTATE_180->setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL->setScale(1f,-1f)
            ExifInterface.ORIENTATION_TRANSPOSE->{setRotate(90f);postScale(-1f,1f)}
            ExifInterface.ORIENTATION_ROTATE_90->setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE->{setRotate(-90f);postScale(-1f,1f)}
            ExifInterface.ORIENTATION_ROTATE_270->setRotate(-90f)
        } }
        val oriented=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
        return try { ByteArrayOutputStream().use { oriented.compress(Bitmap.CompressFormat.JPEG,86,it);it.toByteArray() } } finally { if(oriented!==bitmap)oriented.recycle();bitmap.recycle() }
    }
    @Deprecated("Framework photo picker callback")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data); if((requestCode!=100&&requestCode!=101)||resultCode!=RESULT_OK)return
        val picked=mutableListOf<Uri>(); data?.clipData?.let { clip -> for(i in 0 until clip.itemCount)picked.add(clip.getItemAt(i).uri) } ?: data?.data?.let { picked.add(it) }
        if(requestCode==101){if(picked.size>2){toast("最多选择 2 个视频");return};videos.clear();videos.addAll(picked);videoLabel?.text="已选择 ${videos.size} 个视频"}else{if(picked.size>6){toast("最多选择 6 张照片");return};photos.clear();photos.addAll(picked);photoLabel?.text="已选择 ${photos.size} 张照片"}
    }
    private fun mine() {
        if(store.token==null){toast("请先登录");return}
        async { val rows=api.request("/api/submissions").getJSONArray("submissions");runOnUiThread {
            val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,dp(12),0)}
            if(rows.length()==0)box.addView(text("还没有投稿"))
            for(i in 0 until rows.length()){val s=rows.getJSONObject(i);val status=when(s.getString("status")){"published"->"已公开";"queued"->"整理 / 审核中";else->"已保存 · 待送达"};box.addView(text(s.getString("name")+" · "+status));if(s.getString("status")=="pending")box.addView(button("重新送交 GitHub") { async { api.post("/api/submissions/${s.getString("id")}/retry",JSONObject());runOnUiThread{toast("已送交 GitHub")} } })}
            AlertDialog.Builder(this).setTitle("我的投稿").setView(ScrollView(this).apply{addView(box)}).setPositiveButton("关闭",null).show()
        } }
    }
    private fun locate() {
        if(amap==null){if(!prefs.getBoolean("map_consent",false))privacy(null) else toast("请先配置高德地图");return}
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),200);return}
        stopLocation()
        val manager=getSystemService(LOCATION_SERVICE) as LocationManager;locationManager=manager
        val provider=when {manager.isProviderEnabled(LocationManager.GPS_PROVIDER)&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED->LocationManager.GPS_PROVIDER;manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)->LocationManager.NETWORK_PROVIDER;else->{toast("请开启系统定位，或手动选点");return}}
        val listener=object:LocationListener {
            override fun onLocationChanged(location:Location){stopLocation();val point=CoordinateConverter(this@MainActivity).from(CoordinateConverter.CoordType.GPS).coord(LatLng(location.latitude,location.longitude)).convert();amap?.animateCamera(CameraUpdateFactory.newLatLngZoom(point,17f));selected=point
                if(locatingForForm){locatingForForm=false;lngInput?.setText(point.longitude.toString());latInput?.setText(point.latitude.toString())}else if(store.token!=null){share()}
                if(store.token!=null)resolveAddress(point) else toast("已定位，请登录后自动填写投稿")}
            @Deprecated("Legacy callback") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?){}
            override fun onProviderEnabled(provider:String){}
            override fun onProviderDisabled(provider:String){}
        };locationListener=listener
        try { manager.requestLocationUpdates(provider,0L,0f,listener,Looper.getMainLooper());mainHandler.postDelayed({if(locationListener!=null){stopLocation();toast("定位超时，请手动选点")}},15000) } catch (_: SecurityException){toast("定位权限不可用")}
    }
    private fun resolveAddress(point:LatLng){
        async {try{val data=api.post("/api/location",JSONObject().put("lng",point.longitude).put("lat",point.latitude));val address=data.getString("address");val poi=data.optJSONArray("pois");val candidate=if(poi!=null&&poi.length()>0&&poi.getJSONObject(0).optDouble("distance",999.0)<=80)poi.getJSONObject(0).getString("name")else null
            runOnUiThread{if(selected!=point)return@runOnUiThread;autoAddress=address;nearbyName=candidate;addressInput?.setText(address);if(nameInput?.text.isNullOrBlank()&&candidate!=null)nameInput?.setText(candidate);toast("坐标和地址已填写，请核对实际门店及名称")}
        }catch(e:Exception){runOnUiThread{toast("坐标已填写，地址请手动填写："+e.message)}}}
    }
    private fun stopLocation(){locationListener?.let { locationManager?.removeUpdates(it) };locationListener=null}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==200){if(grantResults.any{it==PackageManager.PERMISSION_GRANTED})locate()else toast("未授予定位权限，仍可手动选点")}}
    override fun onResume(){super.onResume();mapView?.onResume();if(::updater.isInitialized)updater.resumeInstall()}
    override fun onPause(){mapView?.onPause();stopLocation();super.onPause()}
    override fun onSaveInstanceState(outState:Bundle){super.onSaveInstanceState(outState);mapView?.onSaveInstanceState(outState)}
    override fun onDestroy(){mainHandler.removeCallbacksAndMessages(null);stopLocation();mapView?.onDestroy();executor.shutdownNow();if(::updater.isInitialized)updater.close();super.onDestroy()}
}
