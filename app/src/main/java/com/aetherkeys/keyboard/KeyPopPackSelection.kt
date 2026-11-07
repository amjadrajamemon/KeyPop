package com.aetherkeys.keyboard

/** Explicit direction selection. Download only models needed for chosen pairs. */
object KeyPopPackSelection {
    val directions=linkedMapOf(
        "en-ur" to "English → Urdu",
        "ur-en" to "Urdu → English",
        "en-sd" to "English → Sindhi",
        "sd-en" to "Sindhi → English",
        "ur-sd" to "Urdu → Sindhi",
        "sd-ur" to "Sindhi → Urdu"
    )
    fun required(selected:Set<String>):List<String> {
        val filtered=selected.intersect(directions.keys)
        val items=linkedSetOf<String>()
        if("en-ur" in filtered || "ur-en" in filtered) {
            items.add("en");items.add("ur")
        }
        if("en-sd" in filtered)items.add("en-sd")
        if("sd-en" in filtered)items.add("sd-en")
        if("ur-sd" in filtered || "sd-ur" in filtered)items.add("ur-sd")
        return items.toList()
    }
    fun selectionLabel(selected:Set<String>) =
        if(selected.isEmpty())"Choose at least one translation direction."
        else "${selected.size} directions · ${required(selected).size} packs required"
}
