package ru.nayanovaacademy.journal.data.api

import ru.nayanovaacademy.journal.data.model.ScheduleItem
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** Ответ r-web: список учителей. */
data class TeachersResponse(
    val teachers: List<String> = emptyList()
)

/** Ответ r-web: расписание на дату (schedule — массив, imported_at — строка). */
data class ScheduleResponse(
    val schedule: List<ScheduleItem> = emptyList(),
    @SerializedName("imported_at") val importedAt: String? = null
)

/** JSON API сайта расписания r-web (api.php). */
interface ScheduleApi {

    @GET("api.php")
    suspend fun teachers(
        @Query("action") action: String = "teachers"
    ): Response<TeachersResponse>

    @GET("api.php")
    suspend fun schedule(
        @Query("action") action: String = "schedule",
        @Query("teacher") teacher: String,
        @Query("date") date: String
    ): Response<ScheduleResponse>
}
