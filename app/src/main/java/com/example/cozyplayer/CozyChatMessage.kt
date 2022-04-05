package com.example.cozyplayer

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Html
import android.text.Html.ImageGetter
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL


/**
 * TODO: document your custom view class.
 */
class CozyChatMessage : LinearLayout {
    private var empty: Drawable? = null
    lateinit var messageText:TextView
    lateinit var AvatarView:ImageView
    lateinit var moderatorView:ImageView
    var chat:CozyChatBNChat?=null
    constructor(context: Context) : super(context) {
        init(null, 0)
    }
    constructor(context: Context,chatBNChat: CozyChatBNChat) : super(context) {
        chat = chatBNChat
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
        // Load attributes
        inflate(context, R.layout.sample_cozy_chat_message, this)

        messageText = findViewById(R.id.messageText)
        AvatarView = findViewById(R.id.AvatarView)
        moderatorView = findViewById(R.id.moderatorView)
        val message = chat?.message
        val displayName = chat?.displayName
        val meta = chat?.meta
        val mid = chat?.mid
        val photo = chat?.photo
        val uid = chat?.uid
        val role = chat?.role
        if(meta!= null){
            (AvatarView.parent as? ViewGroup)?.removeView(AvatarView)
            (moderatorView.parent as? ViewGroup)?.removeView(moderatorView)
        }
        else{
            GlobalScope.launch {
                val bit = photo.getImgURL()
                (context as? Activity)?.runOnUiThread {
                    if (bit != null) {
                        AvatarView.setImageBitmap(bit)
                    }
                    else{
                        AvatarView.setImageResource(R.drawable.ic_launcher_foreground)
                    }
                }
            }
        }
        var htm = ""
        var cursticker = ""
        var startst = true
        message?.forEach {
            if(!startst && it.code != 448){
                cursticker+=it
            }
            if(it.code == 448){
                startst = !startst
                if(startst) {
                    htm+=createImg(cursticker)
                    cursticker = ""
                }
            }
            else{
                if(startst){
                    htm+=it
                }
            }
        }
        val userName = "$displayName: "
        displayHtml(userName+htm)

    }

    private fun createImg(stick: String): String {
        return "<img src=\"https://prd.foxtrotstream.xyz/a/stk/${stick}.webp\" class=\"chat_sticker\"></img>"
    }

    private fun displayHtml(html: String) {

        // Creating object of ImageGetter class you just created
        val imageGetter = ImageGetter(resources, messageText)

        // Using Html framework to parse html
        val styledText= HtmlCompat.fromHtml(html,
            HtmlCompat.FROM_HTML_MODE_LEGACY,
            imageGetter,null)

        // to enable image/link clicking
        messageText.movementMethod = LinkMovementMethod.getInstance()

        // setting the text after formatting html and downloading and setting images
        messageText.text = styledText
    }
}


private fun String?.toHTML(): Spanned? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(this?:"", Html.FROM_HTML_MODE_COMPACT)
    } else {
        Html.fromHtml(this)
    }
}

suspend fun String?.getImgURL(): Bitmap? {
    if(this == null) return null
    val newurl = URL(this)
    return try {
        BitmapFactory.decodeStream(newurl.openConnection().getInputStream())
    } catch (e: Exception) {
        null
    }
}

class ImageGetter(
    private val res: Resources,
    private val htmlTextView: TextView
) : Html.ImageGetter {

    // Function needs to overridden when extending [Html.ImageGetter] ,
    // which will download the image
    override fun getDrawable(url: String): Drawable {
        val holder = BitmapDrawablePlaceHolder(res, null)

        // Coroutine Scope to download image in Background
        GlobalScope.launch(Dispatchers.IO) {
            runCatching {

                // downloading image in bitmap format using [Picasso] Library
                val bitmap = url.getImgURL()
                val drawable = BitmapDrawable(res, bitmap)

                // To make sure Images don't go out of screen , Setting width less
                // than screen width, You can change image size if you want
                val dp = (res.displayMetrics.widthPixels*0.2)
                val width = dp.toInt()

                // Images may stretch out if you will only resize width,
                // hence resize height to according to aspect ratio
                val aspectRatio: Float =
                    (drawable.intrinsicWidth.toFloat()) / (drawable.intrinsicHeight.toFloat())
                val height = width / aspectRatio
                drawable.setBounds(10, 20, width, height.toInt())
                holder.setDrawable(drawable)
                holder.setBounds(10, 20, width, height.toInt())
                withContext(Dispatchers.Main) {
                    htmlTextView.text = htmlTextView.text
                }
            }
        }
        return holder
    }

    // Actually Putting images
    internal class BitmapDrawablePlaceHolder(res: Resources, bitmap: Bitmap?) :
        BitmapDrawable(res, bitmap) {
        private var drawable: Drawable? = null

        override fun draw(canvas: Canvas) {
            drawable?.run { draw(canvas) }
        }

        fun setDrawable(drawable: Drawable) {
            this.drawable = drawable
        }
    }

    // Function to get screenWidth used above
    fun getScreenWidth() =
        Resources.getSystem().displayMetrics.widthPixels
}