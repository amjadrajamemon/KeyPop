package com.aetherkeys.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
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
    private lateinit var screenshotRow:LinearLayout
    private lateinit var screenshotCount:TextView
    private var query=""
    private var onlyPinned=false
    private var offset=0
    private var generation=0
    private val pageSize=60
    private val exportRequest=811
    private val importRequest=812
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
        screenshotCount=label("Saved screenshots",15f,accent)
        root.addView(screenshotCount)
        screenshotRow=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            setPadding(d(2),d(4),d(2),d(4))
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled=false
            addView(screenshotRow)
        },LinearLayout.LayoutParams(-1,d(84)))
        root.addView(button("All screenshots · Search / Pin / Share") {
            startActivity(Intent(this,KeyPopScreenshotHistoryActivity::class.java))
        },LinearLayout.LayoutParams(-1,d(46)))
        root.addView(button("Enable screenshot detection and automatic history") {
            startActivity(Intent(this,KeyPopScreenshotAccessActivity::class.java))
        },LinearLayout.LayoutParams(-1,d(46)))
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
        val backups=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        backups.addView(button("Encrypted backup") {
            val chooser=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type="application/octet-stream"
                putExtra(Intent.EXTRA_TITLE,"KeyPop-clipboard-history.kpb")
            }
            startActivityForResult(chooser,exportRequest)
        },LinearLayout.LayoutParams(0,d(47),1f))
        backups.addView(button("Restore history") {
            val chooser=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type="*/*"
            }
            startActivityForResult(chooser,importRequest)
        },LinearLayout.LayoutParams(0,d(47),1f))
        root.addView(backups)
        root.addView(label("Backups use your own password and work across KeyPop "+
            "installations. Keep the password safe. Screenshot images are "+
            "stored separately; this backup currently covers text only.",11f,muted))
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
        refreshScreenshots()
    }
    override fun onResume() {
        super.onResume()
        if(::screenshotRow.isInitialized)refreshScreenshots()
    }
    private fun refreshScreenshots() {
        worker.execute {
            val images=runCatching {KeyPopImageStore(this).search("")}
                .getOrDefault(emptyList())
            runOnUiThread {
                if(isFinishing || !::screenshotRow.isInitialized)return@runOnUiThread
                screenshotCount.text="Saved screenshots: "+images.size
                screenshotRow.removeAllViews()
                if(images.isEmpty()) {
                    screenshotRow.addView(label(
                        "No screenshots saved yet. Enable full Photos access above.",
                        12f,muted))
                }
                images.take(12).forEach {image->
                    val thumb=ImageView(this).apply {
                        contentDescription=image.label
                        scaleType=ImageView.ScaleType.CENTER_CROP
                        setBackgroundColor(0xFF294559.toInt())
                        setOnClickListener {
                            startActivity(Intent(this@KeyPopClipboardActivity,
                                KeyPopScreenshotHistoryActivity::class.java))
                        }
                    }
                    screenshotRow.addView(thumb,LinearLayout.LayoutParams(
                        d(74),d(72)).apply{rightMargin=d(6)})
                    worker.execute {
                        val bmp=runCatching {
                            KeyPopImageStore(this).thumbnail(image,128)
                        }.getOrNull()
                        if(bmp!=null)runOnUiThread {
                            if(thumb.isAttachedToWindow)thumb.setImageBitmap(bmp)
                            else bmp.recycle()
                        }
                    }
                }
            }
        }
    }
    @Deprecated("Android activity result callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=Activity.RESULT_OK || data?.data==null ||
            requestCode !in setOf(exportRequest,importRequest))return
        promptBackupPassword(data.data!!,requestCode==exportRequest)
    }
    private fun promptBackupPassword(uri:Uri,isExport:Boolean) {
        val password=EditText(this).apply {
            hint="Password (minimum 8 characters)"
            inputType=InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setPadding(d(14),d(8),d(14),d(8))
        }
        AlertDialog.Builder(this)
            .setTitle(if(isExport)"Encrypt clipboard backup" else "Unlock clipboard backup")
            .setMessage("Only you know this password. Losing it means the "+
                "backup cannot be restored. Do not share it.")
            .setView(password)
            .setNegativeButton("Cancel",null)
            .setPositiveButton(if(isExport)"Save encrypted backup" else "Restore clips") {_,_->
                val pass=password.text.toString()
                password.text.clear()
                if(pass.length<8) {
                    Toast.makeText(this,"Password needs at least 8 characters",
                        Toast.LENGTH_LONG).show()
                }else {
                    status.text=if(isExport)"Encrypting clipboard archive…" else
                        "Verifying and importing backup…"
                    worker.execute {
                        runCatching {
                            if(isExport)KeyPopClipboardBackup.export(this,uri,pass)
                            else KeyPopClipboardBackup.restore(this,uri,pass)
                        }.onSuccess {count->
                            runOnUiThread {
                                if(!isFinishing) {
                                    Toast.makeText(this,if(isExport)
                                        "$count text clips backed up securely"
                                        else "$count text clips restored; originals preserved",
                                        Toast.LENGTH_LONG).show()
                                    refresh(true)
                                }
                            }
                        }.onFailure { error->showError(error) }
                    }
                }
            }.show()
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
                        "No saved text yet. Screenshot images are displayed above.",14f,muted))
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
