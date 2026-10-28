package com.lumora.player

/** Normalize common Arabic and Latin spelling variants for local catalog search. */
internal fun searchKey(value: String): String {
    val folded = java.text.Normalizer.normalize(value.lowercase().trim(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي').replace('ة', 'ه')
    return folded.replace(Regex("\\s+"), " ")
}
