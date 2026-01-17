package com.example.utilitiestracker.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateUtils {
    private val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun format(localDate: LocalDate): String = localDate.format(fmt)

    fun epochDayToLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun localDateToEpochDay(localDate: LocalDate): Long = localDate.toEpochDay()

    fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())
}
