package com.lumora.player

private val MARKS = Regex("\\p{Mn}+")
private val SPACES = Regex("\\s+")

/** Normalize common Arabic and Latin spelling variants for local catalog search. */
internal fun searchKey(value: String): String {
    val folded = java.text.Normalizer.normalize(value.lowercase().trim(), java.text.Normalizer.Form.NFD)
        .replace(MARKS, "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي').replace('ة', 'ه')
    return folded.replace(SPACES, " ")
}
