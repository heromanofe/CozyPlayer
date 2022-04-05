package com.example.cozyplayer

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Process.myUid
import android.util.Base64
import android.util.Rational
import android.view.View
import android.webkit.CookieManager
import android.webkit.CookieManager.getInstance
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.angusmorton.brotli.BrotliResponseInterceptor
import com.example.cozyplayer.databinding.ActivityMainBinding
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.websocket.*
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.notifications.InitialResults
import io.realm.notifications.UpdatedResults
import io.realm.query
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okhttp3.internal.http.RealResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.GzipSource
import okio.buffer
import org.conscrypt.Conscrypt
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.Security
import java.util.concurrent.TimeUnit

val headerOriginal = hashMapOf(
    "Accept" to "application/json; charset=utf-8",
    "Accept-Encoding" to "gzip, deflate, br",
    "Accept-Language" to "en-US,en;q=0.5",
    "Alt-Used" to "api.cozy.tv",
    "Connection" to "keep-alive",
    "DNT" to "1",
    "Host" to "api.cozy.tv",
    "Sec-Fetch-Dest" to "document",
    "Sec-Fetch-Mode" to "navigate",
    "Sec-Fetch-Site" to "cross-site",
    "TE" to "trailers",
    "Upgrade-Insecure-Requests" to "1",
    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:98.0) Gecko/20100101 Firefox/98.0",
)

@ExperimentalSerializationApi
val serializer = (Json {
    isLenient = true
    ignoreUnknownKeys = true
    explicitNulls = false
})

object Global {
    var homepage: Array<CozyStreamer>? = null
    var currentStreamer:CozyStreamer? = null
    var avatars = mutableMapOf<String,Bitmap?>()
    var timeCurrent = 0
    var lastRememer = 0
    var cookies:String? = null
    var me:CozyUserProfile? = null
}
@Serializable
data class  CozyChatBNChat(val uid:String, val message: String, val displayName: String,val photo: String?,val meta:String?,val mid:String,val role:String?)
@Serializable
data class CozyStatus(val type:String, val viewers: Int?,val channel: String?,val chats:Array<CozyChatBNChat>?)
@Serializable
data class CozyUserRole(val channel:String, val role:String?)
@Serializable
data class CozyUserFollowing(val channel:String, val isFollowing:Boolean?,val role:String?)
@Serializable
data class CozyUserProfile(
        val _id: String, val displayName:String, val isAdmin:Boolean?, val tele_photo_url:String?,val longestStreak:Int?,
        val streak:Int?, val roles:Array<CozyUserRole>?,val followings:Array<CozyUserFollowing>?
        )
@Serializable
data class CozyReplayInfo(val avatarUrl: String, val cdns: Array<String>)
@Serializable
data class CozyReplayRep(val _id:String, val date:String, val duration: Int, var watchl:Boolean?,
                         val id:String, val title:String, val user:String, val peakViewers:Int, var timeLast:Int = 0)
@Serializable
data class CozyReplayReq(val info: CozyReplayInfo, val replays:Array<CozyReplayRep>)
@Serializable
data class RepT(var time:Int, var watch:Int,var url:String,var watchLater:Boolean)
@Serializable
data class CozyStreamer(
    val name:String, val displayName:String, val avatarUrl: String,
    val cardUrl:String, val title: String, val followerCount:Int,
    val isLive:String?, val vf:Boolean?, var viewers:Int?, val new:Boolean?,
    val is247:Boolean?, var replayTimes: MutableMap<String, RepT> = mutableMapOf()
    )

data class ImgUserClass(val bitmap: Bitmap?, val mutReplays: MutableMap<String, RepT> = mutableMapOf())

@DelicateCoroutinesApi
class MainActivity : AppCompatActivity() {
    var allMapView = mutableMapOf<String,CozyStreamerCircle>()
    var openedSocked = false
    var newOne:Int = 0

