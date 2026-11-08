package com.aetherkeys.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** Full, searchable encrypted screenshot gallery. Thumbnails are off-UI.
 * Destructive actions live behind an overflow menu, not a five-button row. */
class KeyPopScreenshotHistoryActivity:Activity() {
    private val store by lazy{KeyPopImageStore(this)}
    private val pool=Executors.newFixedThreadPool(2)
    private lateinit var list:LinearLayout
    private lateinit var search:EditText
    private var shown=24
    private var requestId=0
    private val request=301
    private val ink=0xFFF1F9F6.toInt()
    private val accent=0xFF89E6CE.toInt()
    private val muted=0xFFACC5C4.toInt()
    private val ground=0xFF0B252B.toInt()
    private val surface=0xFF193940.toInt()
    private val outline=0xFF3C6768.toInt()
    private fun dp(x:Int)=(x*resources.displayMetrics.density).toInt()
    private fun text(value:String,size:Float=14f,color:Int=ink)=TextView(this).apply{
        text=value;textSize=size;setTextColor(color)
        setPadding(dp(4),dp(5),dp(4),dp(5))
    }
    private fun button(value:String,click:()->Unit)=Button(this).apply{
        text=value;isAllCaps=false;textSize=13f;setTextColor(ink)
        minHeight=0;minimumHeight=0;minWidth=0;minimumWidth=0
        background=GradientDrawable().apply{
            setColor(surface);cornerRadius=dp(12).toFloat()
            setStroke(dp(1),outline)
        }
        setOnClickListener{click()}
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=ground
        window.navigationBarColor=0xFF0C1D26.toInt()
        val wrapper=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(14),dp(12),dp(14),dp(10))
            setBackgroundColor(ground)
        }
        val header=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        }
        header.addView(button("‹"){finish()},LinearLayout.LayoutParams(dp(44),dp(44)))
        header.addView(text("Screenshot history",21f),
            LinearLayout.LayoutParams(0,dp(50),1f))
        val menu=button("⋮"){}
        header.addView(menu,LinearLayout.LayoutParams(dp(43),dp(44)))
        menu.setOnClickListener{
            PopupMenu(this,menu).apply{
                this.menu.add("Import images").setOnMenuItemClickListener{
                    importMedia();true
                }
                this.menu.add("Screenshot detection settings").setOnMenuItemClickListener{
                    startActivity(Intent(this@KeyPopScreenshotHistoryActivity,
                        KeyPopScreenshotAccessActivity::class.java));true
                }
                show()
            }
        }
        wrapper.addView(header)
        wrapper.addView(text("Encrypted and stored on this device",12f,muted))
        search=EditText(this).apply{
            hint="Search saved screenshots"
            textSize=14f;setSingleLine(true)
            setHintTextColor(muted);setTextColor(ink)
            setPadding(dp(13),0,dp(13),0)
            background=GradientDrawable().apply{
                setColor(surface);cornerRadius=dp(12).toFloat()
                setStroke(dp(1),outline)
            }
        }
        wrapper.addView(search,LinearLayout.LayoutParams(-1,dp(46)).apply{
            topMargin=dp(9);bottomMargin=dp(7)
        })
        val scroll=ScrollView(this).apply {
            isVerticalScrollBarEnabled=false
        }
        list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        scroll.addView(list)
        wrapper.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(wrapper)
        search.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                shown=24;render()
            }
            override fun afterTextChanged(s:Editable?){}
        })
        render()
    }
    override fun onDestroy(){requestId++;pool.shutdownNow();super.onDestroy()}
    override fun onResume(){super.onResume();if(::list.isInitialized)render()}
    private fun importMedia(){
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply{
            type="image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
            addCategory(Intent.CATEGORY_OPENABLE)
        },request)
    }
    @Deprecated("Activity Result API")
    override fun onActivityResult(req:Int,status:Int,data:Intent?){
        super.onActivityResult(req,status,data)
        if(req!=request || status!=RESULT_OK)return
        val uris=ArrayList<Uri>()
        data?.data?.let{uris.add(it)}
        data?.clipData?.let{clips->
            for(i in 0 until clips.itemCount)clips.getItemAt(i).uri?.let{uris.add(it)}
        }
        pool.execute{
            var imported=0
            for(uri in uris.distinct().take(40))
                if(runCatching{store.save(uri)}.getOrDefault(false))imported++
            runOnUiThread{
                if(!isFinishing){
                    Toast.makeText(this,imported.toString()+" images imported",
                        Toast.LENGTH_SHORT).show()
                    render()
                }
            }
        }
    }
    private fun render(){
        if(!::list.isInitialized)return
        val gen=++requestId
        val term=search.text.toString()
        val count=shown
        list.removeAllViews()
        list.addView(text("Loading screenshots…",12f,muted))
        pool.execute {
            val result=runCatching{store.search(term)}
            runOnUiThread{
                if(isFinishing || gen!=requestId)return@runOnUiThread
                list.removeAllViews()
                result.onFailure {
                    list.addView(text("Saved screenshots are unavailable. "+
                        "Your archive was not deleted.",13f))
                }.onSuccess{all->
                    list.addView(text(all.size.toString()+" saved screenshots",12f,accent))
                    if(all.isEmpty()){
                        list.addView(text("No screenshots saved yet",18f))
                        list.addView(text("Allow screenshot detection, or import images "+
                            "manually. Previously saved images are never deleted automatically.",
                            13f,muted))
                        list.addView(button("Set up screenshot detection"){
                            startActivity(Intent(this,KeyPopScreenshotAccessActivity::class.java))
                        },LinearLayout.LayoutParams(-1,dp(45)).apply{topMargin=dp(12)})
                        list.addView(button("Import images"){
                            importMedia()
                        },LinearLayout.LayoutParams(-1,dp(43)).apply{topMargin=dp(7)})
                    }else{
                        var row:LinearLayout?=null
                        all.take(count).forEachIndexed{index,image->
                            if(index%2==0){
                                row=LinearLayout(this).apply{
                                    orientation=LinearLayout.HORIZONTAL
                                }
                                list.addView(row,LinearLayout.LayoutParams(-1,-2))
                            }
                            row?.addView(imageCard(image),
                                LinearLayout.LayoutParams(0,-2,1f).apply {
                                    leftMargin=if(index%2==1)dp(4) else 0
                                    rightMargin=if(index%2==0)dp(4) else 0
                                    bottomMargin=dp(8)
                                })
                        }
                    }
                    if(count<all.size)list.addView(button("Load more screenshots"){
                        shown+=24;render()
                    },LinearLayout.LayoutParams(-1,dp(44)))
                }
            }
        }
    }
    private fun imageCard(image:KeyPopImageClip):View{
        val card=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(8),dp(8),dp(8),dp(8))
            background=GradientDrawable().apply{
                setColor(surface);cornerRadius=dp(13).toFloat()
            }
        }
        val preview=ImageView(this).apply{
            contentDescription=image.label
            scaleType=ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF265158.toInt())
            setOnClickListener{showActions(this,image)}
        }
        card.addView(preview,LinearLayout.LayoutParams(-1,dp(128)))
        pool.execute {
            val bmp=runCatching{store.thumbnail(image,256)}.getOrNull()
            if(bmp!=null)runOnUiThread{
                if(preview.isAttachedToWindow)preview.setImageBitmap(bmp)
                else bmp.recycle()
            }
        }
        val bottom=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        }
        bottom.addView(text((if(image.pinned)"📌 " else "")+"Saved "+
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(image.time)),
            11f,muted),LinearLayout.LayoutParams(0,dp(39),1f))
        val more=button("⋮"){}
        bottom.addView(more,LinearLayout.LayoutParams(dp(41),dp(40)))
        more.setOnClickListener{showActions(more,image)}
        card.setOnLongClickListener{showActions(more,image);true}
        card.addView(bottom)
        return card
    }
    private fun showActions(anchor:View,image:KeyPopImageClip) {
        PopupMenu(this,anchor).apply{
            menu.add("Copy").setOnMenuItemClickListener{
                exportImage(image,false);true
            }
            menu.add("Share").setOnMenuItemClickListener{
                exportImage(image,true);true
            }
            menu.add(if(image.pinned)"Unpin" else "Pin").setOnMenuItemClickListener{
                pool.execute{
                    runCatching{store.pin(image.id)}
                    runOnUiThread{if(!isFinishing)render()}
                };true
            }
            menu.add("Rename").setOnMenuItemClickListener{
                val field=EditText(this@KeyPopScreenshotHistoryActivity).apply{
                    setText(image.label);setSingleLine(true)
                }
                AlertDialog.Builder(this@KeyPopScreenshotHistoryActivity)
                    .setTitle("Rename screenshot").setView(field)
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Save"){_,_->
                        pool.execute {
                            runCatching{store.rename(image.id,field.text.toString())}
                            runOnUiThread{if(!isFinishing)render()}
                        }
                    }.show();true
            }
            menu.add("Delete").setOnMenuItemClickListener{
                AlertDialog.Builder(this@KeyPopScreenshotHistoryActivity)
                    .setTitle("Delete saved screenshot?")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Delete"){_,_->
                        pool.execute {
                            runCatching{store.delete(image.id)}
                            runOnUiThread{if(!isFinishing)render()}
                        }
                    }.show();true
            }
            show()
        }
    }
    private fun exportImage(image:KeyPopImageClip,share:Boolean) {
        pool.execute{
            val uri=runCatching{store.uriForShare(image)}.getOrNull()
            runOnUiThread{
                if(uri==null){
                    Toast.makeText(this,"Screenshot unavailable",
                        Toast.LENGTH_SHORT).show()
                }else if(share){
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{
                        type=image.mime
                        putExtra(Intent.EXTRA_STREAM,uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },"Share screenshot"))
                }else{
                    val clipboard=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newUri(contentResolver,image.label,uri))
                    Toast.makeText(this,"Image copied for pasting",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
