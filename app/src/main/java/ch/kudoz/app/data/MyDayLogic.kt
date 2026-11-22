package ch.kudoz.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * «Mein Tag» – gleiche Regeln wie src/lib/my-day.ts auf kudoz.ch:
 * Schulwoche Mo–Fr, Zeitzone Europe/Zurich, Fächer aus dem gespeicherten Datum berechnet.
 */
enum class Bucket(val label: String) {
    UNPLANNED("Unverplant"), TODAY("Heute"), TOMORROW("Morgen"), WEEK("Diese Woche"), NEXT("Nächste Woche")
}

object MyDayLogic {
    const val TODAY_SOFT_LIMIT = 5

    fun today(): LocalDate = LocalDate.now(ZoneId.of("Europe/Zurich"))

    private fun isSchoolDay(d: LocalDate) = d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY
    fun mondayOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())
    fun nextSchoolDay(d: LocalDate): LocalDate {
        var x = d.plusDays(1)
        while (!isSchoolDay(x)) x = x.plusDays(1)
        return x
    }
    fun hasTomorrow(today: LocalDate) = mondayOf(nextSchoolDay(today)) == mondayOf(today)
    fun visibleBuckets(today: LocalDate) = Bucket.entries.filter { it != Bucket.TOMORROW || hasTomorrow(today) }

    fun bucketOf(plan: Plan?, today: LocalDate): Bucket {
        if (plan == null) return Bucket.UNPLANNED
        val date = runCatching { LocalDate.parse(plan.plannedDate.take(10)) }.getOrNull() ?: return Bucket.UNPLANNED
        val mon = mondayOf(today)
        if (plan.scope == "week") return if (!date.isAfter(mon)) Bucket.WEEK else Bucket.NEXT
        if (!date.isAfter(today)) return Bucket.TODAY
        if (date == nextSchoolDay(today) && hasTomorrow(today)) return Bucket.TOMORROW
        if (mondayOf(date) == mon) return Bucket.WEEK
        return Bucket.NEXT
    }

    /** (scope, planned_date) für ein Fach; null = entplanen. */
    fun targetFor(bucket: Bucket, today: LocalDate): Pair<String, String>? = when (bucket) {
        Bucket.UNPLANNED -> null
        Bucket.TODAY -> "day" to today.toString()
        Bucket.TOMORROW -> "day" to nextSchoolDay(today).toString()
        Bucket.WEEK -> "week" to mondayOf(today).toString()
        Bucket.NEXT -> "week" to mondayOf(today).plusDays(7).toString()
    }

    fun postponedDays(plan: Plan?, today: LocalDate): Int {
        if (plan == null || plan.scope != "day") return 0
        val d = runCatching { LocalDate.parse(plan.plannedDate.take(10)) }.getOrNull() ?: return 0
        if (!d.isBefore(today)) return 0
        var n = 0
        var x = d.plusDays(1)
        while (!x.isAfter(today)) { if (isSchoolDay(x)) n++; x = x.plusDays(1) }
        return n
    }

    fun postponedLabel(n: Int) = when {
        n <= 0 -> null
        n == 1 -> "seit gestern"
        else -> "$n× verschoben"
    }
}