    val config = RealmConfiguration.Builder(schema = setOf(RCozyStreamer::class))
        .name("CozyDatabase")
        .deleteRealmIfMigrationNeeded()
        .build()
    var openedNav = false
    val imageCache = mutableMapOf<String, ImgUserClass>()
    var realmLoading = true
    val playerFrag = PlayerFragment()
    val homepage = HomepageFragment()
    val cookiesPref = "cookiesPref"
    var allDone = true
    lateinit var binding: ActivityMainBinding
    var initilizingNew = true
    lateinit var queue:RequestQueue
    private lateinit var actionBarToggle: ActionBarDrawerToggle
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
        GlobalScope.launch {
            setupWebsocket()
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        val sharedPref = getPreferences(MODE_PRIVATE)
        Global.cookies = sharedPref.getString(cookiesPref, null)
        if(Global.cookies != null) GlobalScope.launch { setWho() }
        binding.WebViewForLoggingIn.visibility = View.GONE
        binding.GoBack.visibility = View.GONE
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        initilizingNew = true
        Global.lastRememer = Global.timeCurrent
        GlobalScope.launch {
            initilizeRealm()
        }
        supportActionBar?.title = "HomePage"
        queue = Volley.newRequestQueue(this)
        if (savedInstanceState != null) {

            println()
            // restore your state here
        }
        supportFragmentManager.commit {
            this.replace(R.id.fragmentContainerView, playerFrag,"playerFrag")
            this.replace(R.id.fragmentContainerView, homepage,"homepage")
        }

        binding.navigation.bringToFront();
        binding.navigation.isVerticalScrollBarEnabled = true;
        GlobalScope.launch {
        val res = resultHttpGet("https://api.cozy.tv/cache/homepage")
            if(res == null) alertUser("ERROR HAPPENED DURING HOMEPAGE RETRIEVING, CHECK YOUR INTERNET CONNECTION!")
            val nHome = res.toJsonObj()?.opt("users").toString()
            val homepage = nHome.getClassJson<Array<CozyStreamer>>()
            if (homepage != null) {
                Global.homepage = homepage.sortedBy { it.isLive == null }.toTypedArray()
            }
            allDone = true
            Global.avatars.clear()
            GlobalScope.launch {
                while (realmLoading) delay(100)
                val loadToRealm = mutableListOf<CozyStreamer>()
                Global.homepage?.forEach { cozyStream ->
                    if(imageCache.containsKey(cozyStream.avatarUrl)) {
                        val imV = imageCache[cozyStream.avatarUrl]
                        Global.avatars[cozyStream.name] = imV?.bitmap
                        imV?.mutReplays?.let {
                            cozyStream.replayTimes = it
                        }

                    }
                    else {
                        loadToRealm.add(cozyStream)
                        val newurl = URL(cozyStream.avatarUrl)
                        Global.avatars[cozyStream.name] = try {
                            BitmapFactory.decodeStream(newurl.openConnection().getInputStream())
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                GlobalScope.launch { makeHome() }
                allDone = false
                if(!loadToRealm.isNullOrEmpty()){
                    val realm = Realm.open(config)
                    realm.write {
                        loadToRealm.forEach {
                            val quer = "name == '"+it.name+"'"
                            val streamer: RCozyStreamer? = this.query<RCozyStreamer>(quer).first().find()
                            // if the query returned an object, update object from the query
                            if (streamer != null) {
                                streamer.name = it.name
                                streamer.http = it.avatarUrl
                                streamer.bitmapBase64 = Global.avatars[it.name].bitToBase()
                            } else {
                                // if the query returned no object, insert a new object with a new primary key.
                                this.copyToRealm(RCozyStreamer().apply {
                                    name = it.name
                                    http = it.avatarUrl
                                    bitmapBase64 = Global.avatars[it.name].bitToBase()
                                })
                            }
                        }
                    }
                    realm.close()

                }
            }
            makeHomeNavigation()
        }

        actionBarToggle = ActionBarDrawerToggle(this, binding.root, 0, 0)
        binding.root.addDrawerListener(actionBarToggle)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        actionBarToggle.syncState()
        binding.GoBack.setOnClickListener {
            onBackPressed()
        }
        val web = binding.WebViewForLoggingIn

        val webSettings: WebSettings = web.settings
        val newUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:98.0) Gecko/20100101 Firefox/98.0"
        webSettings.userAgentString = newUserAgent
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.setSupportZoom(true)
        webSettings.builtInZoomControls = true
        webSettings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING;
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true;
        getInstance().setAcceptThirdPartyCookies(web, true);
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val cook =try{getInstance().getCookie(url)}catch (e:Exception){null}
                //if(cook != null) web.loadUrl("https://cozy.tv/")
                GlobalScope.launch {
                    delay(2000)
                    runOnUiThread {
                        val manager = CookieManager.getInstance()
                        val cookies = try{manager.getCookie("cozy.tv")}catch (e:Exception){null}
                        println()
                    }
            /*
                        with(sharedPref.edit()) {
                            putString(cookiesPref, cookies)
                            apply()
                        }
                        Global.cookies = cookies
                        GlobalScope.launch {
                            setWho()
                        }
                        binding.WebViewForLoggingIn.visibility = View.GONE
                        binding.fragmentContainerView.visibility = View.VISIBLE
                        alertUser("DONE, NOW YOU ARE LOGGED IN AS: ")
                        */
                    }
                println()
            }


        }
        binding.tempLogin.visibility = View.GONE
        binding.LoginAccount.setOnClickListener {
            if(binding.fragmentContainerView.isVisible) {
                binding.fragmentContainerView.visibility = View.GONE
                binding.tempLogin.visibility = View.VISIBLE
               // binding.WebViewForLoggingIn.visibility = View.VISIBLE
               // web.loadUrl("https://cozy.tv/")
               // web.loadUrl("https://google.com")
            }
            else {
                binding.fragmentContainerView.visibility = View.VISIBLE
                binding.WebViewForLoggingIn.visibility = View.GONE
                binding.tempLogin.visibility = View.GONE
            }
        }
        binding.button3.setOnClickListener {
            val cookies = binding.editTextTextPersonName.text.toString()
            with(sharedPref.edit()) {
                putString(cookiesPref, cookies)
                apply()
            }
            Global.cookies = cookies
        }
    }


