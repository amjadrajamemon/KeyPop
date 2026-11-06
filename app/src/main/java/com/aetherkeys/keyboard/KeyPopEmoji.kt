package com.aetherkeys.keyboard

import android.content.Context
import org.json.JSONArray
import java.util.Locale

data class EmojiEntry(val symbol:String,val category:String,val keywords:String)

/** Searchable offline emoji catalog. No tracking or external requests. */
object KeyPopEmoji {
    val categories=listOf("Recent","Favorites","Faces","People","Nature","Food","Travel","Objects","Symbols")
    private fun group(category:String,rows:String):List<EmojiEntry> =
        rows.split("|").mapNotNull { item ->
            val values=item.trim().split(":",limit=2)
            if(values.size!=2 || values[0].isBlank())null
            else EmojiEntry(values[0],category,values[1])
        }
    val entries:List<EmojiEntry> =
        group("Faces","😀:grinning happy smile|😁:smile teeth happy|😂:laugh tears funny|🤣:rolling laughing|😊:smiling blush happy|🙂:smile neutral|🙃:upside down|😉:wink|😍:love eyes heart|🥰:love hearts|😘:kiss face|😗:kiss|😚:kiss closed eyes|😜:playful wink|😝:silly tongue|🤪:crazy silly|😎:cool sunglasses|🤩:star struck excited|🥳:party birthday celebration|🥺:pleading eyes|😔:sad down|😭:cry tears|😢:cry sad|😱:shock scared|😡:angry rage|🤯:mind blown|🤔:thinking question|🤫:quiet shush|😷:mask sick|🤒:fever ill|🥱:yawn tired|😴:sleep tired|💀:skull dead|👻:ghost|🤖:robot")
        +group("People","👍:thumb up yes agree|👎:thumb down dislike|👏:clap applause|🙌:raised hands celebrate|🙏:pray thanks please|🤝:handshake agreement|👋:wave hello bye|✌️:peace victory|🤞:fingers crossed wish|👌:okay hand|🤙:call me|💪:strong muscle|🫶:heart hands|🤲:open hands|🤦:facepalm|🤷:shrug|👀:eyes looking|👑:crown king|🧑:person|👩:woman|👨:man|👶:baby|🧑‍💻:developer coder computer|👨‍👩‍👧:family")
        +group("Nature","🐱:cat kitty|🐶:dog puppy|🐼:panda|🦊:fox|🐰:rabbit bunny|🐸:frog|🐻:bear|🦁:lion|🐯:tiger|🐘:elephant|🦋:butterfly|🐝:bee|🌸:cherry flower blossom|🌹:rose flower|🌻:sunflower|🌷:tulip flower|🌺:flower hibiscus|🌵:cactus|🌳:tree|🍀:clover lucky|🌈:rainbow|🌙:moon night|☀️:sun sunny|⭐:star|✨:sparkle stars|🔥:fire hot|💧:water drop|❄️:ice snow|⛈️:storm thunder|🌧️:rain")
        +group("Food","🍎:apple red fruit|🍌:banana|🍇:grapes|🍉:watermelon|🍓:strawberry|🥭:mango|🍑:peach|🍋:lemon|🍍:pineapple|🍒:cherries|🥑:avocado|🥕:carrot|🥦:broccoli|🍔:burger|🍟:fries|🍕:pizza|🌮:taco|🥪:sandwich|🥐:croissant|🍰:cake dessert|🎂:birthday cake|🍫:chocolate|🍪:cookie|☕:coffee|🍵:tea|🥤:drink soda")
        +group("Travel","🚗:car vehicle drive|🚕:taxi cab|🚌:bus|🚲:bicycle cycle|🏍️:motorcycle bike|🚆:train|✈️:airplane flight travel|🚀:rocket space|🛸:ufo|🚁:helicopter|⛵:sailboat|🚢:ship boat|🏠:home house|🏡:house garden|🏢:office building|🏥:hospital|🏫:school|🕌:mosque|🛕:temple|🏖️:beach|⛰️:mountain|🌍:earth globe|🗺️:map|🧭:compass")
        +group("Objects","📱:phone mobile|💻:laptop computer|⌨️:keyboard typing|🖥️:desktop computer|📸:camera photograph screenshot|🎧:headphones music|🎤:microphone sing|🔊:speaker sound|📺:television tv|💡:light bulb idea|🔒:lock secure|🔑:key password|📝:memo note write|📄:document page|📌:pin saved|📎:attachment paperclip|📁:folder|📅:calendar date|⏰:alarm clock|⌚:watch time|💰:money cash|💳:credit card payment|🎁:gift present|🎯:target goal|📦:package box")
        +group("Symbols","❤️:red heart love|🩷:pink heart|💜:purple heart|💙:blue heart|💚:green heart|🖤:black heart|💔:broken heart|💯:hundred perfect|✅:check approved yes|❌:cross no|❗:exclamation important|❓:question|⚠️:warning caution|ℹ️:information|➕:plus add|➖:minus|♻️:recycle|🔄:refresh restart|➡️:right arrow|⬅️:left arrow|⬆️:up arrow|⬇️:down arrow|✔️:tick done|☑️:checkbox|🔴:red circle|🟢:green circle|🔵:blue circle|⚫:black circle|⚪:white circle|🆕:new|🆗:okay ok")
    fun search(term:String):List<EmojiEntry> {
        val query=term.trim().lowercase(Locale.ROOT)
        if(query.isEmpty())return entries
        return entries.filter { it.keywords.contains(query,ignoreCase=true) ||
            it.symbol.contains(query) || it.category.lowercase(Locale.ROOT).contains(query) }
    }
    fun recents(context:Context):List<String> {
        val raw=context.getSharedPreferences("keypop_emoji",Context.MODE_PRIVATE)
            .getString("recent","[]")?:return emptyList()
        return try{
            val array=JSONArray(raw)
            (0 until array.length()).map{array.getString(it)}.take(36)
        }catch(_:Exception){emptyList()}
    }
    fun favorites(context:Context):Set<String> =
        context.getSharedPreferences("keypop_emoji",Context.MODE_PRIVATE)
            .getStringSet("favorite",emptySet())?.toSet()?:emptySet()

    fun record(context:Context,symbol:String) {
        val items=(listOf(symbol)+recents(context).filterNot{it==symbol}).take(36)
        val arr=JSONArray();items.forEach{arr.put(it)}
        context.getSharedPreferences("keypop_emoji",Context.MODE_PRIVATE)
            .edit().putString("recent",arr.toString()).apply()
    }
    fun toggleFavorite(context:Context,symbol:String) {
        val values=favorites(context).toMutableSet()
        if(!values.add(symbol))values.remove(symbol)
        context.getSharedPreferences("keypop_emoji",Context.MODE_PRIVATE)
            .edit().putStringSet("favorite",values).apply()
    }
    fun inCategory(context:Context,category:String,query:String):List<EmojiEntry> {
        val found=search(query)
        return when(category) {
            "Recent"->recents(context).mapNotNull{key->found.find{it.symbol==key}}
            "Favorites"->favorites(context).mapNotNull{key->found.find{it.symbol==key}}
            else->found.filter{it.category==category}
        }
    }
}
