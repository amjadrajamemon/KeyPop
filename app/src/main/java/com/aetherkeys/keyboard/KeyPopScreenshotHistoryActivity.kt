package com.aetherkeys.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.concurrent.Executors

/** Screenshot-only encrypted image history. No GIF module or providers. */
class KeyPopScreenshotHistoryActivity:Activity() {
    private val store by lazy{KeyPopImageStore(this)}
    private val pool=Executors.newFixedThreadPool(2)
    private lateinit var list:LinearLayout
    private lateinit var search:EditText
    private var shown=50
    private val request=301
    private val ink=Color.WHITE
    private val accent=0xFF89EAF3.toInt()
    private val ground=0xFF101729.toInt()
    private val surface=0xFF202B43.toInt()
    private fun dp(x:Int)=(x*resources.displayMetrics.density).toInt()
    private fun text(value:String,size:Float=14f,color:Int=ink)=TextView(this).apply{
        text=value;textSize=size;setTextColor(color)
        setPadding(dp(6),dp(6),dp(6),dp(6))
    }
    private fun button(value:String,click:()->Unit)=Button(this).apply{
        text=value;isAllCaps=false;textSize=12f
        setTextColor(ink)
        minHeight=0;minimumHeight=0;minWidth=0;minimumWidth=0
        background=GradientDrawable().apply{
            setColor(surface);cornerRadius=dp(11).toFloat()
            setStroke(dp(1),0xFF37486C.toInt())
        }
        setOnClickListener{click()}
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=ground;window.navigationBarColor=ground
        val wrapper=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(14),dp(15),dp(14),dp(14))
            setBackgroundColor(ground)
        }
        wrapper.addView(text("KEYPOP  /  CLIPBOARD IMAGES",12f,accent))
        wrapper.addView(text("Screenshot history",26f))
        wrapper.addView(text("Screenshots detected after granting Photos access are kept encrypted. You can search, pin, copy, share or delete them.",12f,0xFFA9BBDA.toInt()))
        wrapper.addView(button("＋ Import screenshots or photos") { importMedia() },
            LinearLayout.LayoutParams(-1,dp(45)).apply{topMargin=dp(7);bottomMargin=dp(10)})
        search=EditText(this).apply{
            hint="Search screenshot names"
            setHintTextColor(0xFF91A2BE.toInt());setTextColor(ink)
            setSingleLine(true);textSize=14f
        }
        wrapper.addView(search)
        wrapper.addView(button("Screenshot permission and auto-save settings") {
            startActivity(Intent(this,KeyPopScreenshotAccessActivity::class.java))
        },LinearLayout.LayoutParams(-1,dp(42)))
        val scroll=ScrollView(this)
        list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        scroll.addView(list)
        wrapper.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(wrapper)
        search.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                shown=50;render()
            }
            override fun afterTextChanged(s:Editable?){}
        })
        render()
    }
    override fun onDestroy(){pool.shutdownNow();super.onDestroy()}
    override fun onResume(){super.onResume();if(::list.isInitialized)render()}
    private fun importMedia() {
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply{
            type="image/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
            addCategory(Intent.CATEGORY_OPENABLE)
        },request)
    }
    @Deprecated("Activity Result API")
    override fun onActivityResult(req:Int,status:Int,data:Intent?) {
        super.onActivityResult(req,status,data)
        if(req!=request || status!=RESULT_OK)return
        val uris=ArrayList<android.net.Uri>()
        data?.data?.let{uris.add(it)}
        data?.clipData?.let{clips->
            for(i in 0 until clips.itemCount)uris.add(clips.getItemAt(i).uri)
        }
        pool.execute {
            var imported=0
            for(uri in uris.distinct().take(40)) {
                if(runCatching{store.save(uri)}.getOrDefault(false))imported++
            }
            runOnUiThread {
                Toast.makeText(this,"Imported "+imported+" image(s)",
                    Toast.LENGTH_SHORT).show()
                if(!isFinishing)render()
            }
        }
    }
    private fun render() {
        list.removeAllViews()
        val items=runCatching{store.search(search.text.toString())}
            .getOrElse{
                list.addView(text("Cannot decrypt saved media. No stored items were deleted.",13f))
                return
            }
        list.addView(text(items.size.toString()+" saved screenshot(s)",12f,accent))
        if(items.isEmpty())list.addView(text(
            "No saved screenshots. Open Screenshot access and choose "+
            "Allow all photos, or import screenshots manually.",13f))
        for(item in items.take(shown)) {
            val card=LinearLayout(this).apply{
                orientation=LinearLayout.VERTICAL
                setPadding(dp(8),dp(8),dp(8),dp(8))
                background=GradientDrawable().apply{
                    setColor(surface);cornerRadius=dp(14).toFloat()
                }
            }
            val header=LinearLayout(this).apply{
                orientation=LinearLayout.HORIZONTAL
                gravity=Gravity.CENTER_VERTICAL
            }
            val preview=ImageView(this).apply{
                contentDescription="Saved screenshot preview"
                scaleType=ImageView.ScaleType.CENTER_CROP
            }
            header.addView(preview,LinearLayout.LayoutParams(dp(58),dp(54)))
            pool.execute{
                val thumb=runCatching{store.thumbnail(item,112)}.getOrNull()
                if(thumb!=null)runOnUiThread{
                    if(preview.isAttachedToWindow)preview.setImageBitmap(thumb) else thumb.recycle()
                }
            }
            header.addView(text((if(item.pinned)"📌  " else "")+item.label+
                "\nScreenshot image",13f),
                LinearLayout.LayoutParams(0,-2,1f))
            card.addView(header)
            val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            fun action(name:String,perform:()->Unit) {
                actions.addView(button(name,perform),
                    LinearLayout.LayoutParams(0,dp(37),1f).apply{rightMargin=dp(4)})
            }
            action("Share"){
                val uri=store.uriForShare(item)?:return@action
                val intent=Intent(Intent.ACTION_SEND).apply{
                    type=item.mime
                    putExtra(Intent.EXTRA_STREAM,uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent,"Share screenshot"))
            }
            action("Copy"){
                val uri=store.uriForShare(item)?:return@action
                val manager=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                manager.setPrimaryClip(ClipData.newUri(contentResolver,item.label,uri))
                Toast.makeText(this,"Image available to paste where supported",Toast.LENGTH_SHORT).show()
            }
            action("Pin"){
                store.pin(item.id);render()
            }
            action("Edit"){
                val field=EditText(this).apply{setText(item.label);setSingleLine(true)}
                AlertDialog.Builder(this).setTitle("Rename screenshot").setView(field)
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Save"){_,_->
                        store.rename(item.id,field.text.toString())
                        render()
                    }.show()
            }
            action("Delete"){
                AlertDialog.Builder(this).setTitle("Delete saved image?")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Delete"){_,_->store.delete(item.id);render()}
                    .show()
            }
            card.addView(actions)
            list.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)})
        }
        if(shown<items.size)list.addView(button("Load more saved screenshots") {
            shown+=50
            render()
        },LinearLayout.LayoutParams(-1,dp(47)))
    }
}
