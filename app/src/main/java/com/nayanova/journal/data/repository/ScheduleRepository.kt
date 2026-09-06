package com.nayanova.journal.data.repository

import com.nayanova.journal.data.api.ScheduleApi
import com.nayanova.journal.data.model.ScheduleItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val api: ScheduleApi
) {
    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val message: String, val code: Int = 0) : Result<Nothing>()
    }

    /** Расписание учителя на дату (формат ГГГГ-ММ-ДД). */
    suspend fun teacherSchedule(date: String): Result<List<ScheduleItem>> {
        return try {
            val teacher = resolveTeacher() ?: TEACHER_NAME
            val response = api.schedule(teacher = teacher, date = date)
            if (response.isSuccessful) {
                Result.Success(response.body()?.schedule ?: emptyList())
            } else {
                Result.Error("Ошибка загрузки расписания", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }

    /**
     * Точное имя учителя из списка сайта расписания (сравнение без пробелов,
     * без учёта регистра и Ё/Е — «Безуглов С. В.» совпадёт с «Безуглов С.В.»).
     * При любой ошибке используется константа.
     */
    private suspend fun resolveTeacher(): String? {
        return try {
            val response = api.teachers()
            val list = if (response.isSuccessful) {
                response.body()?.teachers ?: emptyList()
            } else {
                emptyList()
            }
            list.firstOrNull { normalize(it) == TEACHER_KEY }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** Имя учителя в расписании (именительный падеж, как в XLSX). */
        const val TEACHER_NAME = "Безуглов С.В."
        private const val TEACHER_KEY = "БЕЗУГЛОВС.В."

        private fun normalize(s: String): String =
            s.uppercase().replace('Ё', 'Е').filter { !it.isWhitespace() }
    }
}