    suspend fun setupWebsocket(name: String? = null) {
        val client = HttpClient(OkHttp) {
            engine {
                expectHttpUpgrade(HttpMethod.Get,"websocket",ConnectionOptions.parse("{\"keep-alive\", \"Upgrade\"}"))
                config {
                    //  getUnsafeOkHttpClient()
                    OkHttpClient.Builder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .retryOnConnectionFailure(true)
                        .cache(null)
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .build()


                }
            }
            BrowserUserAgent()

            install(WebSockets)
        }
            client.wss ("wss://api.cozy.tv/ws/wsock",
                request = {
                    header(HttpHeaders.Host,"api.cozy.tv")
                 //   header(HttpHeaders.UserAgent,"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:98.0) Gecko/20100101 Firefox/98.0")
                    header(HttpHeaders.AcceptLanguage,"en-US,en;q=0.5")
                    header(HttpHeaders.AcceptEncoding,"{\"gzip\", \"deflate\", \"br\"}")
                    header(HttpHeaders.SecWebSocketVersion,13)
                    header(HttpHeaders.Origin,"https://cozy.tv")
                  //  header(HttpHeaders.SecWebSocketExtensions,"permessage-deflate")
                    header(HttpHeaders.Connection,"{\"keep-alive\", \"Upgrade\"}")
                    header(HttpHeaders.Pragma,"no-cache")
                    header(HttpHeaders.CacheControl,"no-cache")
                    header(HttpHeaders.Accept, "*/*")
                    header(HttpHeaders.Destination,"websocket")
                  //  header("Upgrade","websocket")
                }) {
                send("sub:status")
                openedSocked = true
                if(name != null) {
                    send("chats:$name")
                    send("sub:$name")
                }
                newOne+=1
                val last = newOne
                while(last == newOne) {
                    try {
                        val othersMessage = incoming.receive() as? Frame.Text
                        val res = othersMessage?.readText()
                        println(res)
                        GlobalScope.launch {
                            statusMake(res?.getClassJson<CozyStatus>())
                        }
                    }
                    catch (e:Exception){
                        delay(200)
                    }
                }
            }
        client.close()
    }

