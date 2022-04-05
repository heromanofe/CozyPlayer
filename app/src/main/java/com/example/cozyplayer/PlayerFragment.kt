package com.example.cozyplayer

import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.*
import android.widget.MediaController
import androidx.core.app.NotificationCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import com.example.cozyplayer.databinding.FragmentPlayerBinding
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.query
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [PlayerFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
@DelicateCoroutinesApi
class PlayerFragment : Fragment() {
    var iWasPaused = false
    val config = RealmConfiguration.Builder(schema = setOf(RCozyStreamer::class))
        .name("CozyDatabase")
        .deleteRealmIfMigrationNeeded()
        .build()
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    val audioMode = "index-ts-audio.m3u8"
    val videoMode = "index.m3u8"
    var mc:MediaController? = null
    lateinit var binding: FragmentPlayerBinding
    var countDown:CountDownTimer? = null
    var startDate: Long? = null
    var initilizingNew = true
    var currentlyStream = false
    var lastView:CozyReplayView?=null
    var urlCur = ""
    var hasMedia = false
    var minimized = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentPlayerBinding.inflate(layoutInflater)
        val ok = activity as? MainActivity
        GlobalScope.launch {
            ok?.setupWebsocket(Global.currentStreamer?.name)
        }
        iWasPaused = false
        hasMedia = false
        lastView = null
        mc = MediaController(context)
        mc?.setAnchorView(binding.videoView)
        binding.videoView.setMediaController(mc)
        if(countDown == null) { countDown = countTimer() }
        binding.videoView.setOnPreparedListener {
            initilizingNew = false
        }
        if(Global.currentStreamer?.isLive != null){
            binding.videoView.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            currentlyStream = true
            val urlStream = "https://cozycdn.foxtrotstream.xyz/live/"+Global.currentStreamer!!.name+"/index.m3u8"
            startVideo(urlStream,true)
        }
        else {
            currentlyStream = false
            val displayMetrics = resources.displayMetrics
            binding.videoView.layoutParams.height = (displayMetrics.heightPixels*0.25).toInt()
        }
        if(Global.currentStreamer!=null) {
            GlobalScope.launch {
                makeReplays(Global.currentStreamer!!)
            }
        }
        ok?.supportActionBar?.title = Global.currentStreamer?.displayName
        ok?.binding?.GoBack?.visibility = View.VISIBLE
        ok?.binding?.scrollHome?.visibility = View.GONE
        binding.button.setOnClickListener {
            minimized = !minimized
            if(minimized){
                binding.replaysScroll.visibility = View.GONE
                binding.replaysScroll.invalidate()
                binding.cozyChatView.invalidate()
            }
            else binding.replaysScroll.visibility = View.VISIBLE
        }
        return binding.root
    }

    override fun onPause() {
        GlobalScope.launch { saveTimes() }
        iWasPaused = true
        super.onPause()
    }
    override fun onResume() {
        if(iWasPaused){
            if(!currentlyStream && lastView!= null){
                val last = lastView?.cozyReplay?.id
                val j =  ( Global.currentStreamer?.replayTimes?.get(last) )
                val jumpingPoint = j?.time?:0
                val ok = activity as? MainActivity
                ok?.supportActionBar?.title = Global.currentStreamer?.displayName
                binding.videoView.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                if(jumpingPoint != 0) binding.videoView.seekTo(jumpingPoint)
            }
            binding.videoView.requestFocus();
            binding.videoView.start();
            getValueColor()
            iWasPaused = false
        }
        super.onResume()
    }
    suspend fun makeReplays(currentStreamer: CozyStreamer) {
        val ok = activity as MainActivity
        ok.runOnUiThread {
            binding.replaysScreen.removeAllViews()
        }
        val url = "https://api.cozy.tv/cache/${currentStreamer.name}/replays"
        val back = ok.resultHttpGet(url)
        val newRep = back?.getClassJson<CozyReplayReq>()
        ok.runOnUiThread {
            newRep?.replays?.forEach { cozyRep ->
                val view = CozyReplayView(ok,cozyRep)
                view.setOnClickListener {
                    if(currentlyStream){
                        val newView = CozyReplayView(ok,
                            CozyReplayRep("","",-1,false,"LIVE",currentStreamer.title,currentStreamer.name,currentStreamer.viewers?:0))
                        binding.replaysScreen.addView(newView,0)
                        currentlyStream = false
                        newView.setOnClickListener {
                            currentlyStream = true
                            val urlStream = "https://cozycdn.foxtrotstream.xyz/live/"+currentStreamer.name+"/index.m3u8"
                            startVideo(urlStream,true)
                            binding.videoView.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                            binding.replaysScreen.removeView(newView)
                            lastView?.makeTime()
                            lastView = null
                        }
                    }
                    if(lastView == view) return@setOnClickListener
                    val urlStream = "https://cozycdn.foxtrotstream.xyz/replays/${cozyRep.user}/${cozyRep.id}/index.m3u8"
                    view.timePlace.text = "CURRENT"
                    lastView?.makeTime()
                    lastView = it as? CozyReplayView
                    startVideo(urlStream)

                }
                binding.replaysScreen.addView(view)
            }
            getValueColor()
        }
    }

    fun startVideo(urlStream: String,isLive:Boolean = false) {
        urlCur = urlStream
        hasMedia = true
        val jumpingPoint = (if(!isLive) {
            val last = lastView?.cozyReplay?.id
            Global.currentStreamer?.replayTimes?.get(last)?.time
        }else 0)?:0
        val ok = activity as? MainActivity
        ok?.supportActionBar?.title = Global.currentStreamer?.displayName
        binding.videoView.setVideoPath(urlStream)
        binding.videoView.requestFocus();
        binding.videoView.start();
        binding.videoView.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT

        if(jumpingPoint != 0) binding.videoView.seekTo(jumpingPoint)
        getValueColor()
        if(isLive){
            binding.button2.visibility = View.GONE
        }
        else{
            binding.button2.visibility = View.VISIBLE
            binding.button2.setOnClickListener{
                val wV = (lastView?.cozyReplay?.watchl == true).not()
                lastView?.cozyReplay?.watchl = wV
                GlobalScope.launch {
                    saveTimes()
                }
                if(wV){
                    binding.button2.text = "Remove From Watch Later"
                }
                else binding.button2.text = "Add To Watch Later"
            }
        }
        val wVv = (lastView?.cozyReplay?.watchl == true)
        if(wVv){
            binding.button2.text = "Remove From Watch Later"
        }
        else binding.button2.text = "Add To Watch Later"
    }

    private fun getValueColor() {
        binding.replaysScreen.children.forEach {
            if(it is CozyReplayView) if(it.cozyReplay?.id == "LIVE") it.progress.progress = 100
            else it.progress.progress = try {
                val seconds = TimeUnit.MILLISECONDS.toSeconds(((Global.currentStreamer?.replayTimes?.get(it.cozyReplay?.id))?.time?:0).toLong()).toFloat()
                ((seconds / (it.cozyReplay?.duration ?: 0))*100).toInt()

            }catch (e:Exception){0}
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val new = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        if(!hasMedia){
            val dMetrics = resources.displayMetrics
            if(new){ binding.videoView.layoutParams.height = (dMetrics.widthPixels*0.20).toInt() }
            else binding.videoView.layoutParams.height = (dMetrics.heightPixels*0.25).toInt()
        }
        val activity = context as? MainActivity
        if(new) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity?.window?.insetsController?.hide(WindowInsets.Type.statusBars())
            } else {
                @Suppress("DEPRECATION")
                activity?.window?.setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                )
            }
            activity?.supportActionBar?.hide();
        }
        else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity?.window?.insetsController?.show(WindowInsets.Type.statusBars())
            } else {
                @Suppress("DEPRECATION")
                activity?.window?.setFlags(WindowManager.LayoutParams.FLAGS_CHANGED,
                    WindowManager.LayoutParams.FLAGS_CHANGED)
            }
            activity?.supportActionBar?.show();

        }

        super.onConfigurationChanged(newConfig)

    }

    private fun countTimer(): CountDownTimer? {
        val countInt = 5L
        return object : CountDownTimer(countInt * 1000L, countInt) {
            override fun onTick(millisUntilFinished: Long) {
                if(!initilizingNew && binding.videoView.currentPosition > 0){
                    val curReplays = Global.currentStreamer?.replayTimes
                    val re = lastView?.cozyReplay
                    val lastID = re?.id
                    if(curReplays!=null && lastID!= null) curReplays[lastID] = RepT(binding.videoView.currentPosition,re.duration,re.id,re.watchl==true)
                }
            }

            override fun onFinish() {
                GlobalScope.launch { saveTimes() }
                countDown = countTimer()
            }
        }.start()
    }

    suspend fun saveTimes() {
        if(currentlyStream) return
            val realm = Realm.open(config)
            realm.write {
                var cur = ""
                Global.currentStreamer?.replayTimes?.forEach {
                    cur+="${it.key};${it.value.time};${it.value.watch};${it.value.url}\n"
                }
                val quer = "name == '" + Global.currentStreamer?.name + "'"
                val streamer: RCozyStreamer? =
                    this.query<RCozyStreamer>(quer).first().find()
                // if the query returned an object, update object from the query
                if (streamer != null) { streamer.savedTimestamps = cur }
            }
            realm.close()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment PlayerFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}