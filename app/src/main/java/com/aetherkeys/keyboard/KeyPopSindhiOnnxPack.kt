package com.aetherkeys.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Experimental downloadable model-file infrastructure for Sindhi translation.
 * Files are real, but an ONNX tokenizer/decoder is still needed for inference.
 * Never equate "files downloaded" with "offline translation ready".
 */
object KeyPopSindhiOnnxPack {
    data class Bundle(
        val id:String, val name:String, val repo:String,
        val revision:String, val approximateMB:Int
    ) {
        val files=listOf("config.json","generation_config.json",
            "encoder_model.onnx","encoder_model.onnx.data",
            "decoder_model.onnx","decoder_with_past_model.onnx",
            "decoder_shared.onnx.data","tokenizer_src.json",
            "tokenizer_tgt.json","tokenizer_meta.json")
        fun url(file:String)= "https://huggingface.co/"+repo+
            "/resolve/"+revision+"/"+file+"?download=true"
    }
    val englishToSindhi=Bundle("en-sd","English → Sindhi (Arabic)",
        "hari31416/indictrans2-en-indic-dist-200M-ONNX-int8","1eaa43a",318)
    val sindhiToEnglish=Bundle("sd-en","Sindhi (Arabic) → English",
        "hari31416/indictrans2-indic-en-dist-200M-ONNX-int8","a7ecdc0",270)
    val bundles=listOf(englishToSindhi,sindhiToEnglish)
    private val main=Handler(Looper.getMainLooper())
    private val running=AtomicBoolean(false)
    private val shaPattern=Regex("[a-fA-F0-9]{64}")
    data class Progress(
        val bundleId:String,val fileName:String,val index:Int,val count:Int,
        val bytes:Long,val total:Long?,val message:String,val complete:Boolean=false
    )
    private fun root(context:Context)=File(context.filesDir,"indictrans2-onnx")
    fun folder(context:Context,bundle:Bundle)=File(root(context),bundle.id)
    fun missing(context:Context,bundle:Bundle):List<String> =
        bundle.files.filter {file ->
            val dest=File(folder(context,bundle),file)
            !dest.isFile || dest.length()==0L
        }
    fun status(context:Context,bundle:Bundle):String {
        val count=bundle.files.size-missing(context,bundle).size
        return if(count==bundle.files.size)
            "All "+count+" files present. Translation runtime NOT enabled."
        else count.toString()+"/"+bundle.files.size+" files downloaded ("+
            bundle.approximateMB+" MB approx. full pack)"
    }
    fun delete(context:Context,bundle:Bundle):Boolean =
        !running.get() && (!folder(context,bundle).exists() ||
            folder(context,bundle).deleteRecursively())
    private fun publish(callback:(Progress)->Unit,p:Progress) {
        main.post {callback(p)}
    }
    private fun sha256(file:File):String {
        val sum=MessageDigest.getInstance("SHA-256")
        file.inputStream().use {input->
            val buffer=ByteArray(128*1024)
            while(true) {
                val n=input.read(buffer)
                if(n<0)break
                sum.update(buffer,0,n)
            }
        }
        return sum.digest().joinToString("") {"%02x".format(it)}
    }
    private fun upstreamSha(connection:HttpURLConnection):String? {
        val value=(connection.getHeaderField("X-Linked-Etag")
            ?: connection.getHeaderField("ETag") ?: "").trim().trim('"')
        return value.takeIf{shaPattern.matches(it)}
    }
    private fun fileDownload(bundle:Bundle,destDir:File,name:String,progress:(Long,Long?)->Unit) {
        require(name in bundle.files && name.none{it=='/' || it=='\\'})
        val file=File(destDir,name)
        if(file.isFile && file.length()>0L)return
        val part=File(destDir,name+".part")
        var offset=if(part.isFile)part.length() else 0L
        fun connect():HttpURLConnection {
            val conn=(URL(bundle.url(name)).openConnection() as HttpURLConnection)
            conn.connectTimeout=25000
            conn.readTimeout=60000
            conn.instanceFollowRedirects=true
            conn.setRequestProperty("Accept-Encoding","identity")
            conn.setRequestProperty("User-Agent","KeyPop-Sindhi-ONNX/0.9.3")
            if(offset>0)conn.setRequestProperty("Range","bytes="+offset+"-")
            return conn
        }
        var connection=connect()
        var status=connection.responseCode
        if(status==416) {
            connection.disconnect();part.delete();offset=0L
            connection=connect();status=connection.responseCode
        }
        try {
            if(status!=200 && status!=206)
                error("HTTP "+status+" downloading "+name)
            val resume=(status==206 && offset>0L)
            if(!resume)offset=0L
            val declared=connection.getHeaderFieldLong("Content-Length",-1L)
                .takeIf{it>=0L}?.plus(offset)
            val expectedSha=upstreamSha(connection)
            FileOutputStream(part,resume).use {output->
                connection.inputStream.use {input->
                    val buf=ByteArray(128*1024)
                    var count=offset
                    var reported=offset
                    while(true) {
                        val n=input.read(buf)
                        if(n<0)break
                        output.write(buf,0,n);count+=n
                        if(count-reported>=512*1024L) {
                            progress(count,declared);reported=count
                        }
                    }
                    output.fd.sync()
                    if(declared!=null && count!=declared)
                        error("Incomplete transfer: "+count+"/"+declared)
                    if(count==0L)error("Empty model file: "+name)
                    progress(count,declared)
                }
            }
            val digest=sha256(part)
            if(expectedSha!=null && !expectedSha.equals(digest,true)) {
                part.delete()
                error("SHA-256 mismatch on "+name)
            }
            if(file.exists() && !file.delete())error("Cannot replace "+name)
            if(!part.renameTo(file))error("Cannot finalize "+name)
            File(destDir,name+".integrity").writeText(
                JSONObject().put("sha256",digest)
                    .put("upstreamVerified",expectedSha!=null)
                    .put("revision",bundle.revision).toString())
        } finally { connection.disconnect() }
    }
    /**
     * User-triggered; runs outside the UI thread, resuming partial files on retry.
     * This method does NOT install a functioning Sindhi translation engine.
     */
    fun download(context:Context,bundle:Bundle,notify:(Progress)->Unit):Boolean {
        if(!running.compareAndSet(false,true))return false
        val app=context.applicationContext
        Thread({
            var index=0
            try {
                val dir=folder(app,bundle)
                if(!dir.exists() && !dir.mkdirs())error("Cannot create private model directory")
                val free=StatFs(dir.absolutePath).availableBytes
                val minBytes=(bundle.approximateMB.toLong()+100)*1024L*1024L
                if(free<minBytes && missing(app,bundle).size>bundle.files.size/2)
                    error("At least "+(bundle.approximateMB+100)+" MB free space required")
                bundle.files.forEachIndexed {position,name->
                    index=position+1
                    publish(notify,Progress(bundle.id,name,index,bundle.files.size,0,null,
                        "Downloading file "+index+"/"+bundle.files.size+": "+name))
                    fileDownload(bundle,dir,name) {bytes,total->
                        publish(notify,Progress(bundle.id,name,index,bundle.files.size,
                            bytes,total,"Downloading "+name))
                    }
                }
                publish(notify,Progress(bundle.id,"",bundle.files.size,bundle.files.size,
                    0,null,"Model files downloaded; translation inference NOT yet available.",true))
            } catch(e:Exception) {
                publish(notify,Progress(bundle.id,"",index,bundle.files.size,0,null,
                    "Stopped: "+(e.localizedMessage?:"unknown error")+
                    ". Retry to resume partial files.",true))
            } finally {running.set(false)}
        },"keypop-sindhi-model-files").start()
        return true
    }
}