    suspend fun statusMake(classJson: CozyStatus?) {
        when(classJson?.type) {
            "status" -> {
                val vi = classJson.viewers?:0
                val cur = allMapView[classJson.channel]
                cur?.cozyStreamer?.viewers = vi
                runOnUiThread {
                    cur?.viewerCountView?.text = vi.toString()
                }
            }
            else -> {
                var timeout = 60
                while(!playerFrag.isVisible && timeout > 0){
                    timeout-=1
                    delay(200)
                }
                delay(100)
                classJson?.chats?.forEach { chat ->
                    if(playerFrag.isVisible){
                        runOnUiThread {
                            val view = CozyChatMessage(this, chat)
                            playerFrag.binding.cozyChatView.chatMess.addView(view)
                            GlobalScope.launch {
                                delay(250)
                                runOnUiThread {
                                    playerFrag.binding.cozyChatView.scrollViewV.fullScroll(View.FOCUS_DOWN)
                                }
                            }
                        }
                    }

                }
            }
        }
    }

    suspend fun makeHome() {
        runOnUiThread {
            binding.curLiveLin.removeAllViews()
            binding.continueWatchingLin.removeAllViews()
           // binding.watchLaterLin.removeAllViews()
        }
        if (Global.homepage == null) return
        for (st in Global.homepage!!) {
            if (st.isLive != null) {
                val cozyR = CozyReplayRep("", "", -1, false,"LIVE", st.title, st.name, st.viewers ?: 0)
                val view = CozyReplayView(this, cozyR)
                runOnUiThread {
                view.setOnClickListener {
                    Global.currentStreamer = st
                    supportFragmentManager.popBackStackImmediate()
                    supportFragmentManager.commit {
                        this.replace(R.id.fragmentContainerView, playerFrag,"playerFrag")
                        this.addToBackStack("playerFrag")
                    }
                }
                    binding.curLiveLin.addView(view)
                }
            }
            if(!st.replayTimes.isNullOrEmpty()){st.replayTimes.forEach {
                val m = it.value
                val cozyR =
                    CozyReplayRep("UPDATE", "", it.value.watch, it.value.watchLater,it.key,
                        st.title, st.name, st.viewers ?: 0, it.value.time.toSeconds()
                    )
                val view = CozyReplayView(this, cozyR)
                runOnUiThread {
                    binding.continueWatchingLin.addView(view)
                    view.setOnClickListener {
                        supportFragmentManager.popBackStackImmediate()
                        supportFragmentManager.commit {
                            this.replace(R.id.fragmentContainerView, playerFrag,"playerFrag")
                            this.addToBackStack("playerFrag")
                        }
                        Global.currentStreamer = st
                        val urlStream = "https://cozycdn.foxtrotstream.xyz/replays/${st.name}/${m.url}/index.m3u8"
                        GlobalScope.launch {
                            var back = 10
                            delay(200)
                            while (back>0 && playerFrag.initilizingNew) {
                                delay(200)
                                back-=1
                            }
                            runOnUiThread {
                                playerFrag.startVideo(urlStream)
                            }
                        }
                    }
                }
            }
            }
            /*
            else {
                val time = 0
                val cozyR = CozyReplayRep("", "", time.toSeconds(), "NOT IMPLEMENTED", st.title, st.name, st.viewers ?: 0)
                val view = CozyReplayView(this, cozyR)
                runOnUiThread {
                    binding.watchLaterLin.addView(view)
                }
            }
             */
        }
    }

    suspend fun setWho() {

        val who = resultHttpGet("https://api.cozy.tv/whoami")
        runOnUiThread {
            val user = who?.getClassJson<CozyUserProfile>()
            Global.me = user
            println()
            GlobalScope.launch {
                val bitmap = user?.tele_photo_url.getImgURL()
                runOnUiThread {
                    if(bitmap!=null) binding.LoginAccount.AvatarView.setImageBitmap(bitmap)
                    else binding.LoginAccount.AvatarView.setImageResource(R.drawable.ic_launcher_background)
                }
            }
        }
    }

    private fun alertUser(txt:String) {
        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setMessage(txt)
            .setCancelable(true)
        alertDialogBuilder.setNegativeButton("OK") {
                dialog, id -> dialog.cancel() }
        runOnUiThread {
            val alert = alertDialogBuilder.create()
            alert.show()
        }
    }

