package com.aetherkeys.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/** Design-only JSON backup: intentionally excludes clipboard, credentials and learned text. */
object KeyPopDesignProfile {
    private val intOptions=mapOf("theme" to 0..14,"height" to 36..90,
        "round" to 3..40,"spacing" to 1..10,"font" to 12..24,
        "handMode" to 0..2,"englishLayout" to 0..4,
        "keyboardPreset" to 0..5,"keyboardWidthPercent" to 65..100,
        "bottomLift" to 0..96)
    private val boolOptions=setOf("numberRow","hints","haptics","sound",
        "wordDelete","doubleSpace","autoCap","splitKeyboard","floating",
        "spaceCursor","highContrast","animatedThemes")
    fun export(context:Context):String {
        val p=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val settings=JSONObject()
        for((k,range) in intOptions)if(p.contains(k))settings.put(k,p.getInt(k,range.first))
        for(k in boolOptions)if(p.contains(k))settings.put(k,p.getBoolean(k,false))
        if(p.contains("customAccent"))settings.put("customAccent",p.getInt("customAccent",0))
        for(i in 0..2){
            val v=p.getString("customLayout_"+i,null)
            if(v!=null)settings.put("customLayout_"+i,JSONArray(v))
        }
        KeyPopKeyGeometry.keys(context).forEach{(k,v)->settings.put(k,v)}
        return JSONObject().put("format","KeyPopDesign").put("version",1)
            .put("appearance",settings).toString(2)
    }
    fun validate(text:String):Map<String,Any> {
        require(text.length in 5..131072){"Invalid file size"}
        val root=JSONObject(text)
        require(root.optString("format")=="KeyPopDesign" && root.optInt("version")==1){"Unsupported format"}
        val obj=root.getJSONObject("appearance")
        val result=linkedMapOf<String,Any>()
        for(k in obj.keys()){
            val v=obj.get(k)
            when {
                k in intOptions->{
                    require(v is Int && v in intOptions.getValue(k)){"Invalid numeric setting"}
                    result[k]=v
                }
                k in boolOptions->{
                    require(v is Boolean){"Invalid switch"}
                    result[k]=v
                }
                k=="customAccent"->{
                    require(v is Int){"Invalid accent"}
                    result[k]=v
                }
                k.matches(Regex("keyWidth_[012]_[012]_[0-9]"))->{
                    require(v is Int && v in 65..175){"Invalid key width"}
                    result[k]=v
                }
                k.startsWith("customLayout_") && k in (0..2).map{"customLayout_"+it}->{
                    require(v is JSONArray && v.length()==3){"Invalid rows"}
                    val englishIndex=if(k=="customLayout_0") (result["englishLayout"] as? Int
                        ?: obj.optInt("englishLayout",0)) else 0
                    val counts=if(k=="customLayout_0") when(englishIndex) {
                        1->listOf(10,10,6)
                        3->listOf(10,10,10)
                        4->listOf(10,10,7)
                        else->listOf(10,9,7)
                    } else listOf(10,9,7)
                    for(i in 0..2) {
                        val row=v.getJSONArray(i)
                        require(row.length()==counts[i]){"Invalid key count"}
                        for(j in 0 until row.length())
                            require(KeyPopLayouts.validKey(row.getString(j))){"Invalid key"}
                    }
                    result[k]=v.toString()
                }
            }
        }
        require(result.isNotEmpty()){"No recognized design settings"}
        return result
    }
    fun restore(context:Context,text:String):Int {
        val parsed=validate(text)
        val editor=context.getSharedPreferences("keypop",Context.MODE_PRIVATE).edit()
        for((k,v) in parsed){
            when(v){is Int->editor.putInt(k,v)
                is Boolean->editor.putBoolean(k,v)
                is String->editor.putString(k,v)}
        }
        check(editor.commit()){"Could not save design"}
        return parsed.size
    }
}

class KeyPopProfileActivity:Activity() {
    private val exportCode=501
    private val importCode=502
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val margin=(20*resources.displayMetrics.density).toInt()
        val box=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(margin,margin,margin,margin)
        }
        fun info(t:String,size:Float){
            box.addView(TextView(this).apply{text=t;textSize=size;setPadding(0,0,0,margin)})
        }
        info("KeyPop Design Studio",24f)
        info("Export your theme and keyboard layout, or bring your design onto another Android device. Passwords, clipboard and personal words are never exported.",14f)
        box.addView(Button(this).apply{
            text="Export keyboard design"
            setOnClickListener{
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{
                    type="application/json"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_TITLE,"KeyPop-Design.json")
                },exportCode)
            }
        })
        box.addView(Button(this).apply{
            text="Import keyboard design"
            setOnClickListener{
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
                    type="application/json"
                    addCategory(Intent.CATEGORY_OPENABLE)
                },importCode)
            }
        })
        setContentView(box)
    }
    @Deprecated("Activity Result API")
    override fun onActivityResult(code:Int,status:Int,data:Intent?){
        super.onActivityResult(code,status,data)
        if(status!=RESULT_OK)return
        val uri=data?.data?:return
        if(code==exportCode){
            val ok=runCatching{
                contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use{
                    it.write(KeyPopDesignProfile.export(this))
                }?:error("Cannot write")
            }.isSuccess
            Toast.makeText(this,if(ok)"Design exported" else "Export failed",Toast.LENGTH_LONG).show()
        } else if(code==importCode){
            val raw=runCatching{
                contentResolver.openInputStream(uri)?.bufferedReader()?.use{
                    val buffer=CharArray(131073)
                    val text=StringBuilder()
                    while(true){
                        val n=it.read(buffer)
                        if(n<0)break
                        text.append(buffer,0,n)
                        require(text.length<=131072){"File too large"}
                    }
                    text.toString()
                }?:error("Cannot read")
            }.getOrElse{
                Toast.makeText(this,"Unable to read design",Toast.LENGTH_LONG).show()
                return
            }
            val valid=runCatching{KeyPopDesignProfile.validate(raw)}
            if(valid.isFailure){
                Toast.makeText(this,"Invalid design file",Toast.LENGTH_LONG).show()
                return
            }
            AlertDialog.Builder(this).setTitle("Import design?")
                .setMessage("Apply supported theme and key-layout settings? Your clipboard and logins remain untouched.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Import"){_,_->
                    val ok=runCatching{KeyPopDesignProfile.restore(this,raw)}.isSuccess
                    Toast.makeText(this,if(ok)"Design imported. Reopen your keyboard." else "Import failed",Toast.LENGTH_LONG).show()
                }.show()
        }
    }
}
