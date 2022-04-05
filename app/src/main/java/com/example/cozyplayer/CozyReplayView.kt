package com.example.cozyplayer

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.widget.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.URL
import java.sql.Timestamp
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * TODO: document your custom view class.
 */
class CozyReplayView : LinearLayout {
    var cozyReplay:CozyReplayRep? = null
    lateinit var imageVideo:ImageView
    lateinit var timePlace:TextView
    lateinit var videoTitle:TextView
    lateinit var frameL:FrameLayout
    lateinit var progress:ProgressBar
    lateinit var rootF:LinearLayout
    constructor(context: Context) : super(context) {
        init(null, 0)
    }
    constructor(context: Context,_cozyReplay:CozyReplayRep) : super(context) {
        cozyReplay = _cozyReplay
        init(null, 0)
    }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        inflate(context, R.layout.sample_cozy_replay_view, this)
        imageVideo = findViewById(R.id.imageVideo)
        timePlace = findViewById(R.id.timePlace)
        videoTitle = findViewById(R.id.videoTitle)
        frameL = findViewById(R.id.frameL)
        progress = findViewById(R.id.determinateBar)
        rootF = findViewById(R.id.rootF)
        val display = resources.displayMetrics
        val use = (display.widthPixels * 0.65)/16
        frameL.layoutParams.width = (use*16).roundToInt()
        frameL.layoutParams.height = (use*10).roundToInt()
        cozyReplay?.let {
            val use = "${it.user}/${it.id}"
            GlobalScope.launch {
                val newurl = if(it.id == "LIVE") URL("https://cozycdn.foxtrotstream.xyz/live/${it.user}/livethumb.webp")
                else URL("https://cozycdn.foxtrotstream.xyz/replays/$use/thumb.webp")
                val img = try {
                    BitmapFactory.decodeStream(newurl.openConnection().getInputStream())
                } catch (e: Exception) {
                    null
                }
                (context as Activity).runOnUiThread {
                    imageVideo.setImageBitmap(img)
                    rootF.visibility = View.VISIBLE
                }
            }
            videoTitle.text = it.title
            makeTime()
        }?: run {
            rootF.visibility = View.VISIBLE
        }
    }

    fun makeTime() {
        cozyReplay?.let {
            if (it.duration != -1) {
                var secondsBegin = (it.duration.toLong())
                val hrs = (secondsBegin / 3600).toInt()
                secondsBegin -= (hrs * 3600)
                val minutes = if (secondsBegin > 0) {
                    (secondsBegin / 60).toInt()
                } else 0
                secondsBegin -= (minutes * 60)
                var newText = ""
                if (hrs != 0) newText += "$hrs"
                if (minutes != 0) newText += if (minutes < 10) ":0$minutes" else ":$minutes"
                if (secondsBegin != 0L) newText += if (secondsBegin < 10) ":0$secondsBegin" else ":$secondsBegin"
                if (newText.startsWith(":")) newText.replaceFirst(":", "")
                timePlace.text = newText
            } else {
                timePlace.text = "LIVE"
                timePlace.setTextColor(Color.RED)
            }
        }
    }
}