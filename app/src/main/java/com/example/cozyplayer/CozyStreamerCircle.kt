package com.example.cozyplayer

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TODO: document your custom view class.
 */
class CozyStreamerCircle : LinearLayout {
    var cozyStreamer: CozyStreamer? = null
    lateinit var avatarView:ImageView
    lateinit var displayNameView:TextView
    lateinit var viewerCountView:TextView
    lateinit var liveLayout:LinearLayout
    constructor(context: Context) : super(context) {
        init(null, 0)
    }
    constructor(context: Context,_cozyStreamer:CozyStreamer) : super(context) {
        cozyStreamer = _cozyStreamer
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
        inflate(context, R.layout.sample_cozy_streamer_circle, this)
        avatarView = findViewById(R.id.AvatarView)
        displayNameView = findViewById(R.id.DisplayNameView)
        viewerCountView = findViewById(R.id.ViewerCountView)
        liveLayout = findViewById(R.id.LiveLayout)
        cozyStreamer?.let { streamer ->
            displayNameView.text = streamer.displayName
            viewerCountView.text = streamer.viewers?.toString() ?:"0"
            liveLayout.vis(streamer.isLive!=null)
            var nameA = Global.avatars[streamer.name]
            if(nameA == null){
                GlobalScope.launch {
                    while (!Global.avatars.keys.contains(streamer.name)){
                        delay(200)
                    }
                    nameA = Global.avatars[streamer.name]
                    (context as? Activity)?.runOnUiThread {
                        avatarView.setImageBitmap(nameA)
                    }
                }
            }
            else{
                (context as? Activity)?.runOnUiThread {
                    avatarView.setImageBitmap(nameA)
                }
            }
        }


    }
}

private fun View.vis(b: Boolean) {
    if(b) this.visibility = View.VISIBLE
    else this.visibility = View.GONE
}
