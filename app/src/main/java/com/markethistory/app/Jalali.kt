package com.markethistory.app

import java.time.LocalDate

val JALALI_MONTHS = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)
val GREG_MONTHS = listOf(
    "ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن",
    "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
)
// index = java DayOfWeek.value (1 = Monday)
val WEEKDAYS = listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")

fun String.fa(): String {
    val sb = StringBuilder()
    for (ch in this) sb.append(if (ch in '0'..'9') '۰' + (ch - '0') else ch)
    return sb.toString()
}

fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
    val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
    var jy = -1595 + (33 * (days / 12053))
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
    val jd = 1 + (if (days < 186) days % 31 else (days - 186) % 30)
    return Triple(jy, jm, jd)
}

fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
    val jy2 = jy + 1595
    var days = -355668 + (365 * jy2) + ((jy2 / 33) * 8) + (((jy2 % 33) + 3) / 4) + jd +
        (if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30) + 186)
    var gy = 400 * (days / 146097)
    days %= 146097
    if (days > 36524) {
        days--
        gy += 100 * (days / 36524)
        days %= 36524
        if (days >= 365) days++
    }
    gy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        gy += (days - 1) / 365
        days = (days - 1) % 365
    }
    var gd = days + 1
    val feb = if ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0) 29 else 28
    val sal = intArrayOf(0, 31, feb, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var gm = 0
    while (gm < 13 && gd > sal[gm]) {
        gd -= sal[gm]
        gm++
    }
    return Triple(gy, gm, gd)
}

fun toJalali(d: LocalDate): Triple<Int, Int, Int> = gregorianToJalali(d.year, d.monthValue, d.dayOfMonth)

fun fromJalali(y: Int, m: Int, d: Int): LocalDate {
    val g = jalaliToGregorian(y, m, d)
    return LocalDate.of(g.first, g.second, g.third)
}

fun jalaliMonthLength(y: Int, m: Int): Int {
    if (m <= 6) return 31
    if (m <= 11) return 30
    // Esfand: 30 days in leap years (check by round trip)
    val g = jalaliToGregorian(y, 12, 30)
    val back = gregorianToJalali(g.first, g.second, g.third)
    return if (back.first == y && back.second == 12 && back.third == 30) 30 else 29
}

fun jalaliText(d: LocalDate): String {
    val (y, m, day) = toJalali(d)
    return "$day ${JALALI_MONTHS[m - 1]} $y".fa()
}

fun gregText(d: LocalDate): String =
    "${d.dayOfMonth} ${GREG_MONTHS[d.monthValue - 1]} ${d.year}".fa()

fun weekdayText(d: LocalDate): String = WEEKDAYS[d.dayOfWeek.value - 1]

fun parseDate(s: String): LocalDate = try {
    LocalDate.parse(s)
} catch (e: Exception) {
    LocalDate.now()
}
