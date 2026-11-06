package com.aetherkeys.keyboard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/** Supports user-selected screenshots or Share -> KeyPop from another application. */
class KeyPopImageImportActivity:Activity() {
    private val request=49
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        if(intent.action==Intent.ACTION_SEND && intent.type?.startsWith("image/")==true) {
            @Suppress("DEPRECATION")
            val uri=intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            save(uri);finish()
        } else {
            try {startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply{
                type="image/*";addCategory(Intent.CATEGORY_OPENABLE)
            },request)}
            catch(_:Exception){finish()}
        }
    }
    @Deprecated("Activity Result API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==request && resultCode==RESULT_OK)save(data?.data)
        finish()
    }
    private fun save(uri:Uri?) {
        val success=runCatching {uri?.let{KeyPopImageStore(this).save(it)} ?:false}.getOrDefault(false)
        Toast.makeText(this,if(success)"Screenshot saved securely in KeyPop"
            else "Could not import image (12 MB limit)",Toast.LENGTH_LONG).show()
    }
}
