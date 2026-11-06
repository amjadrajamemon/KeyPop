package com.aetherkeys.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File

/**
 * A CPU-only graph compatibility check, not an inference implementation.
 * Loads exactly one ONNX encoder in a background thread and immediately closes it.
 * Decoder loops, tokenization and IndicProcessor script normalization are not yet here.
 */
object KeyPopSindhiOnnxProbe {
    data class Report(val ok:Boolean,val message:String)
    fun inspect(context:Context,bundle:KeyPopSindhiOnnxPack.Bundle,
                done:(Report)->Unit) {
        val ui=Handler(Looper.getMainLooper())
        val app=context.applicationContext
        Thread({
            val result=runCatching {
                val missing=KeyPopSindhiOnnxPack.missing(app,bundle)
                require(missing.isEmpty()) {
                    "Bundle incomplete. Missing "+missing.size+" model files."
                }
                val folder=KeyPopSindhiOnnxPack.folder(app,bundle)
                val encoder=File(folder,"encoder_model.onnx")
                require(encoder.isFile && encoder.length()>0L) {
                    "ONNX encoder file not found."
                }
                val environment=OrtEnvironment.getEnvironment()
                OrtSession.SessionOptions().use { options->
                    options.setIntraOpNumThreads(2)
                    environment.createSession(encoder.absolutePath,options).use { session->
                        val inputs=session.inputNames.joinToString(", ")
                        val outputs=session.outputNames.joinToString(", ")
                        Report(true,"ONNX encoder loaded successfully for "+
                            bundle.name+". Inputs: "+inputs+"; outputs: "+outputs+
                            ". Text translation still requires the tokenizer and decoder.")
                    }
                }
            }.getOrElse {
                Report(false,"ONNX compatibility test failed: "+
                    (it.localizedMessage ?: it.javaClass.simpleName))
            }
            ui.post { done(result) }
        },"keypop-sindhi-onnx-probe").start()
    }
}