    suspend fun initilizeRealm() {
        imageCache.clear()
        val realm = Realm.open(config)
        val cozyR = realm.query<RCozyStreamer>().asFlow()
        val first = cozyR.firstOrNull()?.list?.size?:0
        if(first!=0) cozyR.onEach { flowResults ->
            when(flowResults){is InitialResults<RCozyStreamer> -> {
                for (streamer in flowResults.list) {
                    val bitmap = streamer.bitmapBase64.getBitmap()
                    val sp = streamer.savedTimestamps?.split("\n")
                    val newV = ImgUserClass(bitmap)
                    sp?.forEach {
                        if(it.trim() != "") {
                            val split = it.split(";")
                            newV.mutReplays[split[0]] = RepT(split[1].toIntOrNull()?:0,
                                split.getOrNull(2)?.toIntOrNull()?:0,
                                  split.getOrNull(3)?.toString()?:"",
                                  split.getOrNull(4)?.toString()?.toBooleanStrictOrNull() == true
                            )
                        }
                    }
                    if (bitmap != null) imageCache[streamer.http] = newV
                }
                realm.close()
                realmLoading = false
            }
                is UpdatedResults -> {}
            }
        }.launchIn(GlobalScope)
        else {
            realm.close()
            realmLoading = false
        }
        println()
    }

    suspend fun makeHomeNavigation(){
        delay(300)
      //  while (allDone) delay(200)
        val followedChannels = Global.me?.followings
        val liveUsers = Global.homepage?.filter { it.isLive != null }?.toMutableList()
        val notLiveUsers = Global.homepage?.filter { it.isLive == null }?.toMutableList()
        runOnUiThread {
            if (followedChannels == null) {
                binding.FollowingLayout.visibility = View.GONE
                binding.followingText.visibility = View.GONE
            } else {
                binding.FollowingLayout.visibility = View.VISIBLE
                binding.followingText.visibility = View.VISIBLE
                val loc = mutableListOf<String>()
                followedChannels.forEach {
                    loc.add(it.channel)
                }
                val liveFollowed = liveUsers?.filter { loc.contains(it.name) }?.toMutableList()
                val nLive = notLiveUsers?.filter { loc.contains(it.name) }?.toMutableList()
                addStreamerTo(binding.FollowingLayout,liveFollowed)
                addStreamerTo(binding.FollowingLayout,nLive)
                liveFollowed?.forEach {
                    liveUsers.remove(it)
                }
                nLive?.forEach {
                    notLiveUsers.remove(it)
                }
            }
        }
        runOnUiThread {
            addStreamerTo(binding.OtherChannelsLayout,liveUsers)
        }
        runOnUiThread {
            addStreamerTo(binding.OtherChannelsLayout,notLiveUsers)
        }


    }
    fun addStreamerTo(linLay:LinearLayout, cozyStreamers: List<CozyStreamer>?){
        runOnUiThread {
            cozyStreamers?.forEach { cozyStreamer ->
                val view = CozyStreamerCircle(this,cozyStreamer)
                view.setOnClickListener {
                    Global.currentStreamer = cozyStreamer
                    supportFragmentManager.popBackStackImmediate()
                    supportFragmentManager.commit {
                        this.replace(R.id.fragmentContainerView, playerFrag,"playerFrag")
                        this.addToBackStack("playerFrag")
                    }
                }
                allMapView[cozyStreamer.name] = view
                linLay.addView(view)
            }
        }
    }
    suspend fun resultHttpGet(url:String):String?{
        var exitResult:String? = null
        val interceptor = HttpLoggingInterceptor()
        interceptor.level = HttpLoggingInterceptor.Level.BODY
        if(Global.cookies!=null) headerOriginal["Cookie"] = "api.session="+Global.cookies!!
        else headerOriginal.remove("Cookie")
        var client: OkHttpClient = OkHttpClient.Builder().addInterceptor(interceptor)
            .addInterceptor(UnzippingInterceptor()).addInterceptor(BrotliResponseInterceptor())
            .build();
        println()
        fun getRequest(sUrl: String): String? {
            var result: String? = null
            try {
                val r = okhttp3.Request.Builder()
                headerOriginal.forEach {
                    r.addHeader(it.key,it.value)
                }
                // Create URL
                  // Build request
                    r.url(URL(sUrl))

                    r.get()
                val request =r.build()   // Execute request
                val response = client.newCall(request).execute()
                exitResult = (response.body!!.string());


                println()
            }
            catch(err:Error) {
                print("Error when executing get request: "+err.localizedMessage)
            }
            return result
        }
        try {
            getRequest(url)
        }
        catch (e:Exception){return null}
        while(exitResult == null) delay(100)
        return if(exitResult == "Error!") null else exitResult

    }

