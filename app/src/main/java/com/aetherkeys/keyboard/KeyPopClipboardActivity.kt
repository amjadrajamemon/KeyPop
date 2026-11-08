package com.aetherkeys.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** A focused, private clipboard manager with independent Text/Screenshots
 * tabs. Never mixes screenshots into text results, and never shows empty
 * pagination or bulk deletion as a primary action. */
class KeyPopClipboardActivity:Activity() {
    private val store by lazy {ClipStore(this)}
    private val imageStore by lazy{KeyPopImageStore(this)}
    private val worker=Executors.newSingleThreadExecutor()
    private val thumbnails=Executors.newFixedThreadPool(2)
    private val bg=0xFF0B252B.toInt()
    private val surface=0xFF193940.toInt()
    private val outline=0xFF3C6768.toInt()
    private val ink=0xFFF1F9F6.toInt()
    private val accent=0xFF89E6CE.toInt()
    private val muted=0xFFACC5C4.toInt()
    private lateinit var items:LinearLayout
    private lateinit var images:LinearLayout
    private lateinit var status:TextView
    private lateinit var loadMore:Button
    private lateinit var search:EditText
    private lateinit var textTabButton:Button
    private lateinit var screenshotTabButton:Button
    private lateinit var textScroll:ScrollView
    private lateinit var imageScroll:ScrollView
    private var activeTab=0
    private var query=""
    private var onlyPinned=false
    private var offset=0
    private var imageShown=12
    private var generation=0
    private val pageSize=30
    private val exportRequest=811
    private val importRequest=812
    private fun d(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun label(value:String,size:Float=14f,color:Int=ink)=TextView(this).apply{
        text=value;textSize=size;setTextColor(color)
        setPadding(d(3),d(5),d(3),d(5))
    }
    private fun button(value:String,action:()->Unit)=Button(this).apply {
        text=value;isAllCaps=false;textSize=13f;setTextColor(ink)
        minHeight=0;minimumHeight=0;minWidth=0;minimumWidth=0
        setPadding(d(7),0,d(7),0)
        background=GradientDrawable().apply {
            setColor(surface);cornerRadius=d(12).toFloat()
            setStroke(d(1),outline)
        }
        setOnClickListener{action()}
    }
    private fun selected(button:Button,active:Boolean) {
        button.setTextColor(if(active)0xFF08262A.toInt() else ink)
        button.background=GradientDrawable().apply {
            setColor(if(active)accent else surface)
            cornerRadius=d(12).toFloat()
        }
    }
    private fun enterTab(tab:Int) {
        activeTab=tab
        selected(textTabButton,tab==0)
        selected(screenshotTabButton,tab==1)
        textScroll.visibility=if(tab==0)View.VISIBLE else View.GONE
        imageScroll.visibility=if(tab==1)View.VISIBLE else View.GONE
        search.hint=if(tab==0)"Search saved text" else "Search screenshots"
        query=""
        search.setText("")
        offset=0
        imageShown=12
        loadMore.visibility=View.GONE
        if(tab==0)refresh(true) else refreshScreenshots()
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=bg
        window.navigationBarColor=0xFF0C1D26.toInt()
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(d(14),d(12),d(14),d(12))
            setBackgroundColor(bg)
        }
        val top=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        }
        top.addView(button("‹"){finish()},LinearLayout.LayoutParams(d(44),d(44)))
        top.addView(label("Clipboard",22f),LinearLayout.LayoutParams(0,d(49),1f))
        val more=button("⋮") {}
        top.addView(more,LinearLayout.LayoutParams(d(43),d(44)))
        more.setOnClickListener {showMore(more)}
        root.addView(top)
        root.addView(label("Saved privately on this device",12f,muted))
        val tabs=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            setPadding(0,d(8),0,d(8))
        }
        textTabButton=button("Text clips"){enterTab(0)}
        screenshotTabButton=button("Screenshots"){enterTab(1)}
        tabs.addView(textTabButton,LinearLayout.LayoutParams(0,d(45),1f).apply{
            rightMargin=d(4)
        })
        tabs.addView(screenshotTabButton,LinearLayout.LayoutParams(0,d(45),1f).apply{
            leftMargin=d(4)
        })
        root.addView(tabs)
        search=EditText(this).apply{
            hint="Search saved text";setSingleLine(true)
            textSize=14f;setTextColor(ink);setHintTextColor(muted)
            setPadding(d(12),0,d(12),0)
            background=GradientDrawable().apply {
                setColor(surface);cornerRadius=d(12).toFloat()
                setStroke(d(1),outline)
            }
        }
        root.addView(search,LinearLayout.LayoutParams(-1,d(46)))
        status=label("Saved clips",12f,muted)
        root.addView(status)
        val content=FrameLayout(this)
        items=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(0,d(6),0,d(8))
        }
        images=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(0,d(6),0,d(8))
        }
        textScroll=ScrollView(this).apply{
            isFillViewport=false
            addView(items)
        }
        imageScroll=ScrollView(this).apply {
            isFillViewport=false
            addView(images)
            visibility=View.GONE
        }
        content.addView(textScroll,FrameLayout.LayoutParams(-1,-1))
        content.addView(imageScroll,FrameLayout.LayoutParams(-1,-1))
        root.addView(content,LinearLayout.LayoutParams(-1,0,1f))
        loadMore=button("Load more saved clips"){refresh(false)}
        loadMore.visibility=View.GONE
        root.addView(loadMore,LinearLayout.LayoutParams(-1,d(44)))
        setContentView(root)
        search.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                query=s?.toString().orEmpty()
                offset=0
                imageShown=12
                if(activeTab==0)refresh(true) else refreshScreenshots()
            }
            override fun afterTextChanged(s:Editable?){}
        })
        enterTab(0)
    }
    override fun onResume() {
        super.onResume()
        if(::items.isInitialized) {
            if(activeTab==0)refresh(true) else refreshScreenshots()
        }
    }
    private fun showMore(anchor:View) {
        PopupMenu(this,anchor).apply {
            menu.add("Encrypted text backup").setOnMenuItemClickListener{
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type="application/octet-stream"
                    putExtra(Intent.EXTRA_TITLE,"KeyPop-clipboard-history.kpb")
                },exportRequest);true
            }
            menu.add("Restore text history").setOnMenuItemClickListener{
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
                    addCategory(Intent.CATEGORY_OPENABLE);type="*/*"
                },importRequest);true
            }
            if(activeTab==0) {
                menu.add(if(onlyPinned)"Show all text clips" else "Pinned text only")
                    .setOnMenuItemClickListener {
                        onlyPinned=!onlyPinned
                        refresh(true);true
                    }
                menu.add("Delete unpinned text clips").setOnMenuItemClickListener{
                    AlertDialog.Builder(this@KeyPopClipboardActivity)
                        .setTitle("Delete unpinned text clips?")
                        .setMessage("Pinned clips will be kept. Deleted clips cannot be recovered.")
                        .setNegativeButton("Cancel",null)
                        .setPositiveButton("Delete"){_,_->
                            worker.execute {
                                runCatching{store.clearUnpinned()}
                                    .onFailure{showError(it)}
                                    .onSuccess{runOnUiThread{refresh(true)}}
                            }
                        }.show();true
                }
            }else{
                menu.add("Import screenshots").setOnMenuItemClickListener{
                    startActivity(Intent(this@KeyPopClipboardActivity,
                        KeyPopImageImportActivity::class.java));true
                }
                menu.add("Screenshot detection settings").setOnMenuItemClickListener{
                    startActivity(Intent(this@KeyPopClipboardActivity,
                        KeyPopScreenshotAccessActivity::class.java));true
                }
            }
            show()
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
        if(activeTab!=0)return
        loadMore.text="Load more saved clips"
        loadMore.setOnClickListener{refresh(false)}
        if(reset){offset=0;items.removeAllViews()}
        val start=offset
        val searchTerm=query
        val pinned=onlyPinned
        val request=++generation
        loadMore.visibility=View.GONE
        worker.execute {
            val result=runCatching{
                val list=if(searchTerm.isBlank())store.page(pageSize,start,pinned)
                    else store.search(searchTerm,pinned).drop(start).take(pageSize)
                val total=if(searchTerm.isBlank())store.count(pinned)
                    else store.search(searchTerm,pinned).size.toLong()
                Pair(list,total)
            }
            runOnUiThread{
                if(request!=generation || isFinishing || activeTab!=0)return@runOnUiThread
                result.onFailure{
                    items.removeAllViews()
                    items.addView(label("Text archive unavailable. No data was removed.",14f))
                }.onSuccess { (list,total)->
                    if(reset)items.removeAllViews()
                    offset=start+list.size
                    status.text=if(pinned)total.toString()+" pinned clips"
                        else total.toString()+" saved text clips"
                    search.visibility=if(total==0L && searchTerm.isBlank())
                        View.GONE else View.VISIBLE
                    if(total==0L){
                        items.addView(label(if(pinned)"No pinned clips yet"
                            else "No saved text clips yet",18f))
                        items.addView(label(if(pinned)
                            "Pin a clip from your history to find it here."
                            else "Enable clipboard history to retain text you copy.",
                            13f,muted))
                        if(!pinned && !getSharedPreferences("keypop",MODE_PRIVATE)
                                .getBoolean("history",false)){
                            items.addView(button("Enable clipboard history"){
                                getSharedPreferences("keypop",MODE_PRIVATE).edit()
                                    .putBoolean("history",true).apply()
                                refresh(true)
                            },LinearLayout.LayoutParams(-1,d(46)).apply{topMargin=d(12)})
                        }
                    }else list.forEach{renderTextClip(it)}
                    loadMore.visibility=if(offset<total)View.VISIBLE else View.GONE
                    loadMore.isEnabled=true
                }
            }
        }
    }
    private fun renderTextClip(clip:ClipItem) {
        val row=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(d(9),d(8),d(4),d(8))
            background=GradientDrawable().apply{
                setColor(surface);cornerRadius=d(12).toFloat()
            }
        }
        val detail=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        detail.addView(label((if(clip.pinned)"📌  " else "")+
            DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT)
                .format(Date(clip.time)),11f,muted))
        detail.addView(label(clip.text,14f).apply {
            maxLines=3
            ellipsize=android.text.TextUtils.TruncateAt.END
        })
        detail.setOnClickListener{copyClip(clip)}
        row.addView(detail,LinearLayout.LayoutParams(0,-2,1f))
        val menu=button("⋮"){}
        row.addView(menu,LinearLayout.LayoutParams(d(44),d(43)))
        menu.setOnClickListener {showTextClipMenu(menu,clip)}
        row.setOnLongClickListener{showTextClipMenu(menu,clip);true}
        items.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(8)})
    }
    private fun copyClip(clip:ClipItem) {
        val clipboard=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("KeyPop clip",clip.text))
        Toast.makeText(this,"Copied to clipboard",Toast.LENGTH_SHORT).show()
    }
    private fun showTextClipMenu(anchor:View,clip:ClipItem) {
        PopupMenu(this,anchor).apply {
            menu.add("Copy").setOnMenuItemClickListener{copyClip(clip);true}
            menu.add(if(clip.pinned)"Unpin" else "Pin").setOnMenuItemClickListener{
                worker.execute{
                    runCatching{store.pin(clip.id,!clip.pinned)}
                        .onFailure{showError(it)}
                        .onSuccess{runOnUiThread{refresh(true)}}
                };true
            }
            menu.add("Delete").setOnMenuItemClickListener{
                AlertDialog.Builder(this@KeyPopClipboardActivity)
                    .setTitle("Delete this clip?")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Delete"){_,_->
                        worker.execute{
                            runCatching{store.delete(clip.id)}
                                .onFailure{showError(it)}
                                .onSuccess{runOnUiThread{refresh(true)}}
                        }
                    }.show();true
            }
            show()
        }
    }
    private fun refreshScreenshots() {
        if(activeTab!=1)return
        val request=++generation
        val currentSearch=query
        val limit=imageShown
        loadMore.visibility=View.GONE
        worker.execute {
            val result=runCatching{imageStore.search(currentSearch)}
            runOnUiThread{
                if(isFinishing || activeTab!=1 || request!=generation)return@runOnUiThread
                images.removeAllViews()
                result.onFailure{
                    images.addView(label("Screenshot archive unavailable. No data was removed."))
                }.onSuccess{all->
                    status.text=all.size.toString()+" saved screenshots"
                    search.visibility=if(all.isEmpty() && currentSearch.isBlank())
                        View.GONE else View.VISIBLE
                    if(all.isEmpty()){
                        images.addView(label("No screenshots saved yet",18f))
                        images.addView(label(
                            "Capture a screenshot with KeyPop active, or import one.",
                            13f,muted))
                        images.addView(button("Enable screenshot detection"){
                            startActivity(Intent(this,
                                KeyPopScreenshotAccessActivity::class.java))
                        },LinearLayout.LayoutParams(-1,d(47)).apply{topMargin=d(12)})
                        images.addView(button("Import image"){
                            startActivity(Intent(this,
                                KeyPopImageImportActivity::class.java))
                        },LinearLayout.LayoutParams(-1,d(44)).apply{topMargin=d(8)})
                    }else{
                        var row:LinearLayout?=null
                        all.take(limit).forEachIndexed {index,image->
                            if(index%2==0) {
                                row=LinearLayout(this).apply{
                                    orientation=LinearLayout.HORIZONTAL
                                }
                                images.addView(row,LinearLayout.LayoutParams(-1,-2))
                            }
                            row?.addView(renderScreenshotCard(image),
                                LinearLayout.LayoutParams(0,-2,1f).apply {
                                    leftMargin=if(index%2==1)d(4) else 0
                                    rightMargin=if(index%2==0)d(4) else 0
                                    bottomMargin=d(8)
                                })
                        }
                    }
                    loadMore.visibility=if(all.size>limit)View.VISIBLE else View.GONE
                    loadMore.text="Load more screenshots"
                    loadMore.setOnClickListener{
                        imageShown+=12
                        refreshScreenshots()
                    }
                }
            }
        }
    }
    private fun renderScreenshotCard(item:KeyPopImageClip):View {
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(d(7),d(7),d(7),d(7))
            background=GradientDrawable().apply{
                setColor(surface);cornerRadius=d(13).toFloat()
            }
        }
        val picture=ImageView(this).apply {
            scaleType=ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF265158.toInt())
            contentDescription=item.label
            setOnClickListener{showScreenshotOptions(this,item)}
        }
        card.addView(picture,LinearLayout.LayoutParams(-1,d(100)))
        thumbnails.execute {
            val bitmap=runCatching{imageStore.thumbnail(item,180)}.getOrNull()
            if(bitmap!=null)runOnUiThread{
                if(picture.isAttachedToWindow)picture.setImageBitmap(bitmap)
                else bitmap.recycle()
            }
        }
        val bottom=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        }
        val timestamp=(if(item.pinned)"📌  " else "")+"Saved "+
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(item.time))
        bottom.addView(label(timestamp,11f,muted),
            LinearLayout.LayoutParams(0,d(39),1f))
        val dots=button("⋮"){}
        bottom.addView(dots,LinearLayout.LayoutParams(d(40),d(39)))
        dots.setOnClickListener{showScreenshotOptions(dots,item)}
        card.setOnLongClickListener{showScreenshotOptions(dots,item);true}
        card.addView(bottom)
        return card
    }
    private fun showScreenshotOptions(anchor:View,item:KeyPopImageClip) {
        PopupMenu(this,anchor).apply{
            menu.add("Copy").setOnMenuItemClickListener{
                imageAction(item,false);true
            }
            menu.add("Share").setOnMenuItemClickListener{
                imageAction(item,true);true
            }
            menu.add(if(item.pinned)"Unpin" else "Pin").setOnMenuItemClickListener{
                worker.execute {
                    runCatching{imageStore.pin(item.id)}
                        .onFailure{showError(it)}
                        .onSuccess{runOnUiThread{refreshScreenshots()}}
                };true
            }
            menu.add("Rename").setOnMenuItemClickListener{
                val field=EditText(this@KeyPopClipboardActivity).apply {
                    setText(item.label);setSingleLine(true)
                }
                AlertDialog.Builder(this@KeyPopClipboardActivity)
                    .setTitle("Rename screenshot").setView(field)
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Save"){_,_->
                        worker.execute {
                            runCatching{imageStore.rename(item.id,field.text.toString())}
                                .onFailure{showError(it)}
                                .onSuccess{runOnUiThread{refreshScreenshots()}}
                        }
                    }.show();true
            }
            menu.add("Delete").setOnMenuItemClickListener{
                AlertDialog.Builder(this@KeyPopClipboardActivity)
                    .setTitle("Delete saved screenshot?")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Delete"){_,_->
                        worker.execute{
                            runCatching{imageStore.delete(item.id)}
                                .onFailure{showError(it)}
                                .onSuccess{runOnUiThread{refreshScreenshots()}}
                        }
                    }.show();true
            }
            show()
        }
    }
    private fun imageAction(item:KeyPopImageClip,share:Boolean) {
        worker.execute{
            val uri=runCatching{imageStore.uriForShare(item)}.getOrNull()
            runOnUiThread{
                if(uri==null){
                    Toast.makeText(this,"Screenshot unavailable",Toast.LENGTH_SHORT).show()
                }else if(share){
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{
                        type=item.mime
                        putExtra(Intent.EXTRA_STREAM,uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },"Share screenshot"))
                }else{
                    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newUri(contentResolver,item.label,uri))
                    Toast.makeText(this,"Image copied for pasting",Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    override fun onDestroy() {
        generation++
        thumbnails.shutdownNow()
        worker.shutdown()
        super.onDestroy()
    }
}
