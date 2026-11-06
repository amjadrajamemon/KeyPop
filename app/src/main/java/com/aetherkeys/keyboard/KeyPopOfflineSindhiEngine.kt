package com.aetherkeys.keyboard

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.text.Normalizer
import java.util.concurrent.Executors

/**
 * IndicTrans2 offline neural translation. One native model is held in memory
 * at a time to avoid three 200-320M weight sets being mapped simultaneously.
 * All CPU inference and model initialization happen on a dedicated worker.
 *
 * Model-specific correctness must be proven on real sentences before release.
 */
object KeyPopOfflineSindhiEngine {
    private val worker=Executors.newSingleThreadExecutor { action ->
        Thread(action,"keypop-sindhi-inference").apply{isDaemon=true}
    }
    private val ui by lazy {Handler(Looper.getMainLooper())}
    private var loaded:Loaded?=null
    private val maxTokens=96
    private val maxSourceTokens=256

    fun supports(source:String,target:String):Boolean =
        (source=="en" && target=="sd") || (source=="sd" && target=="en") ||
            (source=="ur" && target=="sd") || (source=="sd" && target=="ur")

    fun chosen(source:String,target:String):KeyPopSindhiOnnxPack.Bundle? =
        when {
            source=="en" && target=="sd" ->KeyPopSindhiOnnxPack.englishToSindhi
            source=="sd" && target=="en" ->KeyPopSindhiOnnxPack.sindhiToEnglish
            (source=="sd"&&target=="ur")||(source=="ur"&&target=="sd") ->
                KeyPopSindhiOnnxPack.urduSindhi
            else ->null
        }

    /** Returns only real model availability, not phrasebook availability. */
    fun ready(context:Context,source:String,target:String):Boolean {
        val pack=chosen(source,target)?:return false
        return KeyPopSindhiOnnxPack.missing(context,pack).isEmpty()
    }

    private fun lang(language:String)=when(language){
        "en"->"eng_Latn";"ur"->"urd_Arab";"sd"->"snd_Arab"
        else ->error("Unknown translation language: "+language)
    }

    fun translate(context:Context,source:String,target:String,text:String,
                  callback:(Result<String>)->Unit) {
        val app=context.applicationContext
        val pack=chosen(source,target)
        if(pack==null) {
            callback(Result.failure(IllegalArgumentException("Unsupported offline direction")))
            return
        }
        worker.execute {
            val result=runCatching {
                require(text.isNotBlank()){"Nothing to translate"}
                val missing=KeyPopSindhiOnnxPack.missing(app,pack)
                check(missing.isEmpty()) {
                    pack.name+" offline model is not installed. Missing "+
                        missing.size+" files. Download and verify it in Language Packs."
                }
                val model=obtain(app,pack)
                model.run(text,lang(source),lang(target))
            }
            ui.post{callback(result)}
        }
    }

    private fun obtain(context:Context,pack:KeyPopSindhiOnnxPack.Bundle):Loaded {
        val current=loaded
        if(current!=null && current.pack.id==pack.id)return current
        current?.close()
        loaded=null
        val model=Loaded(KeyPopSindhiOnnxPack.folder(context,pack),pack)
        loaded=model
        return model
    }

    /** No network APIs or remote HTTP endpoints in the inference path. */
    private class Loaded(val folder:File,val pack:KeyPopSindhiOnnxPack.Bundle):AutoCloseable {
        private val env=OrtEnvironment.getEnvironment()
        private val options=OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setInterOpNumThreads(1)
        }
        private val encoder:OrtSession
        private val decoder:OrtSession
        private val decoderPast:OrtSession
        private val sourceTokenizer:KeyPopIndicTokenizer
        private val targetTokenizer:KeyPopIndicTokenizer
        private val sourceVocab:Int
        private val targetVocab:Int
        private val unknown:Int
        private val startToken:Int
        private val endToken:Int

        init {
            val meta=JSONObject(File(folder,"tokenizer_meta.json").readText())
            sourceVocab=meta.getInt("src_dict_size")
            targetVocab=meta.getInt("tgt_dict_size")
            unknown=meta.optInt("unk_id",3)
            val config=JSONObject(File(folder,"generation_config.json").readText())
            startToken=config.optInt("decoder_start_token_id",2)
            endToken=config.optInt("eos_token_id",2)
            sourceTokenizer=KeyPopIndicTokenizer(File(folder,"tokenizer_src.json"))
            targetTokenizer=KeyPopIndicTokenizer(File(folder,"tokenizer_tgt.json"))
            encoder=env.createSession(File(folder,"encoder_model.onnx").absolutePath,options)
            decoder=env.createSession(File(folder,"decoder_model.onnx").absolutePath,options)
            decoderPast=env.createSession(File(folder,"decoder_with_past_model.onnx").absolutePath,options)
        }

        private fun tensor(ids:LongArray):OnnxTensor =
            OnnxTensor.createTensor(env,LongBuffer.wrap(ids),
                longArrayOf(1,ids.size.toLong()))

