package com.aetherkeys.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** Paginated view of every locally saved clip; never truncates history to 60. */
class KeyPopClipboardActivity:Activity() {
    private val store by lazy {ClipStore(this)}
    private val worker=Executors.newSingleThreadExecutor()
    private val ink=Color.WHITE
    private val accent=0xFF78E9D4.toInt()
    private val muted=0xFFADC5D1.toInt()
    private lateinit var items:LinearLayout
    private lateinit var status:TextView
    private lateinit var loadMore:Button
    private var query=""
    private var onlyPinned=false
    private var offset=0
    private var generation=0
    private val pageSize=60
    private fun d(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun label(value:String,size:Float=14f,color:Int=ink)=TextView(this).apply{
        text=value;textSize=size;setTextColor(color);setPadding(d(3),d(5),d(3),d(5))
    }
    private fun button(value:String,fn:()->Unit)=Button(this).apply{
        text=value;isAllCaps=false;textSize=12f;setTextColor(ink)
        background=GradientDrawable().apply{
            setColor(0xFF244559.toInt());cornerRadius=d(12).toFloat()
            setStroke(d(1),0xFF436578.toInt())
        }
        setOnClickListener{fn()}
        minHeight=d(41)
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=0xFF10222E.toInt()
        window.navigationBarColor=0xFF10222E.toInt()
        val root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setBackgroundColor(0xFF10222E.toInt())
            setPadding(d(13),d(12),d(13),d(10))
        }
        root.addView(label("Clipboard archive",23f))
        root.addView(label("Encrypted on this device · No automatic age or item limit. " +
            "History is kept until you delete it or clear app data; available storage applies.",
            12f,muted))
        val search=EditText(this).apply{
            hint="Search all saved clips";textSize=15f;setSingleLine(true)
            setTextColor(ink);setHintTextColor(muted)
            background=GradientDrawable().apply{
                setColor(0xFF1B3545.toInt());cornerRadius=d(11).toFloat()
                setStroke(d(1),0xFF3A6072.toInt())
            }
            setPadding(d(13),d(8),d(13),d(8))
        }
        root.addView(search,LinearLayout.LayoutParams(-1,d(49)))
        search.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                query=s?.toString().orEmpty();refresh(true)
            }
            override fun afterTextChanged(s:Editable?){}
        })
        val options=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val pinned=button("All clips / Pinned only") {
            onlyPinned=!onlyPinned
            Toast.makeText(this,if(onlyPinned)"Showing pinned clips" else "Showing all clips",
                Toast.LENGTH_SHORT).show()
            refresh(true)
        }
        options.addView(pinned,LinearLayout.LayoutParams(0,d(48),1.5f))
        options.addView(button("Clear unpinned") {
            AlertDialog.Builder(this).setTitle("Delete unpinned history?")
                .setMessage("All unpinned clipboard records will be permanently deleted.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Delete"){_,_->
                    worker.execute{runCatching{store.clearUnpinned()}
                        .onFailure{showError(it)}.onSuccess{runOnUiThread{refresh(true)}}}
                }.show()
        },LinearLayout.LayoutParams(0,d(48),1.3f))
        root.addView(options)
        status=label("Loading saved clips…",12f,accent)
        root.addView(status)
        items=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(ScrollView(this).apply {
            isFillViewport=false;addView(items)
        },LinearLayout.LayoutParams(-1,0,1f))
        loadMore=button("Load more saved clips") {refresh(false)}
        root.addView(loadMore,LinearLayout.LayoutParams(-1,d(46)))
        setContentView(root)
        refresh(true)
    }
    private fun showError(error:Throwable) {
        runOnUiThread{Toast.makeText(this,error.localizedMessage?:"Clipboard unavailable",
            Toast.LENGTH_LONG).show()}
    }
    private fun refresh(reset:Boolean) {
        if(reset){offset=0;items.removeAllViews()}
        val start=offset
        val selectedQuery=query
        val pinned=onlyPinned
        val request=++generation
        loadMore.isEnabled=false
        worker.execute {
            try {
                val page=if(selectedQuery.isBlank())store.page(pageSize,start,pinned)
                    else store.search(selectedQuery,pinned).drop(start).take(pageSize)
                val total=if(selectedQuery.isBlank())store.count(pinned)
                    else store.search(selectedQuery,pinned).size.toLong()
                runOnUiThread{
                    if(request!=generation || isFinishing)return@runOnUiThread
                    for(clip in page)render(clip)
                    offset=start+page.size
                    status.text="$total stored ${if(pinned)"pinned " else ""}clips" +
                        if(selectedQuery.isNotEmpty())" matching search" else ""
                    if(total==0L)items.addView(label(
                        "No saved clips. Enable Clipboard history in KeyPop Settings.",14f,muted))
                    loadMore.visibility=if(offset<total)View.VISIBLE else View.GONE
                    loadMore.isEnabled=true
                }
            } catch(error:Exception) {
                showError(error)
                runOnUiThread {if(request==generation)loadMore.isEnabled=true}
            }
        }
    }
    private fun render(clip:ClipItem) {
        val card=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(d(8),d(8),d(8),d(8))
            background=GradientDrawable().apply{
                setColor(0xFF193342.toInt());cornerRadius=d(12).toFloat()
            }
        }
        card.addView(label((if(clip.pinned)"📌 " else "")+
            DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT)
                .format(Date(clip.time)),11f,muted))
        card.addView(label(clip.text,15f).apply{maxLines=4;ellipsize=android.text.TextUtils.TruncateAt.END})
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
        actions.addView(button("Copy") {
            val clipboard=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("KeyPop clip",clip.text))
            Toast.makeText(this,"Copied for pasting into your app",Toast.LENGTH_SHORT).show()
        },LinearLayout.LayoutParams(0,d(44),1f))
        actions.addView(button(if(clip.pinned)"Unpin" else "Pin") {
            worker.execute{runCatching{store.pin(clip.id,!clip.pinned)}
                .onFailure{showError(it)}.onSuccess{runOnUiThread{refresh(true)}}}
        },LinearLayout.LayoutParams(0,d(44),1f))
        actions.addView(button("Delete") {
            AlertDialog.Builder(this).setTitle("Delete this clip?")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Delete"){_,_->
                    worker.execute{runCatching{store.delete(clip.id)}
                        .onFailure{showError(it)}.onSuccess{runOnUiThread{refresh(true)}}}
                }.show()
        },LinearLayout.LayoutParams(0,d(44),1f))
        card.addView(actions)
        items.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(7)})
    }
    override fun onDestroy() {
        generation++
        worker.shutdown()
        super.onDestroy()
    }
}
