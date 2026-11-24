package com.nhom4.subtrack.domain

import java.text.Normalizer

/** Tìm kiếm không phân biệt hoa thường và dấu tiếng Việt: "goi gia dinh" khớp "Gói gia đình". */
object TextSearch {
    private val marks = Regex("""\p{Mn}+""")

    fun fold(s: String): String =
        marks.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "").replace('đ', 'd')

    fun matches(text: String, query: String) = fold(text).contains(fold(query))
}
