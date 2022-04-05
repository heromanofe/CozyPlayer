package com.example.cozyplayer

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.content.ContextCompat.getSystemService
import com.angusmorton.brotli.BrotliResponseInterceptor
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody.Part.Companion.create
import okhttp3.internal.notify
import okhttp3.logging.HttpLoggingInterceptor
import java.net.URL


/**
 * TODO: document your custom view class.
 */
val JSON: MediaType? = "application/json; charset=utf-8".toMediaTypeOrNull()
class CozyChatView : LinearLayout {
    lateinit var chatMess:LinearLayout
    lateinit var messageContent:TextView
    lateinit var sendMessageButton:ImageView
    lateinit var scrollViewV:ScrollView
    var chat:CozyChatBNChat?=null
    constructor(context: Context) : super(context) {
        init(null, 0)
    }
    constructor(context: Context,chatBNChat: CozyChatBNChat) : super(context) {
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
        inflate(context, R.layout.sample_cozy_chat_view, this)
        chatMess = findViewById(R.id.chatMess)
        scrollViewV = findViewById(R.id.scrollC)
        sendMessageButton = findViewById(R.id.sendMessageButton)
        messageContent = findViewById(R.id.messageContent)
        messageContent.text = ""
        chatMess.removeAllViews()
        sendMessageButton.setOnClickListener {
            send()
        }
        val editText = messageContent
        editText.setOnEditorActionListener(
            TextView.OnEditorActionListener { v, actionId, event ->
                send()
                return@OnEditorActionListener true // consume.
            }
        )
    }

    private fun send() {
        if(Global.currentStreamer!=null && Global.me!=null){
            val c = context.toAct()
            if(messageContent.text.toString() == "") {
                val toast = Toast.makeText(c, "You need to Write Something.", Toast.LENGTH_SHORT)
                toast.show()
                closeKeybaord()
                return
            }
            GlobalScope.launch {
                resultHttpPost(
                    Global.currentStreamer?.name.toString(),
                    messageContent.text.toString()
                )
                c?.runOnUiThread {
                    messageContent.text = ""
                    val toast = Toast.makeText(c, "Message was sent", Toast.LENGTH_SHORT)
                    toast.show()
                    closeKeybaord()
                }
            }
        }
    }
    fun closeKeybaord(){
        val ok = context.toAct()?:return
        val imm: InputMethodManager = ok.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        var view = ok.currentFocus
        if (view == null) {
            view = View(ok)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
    suspend fun resultHttpPost(channel:String, message:String):String?{
        val url = "https://api.cozy.tv/chat"
        var exitResult:String? = null
        val interceptor = HttpLoggingInterceptor()
        interceptor.level = HttpLoggingInterceptor.Level.BODY
        val content = "{\"channel\":\"$channel\",\"message\":\"$message\"}"
        if(Global.cookies!=null) headerOriginal["Cookie"] = "api.session="+Global.cookies!!
        else headerOriginal.remove("Cookie")

        var client: OkHttpClient = OkHttpClient.Builder().addInterceptor(interceptor)
            .addInterceptor(MainActivity.UnzippingInterceptor()).addInterceptor(
                BrotliResponseInterceptor()
            )
            .build();
        println()
        fun getRequest(sUrl: String): String? {
            var result: String? = null
            try {
                val body = RequestBody.create(JSON, content)
                val r = okhttp3.Request.Builder()
                headerOriginal.forEach {
                    r.addHeader(it.key,it.value)
                }
                // Create URL
                // Build request
                r.url(URL(sUrl))

                r.post(body)
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
}

private fun Context.toAct(): Activity? {
    return this as? Activity
}
