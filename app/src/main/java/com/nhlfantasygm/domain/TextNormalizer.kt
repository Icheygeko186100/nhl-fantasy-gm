package com.nhlfantasygm.domain

import java.text.Normalizer
import java.time.LocalDate
import java.time.Period

object TextNormalizer {

    fun normalizeForMatch(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
        return decomposed
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase()
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    fun computeAge(birthDateIso: String?, referenceDate: LocalDate = LocalDate.now()): Int {
        if (birthDateIso.isNullOrBlank()) return 25
        return runCatching {
            val dob = LocalDate.parse(birthDateIso.trim().take(10))
            Period.between(dob, referenceDate).years.coerceIn(16, 48)
        }.getOrDefault(25)
    }
}
