package com.aetherkeys.keyboard

import android.util.JsonReader
import android.util.JsonToken
import java.io.File
import java.io.InputStreamReader
import java.io.FileInputStream
import java.text.Normalizer
import java.util.Locale

/**
 * On-device BPE tokenizer for the remapped IndicTrans2 HuggingFace JSON exports.
 * The IDs in tokenizer_src/tokenizer_tgt are NOT SentencePiece-native IDs.
 * Uses streaming JSON parsing to avoid loading 24 MB documents into an extra DOM.
 *
 * The model exporter includes the language tags as added tokens. Translation
 * quality must still be checked against HuggingFace tokenizer golden vectors.
 */
internal class KeyPopIndicTokenizer(file:File) {
    private val vocabulary=HashMap<String,Int>(150000)
    private val reverse=HashMap<Int,String>(150000)
    private val merges=HashMap<String,Int>(260000)
    private val specials=HashSet<Int>()
    private val added=HashMap<String,Int>()
    private var unknownId=3
    private var endId=2
    private var metaspace="▁"
    private val split=Regex("\\s+")
    private val punctuation=Regex("([,;:!?()\\[\\]{}])")

    init {
        FileInputStream(file).use { stream ->
            JsonReader(InputStreamReader(stream,Charsets.UTF_8)).use {reader->
                reader.isLenient=false
                reader.beginObject()
                while(reader.hasNext()) {
                    when(reader.nextName()) {
                        "model" ->readModel(reader)
                        "added_tokens" ->readSpecials(reader)
                        "pre_tokenizer" ->readPretokenizer(reader)
                        else ->reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }
        require(vocabulary.isNotEmpty()){"Tokenizer vocabulary missing in "+file.name}
        if("<unk>" in vocabulary)unknownId=vocabulary.getValue("<unk>")
        if("</s>" in vocabulary)endId=vocabulary.getValue("</s>")
    }

    private fun readModel(reader:JsonReader) {
        reader.beginObject()
        while(reader.hasNext()) {
            when(reader.nextName()) {
                "vocab" -> {
                    reader.beginObject()
                    while(reader.hasNext()) {
                        val key=reader.nextName()
                        val id=reader.nextInt()
                        vocabulary[key]=id
                        reverse[id]=key
                    }
                    reader.endObject()
                }
                "merges" -> {
                    reader.beginArray()
                    var rank=0
                    while(reader.hasNext()) {
                        val pair=when(reader.peek()) {
                            JsonToken.BEGIN_ARRAY -> {
                                reader.beginArray()
                                val first=reader.nextString()
                                val second=reader.nextString()
                                while(reader.hasNext())reader.skipValue()
                                reader.endArray()
                                first+"\u0001"+second
                            }
                            else -> {
                                val line=reader.nextString()
                                val boundary=line.indexOf(' ')
                                if(boundary<0)"" else line.substring(0,boundary)+
                                    "\u0001"+line.substring(boundary+1)
                            }
                        }
                        if(pair.isNotEmpty())merges[pair]=rank
                        rank++
                    }
                    reader.endArray()
                }
                else ->reader.skipValue()
            }
        }
        reader.endObject()
    }
    private fun readSpecials(reader:JsonReader) {
        reader.beginArray()
        while(reader.hasNext()) {
            var id=-1
            var text=""
            var special=false
            reader.beginObject()
            while(reader.hasNext()) {
                when(reader.nextName()) {
                    "id" ->id=reader.nextInt()
                    "content" ->text=reader.nextString()
                    "special" ->special=reader.nextBoolean()
                    else ->reader.skipValue()
                }
            }
            reader.endObject()
            if(id>=0&&text.isNotBlank()) {
                added[text]=id
                if(special)specials.add(id)
                reverse[id]=text
                vocabulary[text]=id
            }
        }
        reader.endArray()
    }
    private fun readPretokenizer(reader:JsonReader) {
        // Metaspace replacement is recorded in the JSON, but can be nested
        // inside a Sequence. Detect it without materializing the whole graph.
        fun traverse() {
            when(reader.peek()) {
                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject()
                    while(reader.hasNext()){
                        val name=reader.nextName()
                        if(name=="replacement" && reader.peek()==JsonToken.STRING)
                            metaspace=reader.nextString()
                        else traverse()
                    }
                    reader.endObject()
                }
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray()
                    while(reader.hasNext())traverse()
                    reader.endArray()
                }
                else ->reader.skipValue()
            }
        }
        traverse()
    }
    private fun bpe(segment:String):List<Int> {
        vocabulary[segment]?.let {return listOf(it)}
        val symbols=ArrayList<String>()
        var at=0
        while(at<segment.length) {
            val cp=segment.codePointAt(at)
            symbols.add(String(Character.toChars(cp)))
            at+=Character.charCount(cp)
        }
        while(symbols.size>1) {
            var bestIndex=-1
            var bestRank=Int.MAX_VALUE
            for(i in 0 until symbols.size-1) {
                val key=symbols[i]+"\u0001"+symbols[i+1]
                val rank=merges[key]?:continue
                if(rank<bestRank) {bestRank=rank;bestIndex=i}
            }
            if(bestIndex<0)break
            symbols[bestIndex]+=symbols.removeAt(bestIndex+1)
        }
        return symbols.map{vocabulary[it]?:unknownId}
    }

    fun encode(input:String,maxLength:Int=256):LongArray {
        require(maxLength>=4)
        val text=Normalizer.normalize(input.trim(),Normalizer.Form.NFC)
        if(text.isBlank())return longArrayOf(endId.toLong())
        val output=ArrayList<Long>()
        val words=text.replace(punctuation," $1 ")
            .split(split).filter{it.isNotBlank()}
        for(word in words) {
            val direct=added[word]?:vocabulary[word]?.takeIf {
                word.matches(Regex("[a-z]{3}_[A-Za-z]{4}"))
            }
            val ids=if(direct!=null)listOf(direct) else bpe(metaspace+word)
            for(id in ids) {
                if(output.size>=maxLength-1)break
                output.add(id.toLong())
            }
            if(output.size>=maxLength-1)break
        }
        output.add(endId.toLong())
        return output.toLongArray()
    }

    fun decode(tokens:List<Long>):String {
        val builder=StringBuilder()
        for(token in tokens) {
            val id=token.toInt()
            if(id in specials||id==0||id==1||id==2||id==3)continue
            builder.append(reverse[id].orEmpty())
        }
        return builder.toString().replace(metaspace," ")
            .replace(Regex("\\s+([.,!?;:؟،؛۔])"),"$1")
            .trim()
    }

    fun hasToken(text:String)=text in vocabulary
    fun tokenId(text:String)=vocabulary[text]
}
