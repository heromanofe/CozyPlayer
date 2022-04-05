package com.example.cozyplayer
import android.graphics.Bitmap
import io.realm.annotations.PrimaryKey
import io.realm.RealmObject

open class RCozyStreamer : RealmObject {
    var name: String = ""
    var http:String = ""
    var bitmapBase64:String?=null
    var savedTimestamps:String?=null
}