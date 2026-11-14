package com.windrr.hyundae.ui.result

/** 받침 따라 조사 고르기: "책상과 물건 S를", "캐리어와 유모차를" */
internal object Josa {

    /** 마지막 글자에 받침이 있는지. 영문·숫자는 읽는 소리 기준 (L→엘, 1→일 …) */
    fun hasBatchim(word: String): Boolean {
        val last = word.trim().lastOrNull { !it.isWhitespace() && it !in ")]}.,!?\"'" } ?: return false
        return when {
            last in '가'..'힣' -> (last - '가') % 28 != 0
            last.uppercaseChar() in "LMNR" -> true
            last in "013678" -> true
            else -> false
        }
    }

    fun 과와(word: String) = if (hasBatchim(word)) "과" else "와"

    fun 을를(word: String) = if (hasBatchim(word)) "을" else "를"

    /** "책상과 물건 S" / "캐리어, 유모차와 박스" / "캐리어 외 3개" */
    fun joinNames(names: List<String>): String = when (names.size) {
        0 -> "짐"
        1 -> names[0]
        2 -> names[0] + 과와(names[0]) + " " + names[1]
        else -> "${names[0]} 외 ${names.size - 1}개"
    }
}
