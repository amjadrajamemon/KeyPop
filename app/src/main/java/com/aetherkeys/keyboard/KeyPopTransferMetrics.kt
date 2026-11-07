package com.aetherkeys.keyboard

import java.util.Locale
import kotlin.math.max

/** User-facing transfer math. Byte totals are estimates unless the server
 * reports actual Content-Length; ML Kit transfers expose no byte counts. */
object KeyPopTransferMetrics {
    const val MIB=1024L*1024L
    fun mb(bytes:Long):String=String.format(Locale.US,"%.1f",bytes.coerceAtLeast(0L)/MIB.toDouble())
    fun elapsed(seconds:Long):String {
        val value=seconds.coerceAtLeast(0L)
        return when {
            value<60L->"${value}s"
            value<3600L->"${value/60}m ${value%60}s"
            else->"${value/3600}h ${(value%3600)/60}m"
        }
    }
    fun percent(downloaded:Long,estimated:Long):Int =
        if(estimated<=0L)0 else
            ((100.0*downloaded.coerceAtLeast(0L)/estimated).toInt()).coerceIn(0,99)
    fun remaining(downloaded:Long,estimated:Long):Long =
        max(0L,estimated-downloaded)
    fun etaSeconds(downloaded:Long,estimated:Long,bytesPerSecond:Double):Long? =
        if(estimated<=0 || bytesPerSecond<=1024.0 || !bytesPerSecond.isFinite())null
        else (remaining(downloaded,estimated)/bytesPerSecond).toLong().coerceAtLeast(0L)
    /** Once selected files are verified, estimated budget is not an outstanding
     * obligation: actual compressed/hosted bytes may be smaller than estimate. */
    fun completedSummary(verifiedBytes:Long,selectedCount:Int,elapsedSeconds:Long):String =
        "Completed · "+selectedCount+" selected model pack"+
        (if(selectedCount==1)"" else "s")+" processed\n"+
        "Verified files on device: "+mb(verifiedBytes)+" MB\n"+
        "Time: "+elapsed(elapsedSeconds)+"\n"+
        "Remaining download: none for the selected packs.\n"+
        "Planning estimates may differ from actual model file sizes; "+
        "download completion is not a translation-accuracy test."
    fun summary(downloaded:Long,estimated:Long,bytesPerSecond:Double,
                includesPlayServices:Boolean):String {
        val google=if(includesPlayServices)" · Google ML Kit size not reported" else ""
        if(estimated<=0L)return "Google ML Kit manages download size and speed; progress is per pack."
        val speed=if(bytesPerSecond>1024)" · ${mb(bytesPerSecond.toLong())} MB/s" else " · measuring speed…"
        val eta=etaSeconds(downloaded,estimated,bytesPerSecond)?.let{
            " · approx. ${elapsed(it)} left"
        }?:" · ETA calculating…"
        return "${mb(downloaded)} MB / ~${mb(estimated)} MB · ~${mb(remaining(downloaded,estimated))} MB left"+
            speed+eta+google
    }
}