    class UnzippingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response: Response = chain.proceed(chain.request())
            return unzip(response)
        }

        private fun unzip(response: Response): Response {
            if (response.body == null) {
                return response
            }

            //check if we have gzip response
            val contentEncoding: String? = response.headers["Content-Encoding"]

            //this is used to decompress gzipped responses
            return if (contentEncoding != null && contentEncoding == "gzip") {
                val contentLength: Long = response.body!!.contentLength()
                val responseBody = GzipSource(response.body!!.source())
                val strippedHeaders: Headers = response.headers.newBuilder().build()
                response.newBuilder().headers(strippedHeaders)
                    .body(
                        RealResponseBody(
                            response.body!!.contentType().toString(),
                            contentLength,
                            responseBody.buffer()
                        )
                    )
                    .build()
            } else {
                response
            }
        }
    }

    fun canEnterPiPMode(): Boolean {
        if(supportFragmentManager.findFragmentByTag("playerFrag")==null) return false
        if(!playerFrag.hasMedia) return false


        val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AppOpsManager.MODE_ALLOWED == appOpsManager.unsafeCheckOpRaw(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                myUid(),
                packageName
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AppOpsManager.MODE_ALLOWED == appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    myUid(),
                    packageName
                )
            } else {
                return true
            }
        }
    }

    override fun onUserLeaveHint() {
        if(!canEnterPiPMode()) return
        if(Build.VERSION.SDK_INT == Build.VERSION_CODES.N){
            enterPictureInPictureMode()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  val params = PictureInPictureParams.Builder()
            .setAspectRatio(getPipRatio())
            .build()
            enterPictureInPictureMode(params)
        }
    }

    private fun getPipRatio(): Rational {
        return if(resources.configuration.orientation
            == Configuration.ORIENTATION_PORTRAIT)
        {
            Rational(window.decorView.height, window.decorView.width)
        } else {
            Rational(window.decorView.width, window.decorView.height)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        openedNav = binding.root.isDrawerOpen(GravityCompat.START)
        if (openedNav) binding.root.closeDrawer(GravityCompat.START)
        else binding.root.openDrawer(binding.navigation)
        supportFragmentManager.fragments.forEach {
            if(it is PlayerFragment) {
                it.mc?.hide()
            }
        }
        return true
    }

    override fun onBackPressed() {
        if (binding.root.isDrawerOpen(GravityCompat.START)) {
            binding.root.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
        if(homepage.isVisible) {
            binding.GoBack.visibility = View.GONE
            binding.scrollHome.visibility = View.VISIBLE
        }
    }
}

private fun Int.toSeconds(): Int {
    return if(this == null) return 0
    else try {
        TimeUnit.MILLISECONDS.toSeconds(this.toLong()).toInt()
    }
    catch (e:Exception){return 0}

}

private fun Bitmap?.bitToBase(): String? {
    if(this == null) return null
    return try {
        val byteArrayOutputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        val byteArray: ByteArray = byteArrayOutputStream.toByteArray()
        Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
    catch (e:Exception){
        null
    }
}

inline fun <reified T> String.getClassJson(): T? =
    try{ serializer.decodeFromString(this) }
catch (e:Exception){
    println(e.message)
    null
}

private fun String?.getBitmap():Bitmap?{
    if(this == null) return null
    return try {
        val imageBytes = Base64.decode(this, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
    catch (e:Exception) { null }
}
private fun String?.toJsonObj(): JSONObject? {
    if(this == null) return null
    return try{
        JSONObject(this)
    }
    catch (e:Exception){
        null
    }

}
