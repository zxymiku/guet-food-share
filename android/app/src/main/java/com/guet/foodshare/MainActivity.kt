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
import android.view.Gravity
import android.graphics.Typeface
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
    private val orange = ArkUi.signal
    private val executor = Executors.newFixedThreadPool(3)
    private lateinit var store: SecureStore
    private lateinit var api: Api
    private lateinit var updater: AppUpdater
    private lateinit var feedback: FeedbackController
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
    private var categories = arrayOf("后街", "前街", "餐厅")
    private var categoryConfig=JSONObject("{\"categories\":[{\"name\":\"后街\",\"children\":[]},{\"name\":\"前街\",\"children\":[]},{\"name\":\"餐厅\",\"children\":[\"中餐\",\"小吃\",\"面馆\",\"咖啡\",\"甜品\",\"其他\"]}]}")
    private lateinit var categoryChips:LinearLayout
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var locationListener: LocationListener? = null
    private var locationManager: LocationManager? = null
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(value:String,size:Float=14f)=ArkUi.label(this,value,size)
    private fun button(value:String,action:()->Unit)=ArkUi.button(this,value,action=action)
    private lateinit var mapStatus:TextView
    private lateinit var countLabel:TextView
    private lateinit var directory:LinearLayout
    private lateinit var stage:LinearLayout
    private lateinit var body:LinearLayout
    private lateinit var exploreButton:Button
    private lateinit var directoryButton:Button
    private var wide=false
    private var activePlaceId:String?=null
    private var exportData:String?=null
    private var fetchInProgress=false
    private fun settings(){startActivityForResult(Intent(this,MapSettingsActivity::class.java),301)}
    private fun toast(value: String) { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
    private fun async(action: () -> Unit) { executor.execute { try { action() } catch (e: Exception) { runOnUiThread { if (!isFinishing) toast(e.message ?: "请求失败，请重试") } } } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=ArkUi.ink;window.navigationBarColor=ArkUi.ink
        store = SecureStore(this); api = Api { store.token }; updater = AppUpdater(this,api)
        feedback = FeedbackController(this,api,{store.token!=null},{uri->encodePhoto(uri)})
        root=ArkUi.column(this).apply{setBackgroundColor(ArkUi.ink);tag="${ArkUi.family}/${ArkUi.depth}"}
        root.setOnApplyWindowInsetsListener{v,i->v.setPadding(0,i.systemWindowInsetTop,0,i.systemWindowInsetBottom);i}
        setContentView(root)
        val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(4),dp(12),dp(4))}
        val brand=ArkUi.column(this);brand.addView(ArkUi.kicker(this,"GUET / FOOD ATLAS"));brand.addView(text("桂电美食分享",22f).apply{typeface=Typeface.DEFAULT_BOLD})
        top.addView(brand,LinearLayout.LayoutParams(0,-2,1f));top.addView(button("设置"){settings()});top.addView(button("更多"){menu()});root.addView(top);root.addView(ArkUi.rule(this,true))
        loginButton=button(if(store.token==null)"登录" else if(store.authType=="credential")"退出（密码）" else "退出登录"){login()}
        wide=resources.configuration.screenWidthDp>=720 || resources.configuration.screenWidthDp>resources.configuration.screenHeightDp
        body=LinearLayout(this).apply{orientation=if(wide)LinearLayout.HORIZONTAL else LinearLayout.VERTICAL}
        root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        stage=ArkUi.column(this);body.addView(stage,if(wide)LinearLayout.LayoutParams(0,-1,1.25f) else LinearLayout.LayoutParams(-1,0,1.25f))
        mapStatus=ArkUi.label(this,"地图未启用 · 公开地点可正常浏览",13f,ArkUi.muted).apply{accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE};stage.addView(mapStatus)
        mapHost=FrameLayout(this);stage.addView(mapHost,LinearLayout.LayoutParams(-1,0,1f))
        val mapActions=LinearLayout(this)
        mapActions.addView(button("◎ 定位填写"){locate()},LinearLayout.LayoutParams(0,-2,1f))
        mapActions.addView(button("＋ 选点投稿"){if(amap==null)settings() else {share();if(submitDialog!=null){picking=true;submitDialog?.hide();mapStatus.text="请点击实际门店位置 · 返回表单核对"}}},LinearLayout.LayoutParams(0,-2,1f))
        stage.addView(mapActions)
        directory=ArkUi.column(this,12);body.addView(directory,if(wide)LinearLayout.LayoutParams(0,-1,1f) else LinearLayout.LayoutParams(-1,0,1f))
        val search=ArkUi.field(this,"搜索店名、地址、推荐理由").apply{contentDescription="搜索公开地点";addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){searchQuery=s.toString();renderPlaces()}
            override fun afterTextChanged(s:android.text.Editable?){}
        })};directory.addView(search)
        categoryChips=LinearLayout(this)
        directory.addView(HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(categoryChips)})
        renderCategoryChips()
        countLabel=ArkUi.kicker(this,"公开地点 · 正在读取");directory.addView(countLabel)
        list=ArkUi.column(this);directory.addView(ScrollView(this).apply{addView(list)},LinearLayout.LayoutParams(-1,0,1f))
        val dock=LinearLayout(this).apply{setPadding(dp(10),dp(8),dp(10),dp(8))}
        exploreButton=button("探索地图"){setMode("map")};directoryButton=button("地点档案"){setMode("list")}
        dock.addView(exploreButton,LinearLayout.LayoutParams(0,-2,1f));dock.addView(directoryButton,LinearLayout.LayoutParams(0,-2,1f));dock.addView(ArkUi.button(this,"＋ 分享",true){share()},LinearLayout.LayoutParams(0,-2,1f));root.addView(ArkUi.rule(this));root.addView(dock)
        showMapEmpty();setMode("map")
        if(prefs.getBoolean("map_consent",false)&&!store.amapAndroidKey.isNullOrBlank())initMap(savedInstanceState)
        refresh();handleLogin(intent);updater.check();ArkUi.reveal(body)
        if(!prefs.getBoolean("guide_v1",false))guide()
    }
    private fun setMode(mode:String){
        stage.visibility=if(wide||mode=="map")View.VISIBLE else View.GONE
        directory.visibility=if(wide||mode=="list"||resources.configuration.screenHeightDp>=780)View.VISIBLE else View.GONE
        exploreButton.isSelected=mode=="map";directoryButton.isSelected=mode=="list"
        for(button in arrayOf(exploreButton,directoryButton)){button.background=ArkUi.surface(if(button.isSelected)ArkUi.paper else ArkUi.panel);button.setTextColor(if(button.isSelected)ArkUi.ink else ArkUi.paper)}
        ArkUi.reveal(if(mode=="list")directory else stage)
    }
    private fun menu(){
        AlertDialog.Builder(this).setTitle("桂电美食分享").setItems(arrayOf(loginButton.text.toString(),"我的投稿","刷新公开地点","导出全部公开地点","意见反馈","设置与 API 教程","使用指引")){_,which->when(which){0->login();1->mine();2->refresh();3->exportPlaces();4->feedback.show();5->settings();6->guide()}}.setNegativeButton("返回",null).show()
    }
    private fun showMapEmpty(){
        mapHost.removeAllViews();mapHost.addView(AtlasStage(this),FrameLayout.LayoutParams(-1,-1))
        val content=ArkUi.column(this,24)
        content.addView(ArkUi.kicker(this,"地图 / 等待你的 API"))
        content.addView(text("把好吃的，\n留在地图上。",30f).apply{typeface=Typeface.DEFAULT_BOLD})
        content.addView(ArkUi.label(this,"自己的地图 API，大家的美食档案。",14f,ArkUi.muted))
        content.addView(ArkUi.button(this,"申请并填写高德 API →",true){settings()})
        content.addView(ArkUi.label(this,"未配置地图时，仍可浏览地点档案。",13f,ArkUi.muted))
        mapHost.addView(ScrollView(this).apply{isFillViewport=true;addView(content)},FrameLayout.LayoutParams(-1,-1))
        mapStatus.text="地图未启用 · 个人 API 仅保存在本机"
    }
    private fun initMap(bundle: Bundle?) {
        if (mapView != null) return
        val key=store.amapAndroidKey
        if(key.isNullOrBlank()){showMapEmpty();return}
        try {
        MapsInitializer.setApiKey(key)
        MapsInitializer.updatePrivacyShow(this,true,true); MapsInitializer.updatePrivacyAgree(this,true)
        mapView = MapView(this).also { it.onCreate(bundle); mapHost.removeAllViews(); mapHost.addView(it) }
        amap = mapView!!.map.apply {
            moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(25.274215,110.299121),13f))
            setOnMapClickListener { point ->
                selected = point;autoAddress=null;nearbyName=null; selectedMarker?.remove(); selectedMarker = addMarker(MarkerOptions().position(point).title("待投稿门店位置"))
                if (picking) { picking = false;mapStatus.text="已选择门店 · 请核对后提交"; resolveAddress(point); lngInput?.setText("%.6f".format(java.util.Locale.US,point.longitude)); latInput?.setText("%.6f".format(java.util.Locale.US,point.latitude)); submitDialog?.show() }
                else toast("已选点，点击“分享”填写门店信息")
            }
            setOnMarkerClickListener { marker -> (marker.`object` as? JSONObject)?.let { detail(it); true } ?: false }
        }
        amap?.uiSettings?.isZoomControlsEnabled=false
        amap?.uiSettings?.isScaleControlsEnabled=true
        mapStatus.text="个人 API · 正在连接高德"
        var loaded=false
        amap?.setOnMapLoadedListener{loaded=true;mapStatus.text="高德地图 · GCJ-02 · 个人 API"}
        val currentMap=mapView
        mainHandler.postDelayed({if(!loaded&&mapView===currentMap&&!isDestroyed)mapStatus.text="地图未就绪 · 检查网络或地图 API 设置"},20000)
        renderPlaces()
        }catch(_:Exception){mapView?.onDestroy();mapView=null;amap=null;showMapEmpty();mapStatus.text="地图初始化失败 · 请检查地图 API 设置"}
    }
    private fun readPlaces():List<JSONObject>{
        val result=linkedMapOf<String,JSONObject>();val seen=mutableSetOf<String>();var cursor=""
        do{val page=api.request("/api/places?limit=200"+if(cursor.isEmpty())"" else "&cursor="+Uri.encode(cursor));val rows=page.getJSONArray("places")
            for(i in 0 until rows.length()){val p=rows.getJSONObject(i);result[p.getString("id")]=p}
            cursor=if(page.isNull("nextCursor"))"" else page.optString("nextCursor","")
            check(cursor.isEmpty()||seen.add(cursor)){"地点分页异常，请稍后重试"}
        }while(cursor.isNotEmpty())
        return result.values.toList()
    }
    private fun refresh(){
        if(fetchInProgress)return;fetchInProgress=true;countLabel.text="公开地点 · 正在读取"
        executor.execute{try{val config=runCatching{api.request("/api/categories")}.getOrNull();val data=readPlaces();runOnUiThread{if(config!=null)applyCategoryConfig(config);places=data;fetchInProgress=false;renderPlaces()}}
        catch(e:Exception){runOnUiThread{fetchInProgress=false;countLabel.text="读取失败 · 更多 → 刷新公开地点";toast(e.message?:"读取失败")}}}
    }
    private fun renderPlaces(){
        if(!::list.isInitialized)return
        list.removeAllViews();amap?.clear();selectedMarker=null
        val visible=places.filter{(categoryFilter=="全部"||it.getString("category")==categoryFilter)&&(it.getString("name")+it.getString("address")+it.optString("description")).contains(searchQuery,true)}
        countLabel.text="地点档案 / ${visible.size} 个匹配 · 共 ${places.size} 个公开"
        if(visible.isEmpty()){
            list.addView(ArkUi.label(this,if(places.isEmpty())"等你发现第一处好味道。" else "没有匹配的地点。",20f))
            list.addView(ArkUi.label(this,if(places.isEmpty())"分享真实门店，审核后每个人都能在自己的地图上看到。" else "试试其他分类或关键词。",14f,ArkUi.muted))
        }
        visible.forEachIndexed{index,p->
            val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(10),0,dp(10));background=ArkUi.surface(if(p.optString("id")==activePlaceId)ArkUi.line else ArkUi.ink,ArkUi.ink)}
            row.addView(ArkUi.label(this,"%02d".format(index+1),24f,ArkUi.signal).apply{typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)})
            val copy=ArkUi.column(this);copy.addView(ArkUi.kicker(this,p.getString("category")));copy.addView(text(p.getString("name"),18f).apply{typeface=Typeface.DEFAULT_BOLD});copy.addView(ArkUi.label(this,p.getString("address"),14f,ArkUi.muted))
            row.addView(copy,LinearLayout.LayoutParams(0,-2,1f));row.addView(text("↗",22f));row.isClickable=true;row.isFocusable=true;row.setOnFocusChangeListener{v,focused->v.background=ArkUi.surface(if(focused)ArkUi.line else ArkUi.ink,if(focused)ArkUi.signal else ArkUi.ink)};row.contentDescription="${p.getString("name")}，${p.getString("category")}，${p.getString("address")}，查看详情";row.setOnClickListener{detail(p)}
            list.addView(row);list.addView(ArkUi.rule(this))
            val loc=p.getJSONObject("location");amap?.addMarker(MarkerOptions().position(LatLng(loc.getDouble("lat"),loc.getDouble("lng"))).title(p.getString("name")).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)))?.`object`=p
        }
        selected?.let{point->selectedMarker=amap?.addMarker(MarkerOptions().position(point).title("待投稿门店位置"))}
    }
    private fun exportPlaces(){
        toast("正在整理全部公开地点…")
        async{val all=readPlaces();val archive=JSONObject().put("format","guet-food-places").put("schemaVersion",1).put("exportedAt",java.time.Instant.now().toString()).put("source",BuildConfig.API_BASE_URL).put("mediaBaseUrl",BuildConfig.API_BASE_URL).put("crs","GCJ-02").put("places",JSONArray(all))
            runOnUiThread{exportData=archive.toString(2);startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,"guet-food-places.json")},302)}
        }
    }
    private fun detail(p: JSONObject) {
        activePlaceId=p.optString("id");renderPlaces()
        val point=p.getJSONObject("location");amap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.getDouble("lat"),point.getDouble("lng")),16f))
        val content=ArkUi.column(this,16)
        content.addView(ArkUi.section(this,"地点档案 / ${p.getString("category")}",p.getString("name"),"由同学分享 · 已公开"))
        content.addView(ArkUi.label(this,"${point.getDouble("lng")}, ${point.getDouble("lat")} · GCJ-02",14f,ArkUi.signal).apply{setTextIsSelectable(true)})
        if(!p.isNull("publishedAt"))content.addView(ArkUi.label(this,"公开时间："+p.optString("publishedAt"),13f,ArkUi.muted))
        content.addView(text(p.getString("address"))); content.addView(text(p.optString("description"))); content.addView(text("如何前往\n"+p.optString("directions")))
        for((key,label) in listOf("subcategory" to "子分类","pros" to "优点","cons" to "缺点","menuPrice" to "菜单与价格")){if(p.optString(key).isNotBlank()){content.addView(ArkUi.kicker(this,label));content.addView(text(p.optString(key)))}}
        val reviewsBox=ArkUi.column(this);content.addView(reviewsBox);loadReviews(p.getString("id"),reviewsBox)
        val images = p.optJSONArray("photos") ?: JSONArray()
        for (i in 0 until images.length()) { val image = ImageView(this).apply { contentDescription = "门店照片"; adjustViewBounds = true }; content.addView(image,LinearLayout.LayoutParams(-1,dp(190))); async { val bytes = api.photo(images.getString(i)); val bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.size); runOnUiThread { image.setImageBitmap(bitmap) } } }
        val clips=p.optJSONArray("videos")?:JSONArray()
        for(i in 0 until clips.length()){content.addView(button("▶ 播放美食视频 ${i+1}"){val video=VideoView(this);val controller=MediaController(this);controller.setAnchorView(video);video.setMediaController(controller);video.setVideoURI(Uri.parse(BuildConfig.API_BASE_URL+clips.getString(i)),mapOf("Authorization" to "Bearer ${store.token.orEmpty()}"));AlertDialog.Builder(this).setView(video).setPositiveButton("关闭"){_,_->video.stopPlayback()}.setOnDismissListener{video.stopPlayback()}.show();video.setOnPreparedListener{video.start()}})}
        AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(content) }).setNegativeButton("关闭",null).setPositiveButton("高德导航") { _, _ ->
            val loc = p.getJSONObject("location"); open("https://uri.amap.com/navigation?to=${loc.getDouble("lng")},${loc.getDouble("lat")},${Uri.encode(p.getString("name"))}&mode=walk&coordinate=gaode&callnative=1")
        }.show()
    }
    private fun renderCategoryChips(){
        categoryChips.removeAllViews()
        (arrayOf("全部")+categories).forEach{c->categoryChips.addView(ArkUi.button(this,c,c==categoryFilter){categoryFilter=c;renderCategoryChips();renderPlaces()},LinearLayout.LayoutParams(-2,dp(48)))}
    }
    private fun applyCategoryConfig(config:JSONObject){
        runCatching{val rows=config.getJSONArray("categories");require(rows.length() in 1..20);val names=(0 until rows.length()).map{val c=rows.getJSONObject(it);val name=c.getString("name");require(name.isNotBlank()&&name.length<=30);val children=c.getJSONArray("children");require(children.length()<=40);for(j in 0 until children.length())require(children.getString(j).length in 1..30);name};require(names.toSet().size==names.size);categoryConfig=config;categories=names.toTypedArray();if(categoryFilter!in categories)categoryFilter="全部";renderCategoryChips()}
    }
    private fun guide(){
        val box=ArkUi.column(this,20);box.addView(ArkUi.section(this,"START / 使用指引","自己的地图，大家的美食","指引可跳过，更多菜单可随时重开"))
        listOf("01 上传：登录后分享，填写店名、分类、地址、GCJ-02 坐标和公开授权，人工审核后公开；媒体、优缺点、菜单价格选填。","02 删除：更多 → 我的投稿 → 删除自己的投稿。公共地点撤回，GitHub 历史和他人副本需另行联系管理员处理。","03 反馈：更多 → 意见反馈，支持手动 Issue 与自动反馈，先遮挡个人隐私。","04 查看他人内容：地点档案可搜索筛选，地图标记与列表打开相同详情，未配置 API 也可查看列表。","05 设置 API：顶部设置，按离线 README 教程申请 Android Key 并填写；个人大量使用可能缴费，Key 加密保存在本机，不进公开数据，源码欢迎审查。","06 评分：详情选 1～5 分，文字可选；北京时间每天，同一 IP 或随机设备标识对一个地点一次，可评多个地点。服务端只存每日 HMAC，不采集硬件指纹。共享网络可能误限，清数据并换网络可绕过，无法绝对刷票。").forEach{box.addView(text(it))}
        AlertDialog.Builder(this).setView(ScrollView(this).apply{addView(box)}).setPositiveButton("知道了 / 跳过",null).setOnDismissListener{prefs.edit().putBoolean("guide_v1",true).apply()}.show()
    }
    private fun loadReviews(id:String,box:LinearLayout,before:String="",append:Boolean=false){
        async{val data=api.request("/api/places/$id/reviews"+if(before.isEmpty())"" else "?before="+Uri.encode(before));runOnUiThread{
            if(!append){box.removeAllViews();box.addView(ArkUi.section(this,"评价 / PUBLIC REVIEWS",if(data.optInt("count")==0)"暂无评分" else "%.1f / 5".format(data.optDouble("average")),"${data.optInt("count")} 条评价"))}
            val rows=data.getJSONArray("reviews");for(i in 0 until rows.length()){val r=rows.getJSONObject(i);box.addView(text("${r.getInt("score")} / 5 · ${r.optString("comment").ifBlank{"仅评分"}} · ${r.getString("created_at")}"))}
            if(!data.isNull("nextCursor"))box.addView(button("加载更多评价"){loadReviews(id,box,data.getString("nextCursor"),true)})
            if(!append){val score=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("5 分","4 分","3 分","2 分","1 分"))};box.addView(score);val comment=field(box,"评价（选填，最多 1000 字）",multiline=true);comment.filters=arrayOf(android.text.InputFilter.LengthFilter(1000));box.addView(text("评价将公开，同一 IP 或设备每日限一次。",12f));val submit=button("提交评价"){if(store.token==null){toast("请先登录")}else{var device=prefs.getString("review_device",null);if(device==null){device=java.util.UUID.randomUUID().toString();prefs.edit().putString("review_device",device).apply()};val payload=JSONObject().put("score",5-score.selectedItemPosition).put("comment",comment.text.toString()).put("deviceId",device);async{api.post("/api/places/$id/reviews",payload);runOnUiThread{toast("评价已公开");loadReviews(id,box)}}}};box.addView(submit)}
        }}
    }
    private fun login() {
        if(store.token!=null){store.token=null;store.authType=null;loginButton.text="登录";return}
        AlertDialog.Builder(this).setTitle("选择登录方式").setItems(arrayOf("密码凭证登录（无需 GitHub）","GitHub 登录")){_,which->if(which==0)passwordLogin()else githubLogin()}.setNegativeButton("取消",null).show()
    }
    private fun passwordLogin(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(8))}
        box.addView(text("输入管理员提供的共享密码，可查看、上传美食并提交反馈；公开美食仍需审核，可在我的投稿删除自己的内容，不能审核。",12f))
        val password=ArkUi.field(this,"共享密码（12～128 字符）",true).apply{inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;filters=arrayOf(android.text.InputFilter.LengthFilter(128));box.addView(this)}
        val error=TextView(this).apply{setTextColor(orange);textSize=12f;box.addView(this)}
        val dialog=AlertDialog.Builder(this).setTitle("密码凭证登录").setView(box).setNegativeButton("取消",null).setPositiveButton("登录",null).create()
        dialog.setOnDismissListener{password.text.clear()}
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            val value=password.text.toString();if(value.length<12){error.text="密码至少 12 字符";return@setOnClickListener}
            val submit=dialog.getButton(AlertDialog.BUTTON_POSITIVE);submit.isEnabled=false;password.isEnabled=false;error.text=""
            executor.execute{try{
                val data=JSONObject().put("password",value).put("client","android");store.credentialDevice?.let{data.put("deviceToken",it)}
                val result=api.post("/api/auth/password",data);store.token=result.getString("token");store.credentialDevice=result.getString("deviceToken");store.authType="credential"
                runOnUiThread{if(!isFinishing&&!isDestroyed){dialog.dismiss();loginButton.text="退出（密码）";toast("已通过密码登录，可查看和上传美食")}}
            }catch(e:Exception){runOnUiThread{if(!isFinishing&&!isDestroyed){submit.isEnabled=true;password.isEnabled=true;error.text=e.message?:"登录失败，请重试"}}}}
        }};dialog.show()
    }
    private fun githubLogin() {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val verifier = Base64.encodeToString(bytes,Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        prefs.edit().putString("verifier",verifier).apply(); open(BuildConfig.API_BASE_URL+"/auth/github?challenge="+challenge)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleLogin(intent) }
    private fun handleLogin(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "guetfood" || uri.host != "login") return
        val code = uri.getQueryParameter("code") ?: return; val verifier = prefs.getString("verifier",null) ?: return
        prefs.edit().remove("verifier").apply()
        async { val result = api.post("/api/mobile/token",JSONObject().put("code",code).put("verifier",verifier)); store.token = result.getString("token"); store.authType="github"; runOnUiThread { loginButton.text = "退出登录"; toast("登录成功") } }
    }
    private fun open(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) } catch (_: Exception) { toast("没有可打开链接的应用") } }
    private fun field(parent: LinearLayout, title: String, hint: String = "", multiline: Boolean = false): EditText {
        parent.addView(text(title,14f)); return ArkUi.field(this,hint).apply { setSingleLine(!multiline); if (multiline) minLines = 3; parent.addView(this) }
    }
    private fun share() {
        if (store.token == null) { toast("请先登录后投稿"); return }
        if (submitDialog != null) { picking=false;submitDialog?.show();return }
        photos.clear(); videos.clear()
        val form = ArkUi.column(this,18)
        form.addView(ArkUi.section(this,"01 / 门店档案","分享好味道","坐标与内容一起保存，其他人用自己的 API 也能看到。"))
        val name = field(form,"店名 *"); nameInput=name; nearbyName?.let {name.setText(it)}; val category = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,categories) }; form.addView(text("分类 *",12f)); form.addView(category)
        val subcategory=Spinner(this);form.addView(text("餐厅子分类（选填）"));form.addView(subcategory)
        fun updateChildren(){val rows=categoryConfig.getJSONArray("categories");var children=listOf("不指定");for(i in 0 until rows.length()){val c=rows.getJSONObject(i);if(c.getString("name")==category.selectedItem.toString()){val a=c.getJSONArray("children");children=children+(0 until a.length()).map{a.getString(it)}}};subcategory.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,children)}
        category.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){updateChildren()};override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}}
        updateChildren()
        form.addView(ArkUi.section(this,"02 / 地图位置","把地点标清楚","高德 GCJ-02 坐标 · 请核对实际门店"))
        val address = field(form,"门店地址 *"); addressInput=address; autoAddress?.let {address.setText(it)}; lngInput = field(form,"高德经度 *","例如 110.299121"); latInput = field(form,"高德纬度 *","例如 25.274215")
        selected?.let { lngInput!!.setText(it.longitude.toString()); latInput!!.setText(it.latitude.toString()) }
        form.addView(button("在地图上选点") { if(amap==null)toast("请先启用并配置高德地图") else { picking=true; setMode("map");submitDialog?.hide(); toast("请点击地图上的门店位置") } })
        form.addView(button("◎ 定位并自动填写地址") { locatingForForm=true; locate() })
        val description = field(form,"推荐理由（选填）",multiline=true); val directions = field(form,"如何前往（选填）",multiline=true)
        val pros=field(form,"优点（选填）",multiline=true);val cons=field(form,"缺点（选填）",multiline=true);val menuPrice=field(form,"菜单价格（选填）",multiline=true)
        form.addView(ArkUi.section(this,"03 / 图像与授权","留下现场的味道","媒体选填 · 审核通过后公开"))
        photoLabel = text("照片可选，最多 6 张 JPEG / PNG（每张 8 MB）",14f); form.addView(photoLabel)
        form.addView(button("选择门店照片") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*"; putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("image/jpeg","image/png")); putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); addCategory(Intent.CATEGORY_OPENABLE) },100) })
        videoLabel=text("视频可选，最多 2 个 MP4/WebM（每个 50 MB）",14f);form.addView(videoLabel)
        form.addView(button("选择美食视频") {startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {type="video/*";putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("video/mp4","video/webm"));putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addCategory(Intent.CATEGORY_OPENABLE)},101)})
        val consent = CheckBox(this).apply { text="我有权分享文字、图片和视频，允许公开门店位置；文字和坐标进入公开审核 PR，媒体密文存入公开仓库，审核后在应用公开。视频可能含设备信息，请核对后提交。"; textSize=14f }; form.addView(consent)
        submitDialog = AlertDialog.Builder(this).setTitle("分享好味道").setView(ScrollView(this).apply { addView(form) }).setNegativeButton("取消") { _, _ -> picking=false }.setPositiveButton("提交审核",null).create()
        submitDialog!!.setOnShowListener { submitDialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val lng=lngInput!!.text.toString().toDoubleOrNull(); val lat=latInput!!.text.toString().toDoubleOrNull()
            if(name.text.isBlank()||address.text.isBlank()||lng==null||lat==null||!lng.isFinite()||!lat.isFinite()||lng !in 73.0..136.0||lat !in 3.0..54.0||!consent.isChecked){toast("请填写店名、地址、有效高德坐标并确认授权");return@setOnClickListener}
            val data=JSONObject().put("name",name.text.toString()).put("category",categories[category.selectedItemPosition]).put("subcategory",if(subcategory.selectedItemPosition>0)subcategory.selectedItem.toString() else "").put("pros",pros.text.toString()).put("cons",cons.text.toString()).put("menuPrice",menuPrice.text.toString()).put("address",address.text.toString()).put("description",description.text.toString()).put("directions",directions.text.toString()).put("consent",true).put("location",JSONObject().put("lng",lng).put("lat",lat).put("crs","GCJ-02"))
            val submit=submitDialog!!.getButton(AlertDialog.BUTTON_POSITIVE); submit.isEnabled=false; submit.text="提交中…"
            executor.execute { try {
                val uploaded=JSONArray(); photos.toList().forEach { uri -> uploaded.put(api.request("/api/photos","POST",encodePhoto(uri),"image/jpeg").getString("url")) }; data.put("photos",uploaded);val uploadedVideos=JSONArray();videos.toList().forEach {uri->uploadedVideos.put(api.uploadVideo(this,uri).getString("url"))};data.put("videos",uploadedVideos)
                val result=api.post("/api/submissions",data); runOnUiThread { submitDialog?.dismiss(); submitDialog=null; toast(result.getString("message")) }
            } catch(e:Exception) { runOnUiThread { submit.isEnabled=true;submit.text="提交审核";toast(e.message?:"提交失败") } } }
        } }; submitDialog!!.setOnDismissListener{submitDialog=null;picking=false;addressInput=null;nameInput=null;lngInput=null;latInput=null};submitDialog!!.show()
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
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==301&&resultCode==RESULT_OK){
            mapView?.onPause();mapView?.onDestroy();mapView=null;amap=null
            showMapEmpty();if(prefs.getBoolean("map_consent",false)&&!store.amapAndroidKey.isNullOrBlank()){initMap(null)};return
        }
        if(requestCode==302){val archive=exportData;exportData=null;if(resultCode==RESULT_OK&&data?.data!=null&&archive!=null){val uri=data.data!!;async{contentResolver.openOutputStream(uri)!!.bufferedWriter().use{it.write(archive)};runOnUiThread{toast("已导出完整公开地点与媒体引用，不含个人 API")}}};return}
        if(requestCode==FeedbackController.PICK_SCREENSHOTS){feedback.onPicked(resultCode,data);return}; if((requestCode!=100&&requestCode!=101)||resultCode!=RESULT_OK)return
        val picked=mutableListOf<Uri>(); data?.clipData?.let { clip -> for(i in 0 until clip.itemCount)picked.add(clip.getItemAt(i).uri) } ?: data?.data?.let { picked.add(it) }
        if(requestCode==101){if(picked.size>2){toast("最多选择 2 个视频");return};videos.clear();videos.addAll(picked);videoLabel?.text="已选择 ${videos.size} 个视频"}else{if(picked.size>6){toast("最多选择 6 张照片");return};photos.clear();photos.addAll(picked);photoLabel?.text="已选择 ${photos.size} 张照片"}
    }
    private fun mine() {
        if(store.token==null){toast("请先登录");return}
        async { val rows=api.request("/api/submissions").getJSONArray("submissions");runOnUiThread {
            val box=ArkUi.column(this,16);box.addView(ArkUi.section(this,"投稿 / 处理进度","我的美食档案","提交 → 整理 → 人工审核 → 地图公开"))
            if(rows.length()==0)box.addView(text("还没有投稿"))
            for(i in 0 until rows.length()){val s=rows.getJSONObject(i);val status=when(s.getString("status")){"published"->"已公开";"queued"->"整理 / 审核中";else->"已保存 · 待送达"};box.addView(text(s.getString("name")+" · "+status));box.addView(button("删除自己的投稿"){AlertDialog.Builder(this).setTitle("确认撤回？").setMessage("公共地点将撤回，GitHub 历史与他人副本不会自动清除。需要清理历史可反馈管理员。").setNegativeButton("取消",null).setPositiveButton("删除"){_,_->async{api.request("/api/submissions/${s.getString("id")}","DELETE");runOnUiThread{toast("已撤回");refresh()}}}.show()});if(s.getString("status")=="pending")box.addView(button("重新送交 GitHub") { async { api.post("/api/submissions/${s.getString("id")}/retry",JSONObject());runOnUiThread{toast("已送交 GitHub")} } })}
            AlertDialog.Builder(this).setTitle("我的投稿").setView(ScrollView(this).apply{addView(box)}).setPositiveButton("关闭",null).show()
        } }
    }
    private fun locate() {
        if(amap==null){settings();return}
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
        val key=store.amapRestKey
        autoAddress=null;nearbyName=null;addressInput?.setText("")
        if(key.isNullOrBlank()){toast("坐标已填写。可手动填写地址，或在地图 API 设置中添加 Web 服务 Key。");return}
        async {try{val data=PersonalGeocoder.lookup(key,point.longitude,point.latitude);val address=data.getString("address");val poi=data.optJSONArray("pois");val candidate=if(poi!=null&&poi.length()>0&&poi.getJSONObject(0).optDouble("distance",999.0)<=80)poi.getJSONObject(0).getString("name")else null
            runOnUiThread{if(selected!=point)return@runOnUiThread;val currentLng=lngInput?.text?.toString()?.toDoubleOrNull();val currentLat=latInput?.text?.toString()?.toDoubleOrNull();if(currentLng!=null&&currentLat!=null&&(kotlin.math.abs(currentLng-point.longitude)>0.000001||kotlin.math.abs(currentLat-point.latitude)>0.000001))return@runOnUiThread;autoAddress=address;nearbyName=candidate;addressInput?.setText(address);if(nameInput?.text.isNullOrBlank()&&candidate!=null)nameInput?.setText(candidate);toast("坐标和地址已填写，请核对实际门店及名称")}
        }catch(e:Exception){runOnUiThread{toast("坐标已填写，地址请手动填写："+e.message)}}}
    }
    private fun stopLocation(){locationListener?.let { locationManager?.removeUpdates(it) };locationListener=null}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==200){if(grantResults.any{it==PackageManager.PERMISSION_GRANTED})locate()else toast("未授予定位权限，仍可手动选点")}}
    @Deprecated("Framework back navigation")
    override fun onBackPressed(){if(picking&&submitDialog!=null){picking=false;submitDialog?.show();mapStatus.text="高德地图 · 个人 API"}else super.onBackPressed()}
    override fun onResume(){super.onResume();mapView?.onResume();if(::updater.isInitialized)updater.resumeInstall()}
    override fun onPause(){mapView?.onPause();stopLocation();super.onPause()}
    override fun onSaveInstanceState(outState:Bundle){super.onSaveInstanceState(outState);mapView?.onSaveInstanceState(outState)}
    override fun onDestroy(){mainHandler.removeCallbacksAndMessages(null);stopLocation();mapView?.onDestroy();executor.shutdownNow();if(::updater.isInitialized)updater.close();if(::feedback.isInitialized)feedback.close();super.onDestroy()}
}