        fun run(source:String,from:String,to:String):String {
            val normalized=preprocess(source,from,to)
            val encoded=sourceTokenizer.encode(normalized,maxSourceTokens)
            require(encoded.isNotEmpty()){"Source tokenizer returned no input"}
            for(i in encoded.indices) {
                if(encoded[i]>=sourceVocab)encoded[i]=unknown.toLong()
            }
            val mask=LongArray(encoded.size){1L}
            val encIds=tensor(encoded)
            val encMask=tensor(mask)
            val encoderOutput=try {
                encoder.run(mapOf("input_ids" to encIds,"attention_mask" to encMask))
            } finally {
                encIds.close()
                encMask.close()
            }
            try {
                val hiddenTensor=encoderOutput[0] as OnnxTensor
                val hiddenShape=hiddenTensor.info.shape
                require(hiddenShape.size==3) {
                    "Unexpected encoder output rank "+hiddenShape.size
                }
                val hiddenData=hiddenTensor.floatBuffer
                val floats=FloatArray(hiddenData.remaining())
                hiddenData.get(floats)
                val tokenIds=generate(floats,hiddenShape,mask)
                val decoded=targetTokenizer.decode(tokenIds)
                val result=postprocess(decoded,to)
                check(result.isNotBlank()){"Model returned an empty translation"}
                return result
            } finally {encoderOutput.close()}
        }

        private fun generate(hidden:FloatArray,shape:LongArray,mask:LongArray):List<Long> {
            val maskTensor=tensor(mask)
            val hiddenTensor=OnnxTensor.createTensor(env,FloatBuffer.wrap(hidden),shape)
            var previous:OrtSession.Result?=null
            val ids=ArrayList<Long>()
            var next=startToken.toLong()
            try {
                val cacheInputs=decoderPast.inputNames
                for(step in 0 until maxTokens) {
                    val input=tensor(longArrayOf(next))
                    try {
                        val arguments=linkedMapOf<String,OnnxTensor>(
                            "input_ids" to input,
                            "encoder_attention_mask" to maskTensor
                        )
                        val old=previous
                        if(step==0) {
                            arguments["encoder_hidden_states"]=hiddenTensor
                        } else {
                            check(old!=null){"Decoder cache absent"}
                            var cacheIndex=1
                            for(layer in 0 until (old.size()-1)/4) {
                                for(component in listOf("decoder.key","decoder.value",
                                    "encoder.key","encoder.value")) {
                                    val key="past_key_values."+layer+"."+component
                                    if(key in cacheInputs) {
                                        arguments[key]=old[cacheIndex] as OnnxTensor
                                    }
                                    cacheIndex++
                                }
                            }
                            val required=cacheInputs.filter{it.startsWith("past_key_values.")}
                            check(required.all{it in arguments}) {
                                "Decoder KV cache mismatch: expected "+required.size+" inputs"
                            }
                        }
                        val result=if(step==0)decoder.run(arguments)
                            else decoderPast.run(arguments)
                        val token=bestToken(result[0] as OnnxTensor)
                        old?.close()
                        previous=result
                        next=if(token>=targetVocab)unknown.toLong() else token
                        if(next==endToken.toLong())break
                        ids.add(next)
                    } finally {input.close()}
                }
            } finally {
                previous?.close()
                hiddenTensor.close()
                maskTensor.close()
            }
            return ids
        }

        private fun bestToken(logits:OnnxTensor):Long {
            val shape=logits.info.shape
            require(shape.size==3 && shape.last()>1) {
                "Unexpected decoder logits shape "+shape.joinToString()
            }
            val vocab=shape.last().toInt()
            val buffer=logits.floatBuffer
            val end=buffer.limit()
            require(end>=vocab){"Invalid model logits buffer length"}
            buffer.position(end-vocab)
            var best=Float.NEGATIVE_INFINITY
            var index=0
            for(i in 0 until vocab) {
                val value=buffer.get()
                if(value>best){best=value;index=i}
            }
            return index.toLong()
        }

        override fun close() {
            decoderPast.close()
            decoder.close()
            encoder.close()
            options.close()
        }
    }

    /** Arabic-script source text is NOT transliterated to Devanagari. */
    internal fun preprocess(original:String,from:String,to:String):String {
        var normalized=Normalizer.normalize(original.trim(),Normalizer.Form.NFC)
        normalized=normalized.replace(Regex("\\s+")," ")
            .replace(Regex("\\s*([،؛؟۔])\\s*")," $1 ")
            .replace(Regex("\\s+")," ").trim()
        // The reference IndicProcessor prefixes exactly two language tags.
        return from+" "+to+" "+normalized
    }

    internal fun postprocess(text:String,to:String):String {
        var output=Normalizer.normalize(text,Normalizer.Form.NFC)
            .replace(Regex("\\s+([؟۔،؛.!?])"),"$1")
            .replace("ٮ۪","ؠ")
            .trim()
        if(to=="snd_Arab"||to=="urd_Arab") {
            output=output.replace(Regex("[\\u200e\\u200f\\u202a-\\u202e]"),"")
        }
        return output
    }
}
