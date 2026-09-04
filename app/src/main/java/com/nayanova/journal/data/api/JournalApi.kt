package com.nayanova.journal.data.api

import com.nayanova.journal.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface JournalApi {

    @GET("api/v1/me")
    suspend fun me(): Response<Map<String, Any>>

    @GET("api/v1/classes")
    suspend fun classes(): Response<Map<String, List<SchoolClass>>>

    @GET("api/v1/classes/{id}/subjects")
    suspend fun classSubjects(@Path("id") classId: Int): Response<Map<String, List<Subject>>>

    @GET("api/v1/lessons")
    suspend fun lessons(
        @Query("class_id") classId: Int = 0,
        @Query("subject_id") subjectId: Int = 0,
        @Query("date") date: String? = null
    ): Response<Map<String, List<Lesson>>>

    @GET("api/v1/lessons/{id}")
    suspend fun lessonDetail(@Path("id") lessonId: Int): Response<LessonDetail>

    @POST("api/v1/lessons")
    suspend fun lessonCreate(@Body body: HashMap<String, Any>): Response<Map<String, Int>>

    @POST("api/v1/lessons/{id}/marks")
    suspend fun marksSave(
        @Path("id") lessonId: Int,
        @Body body: HashMap<String, Any>
    ): Response<Map<String, Boolean>>

    @POST("api/v1/lessons/{id}/remarks")
    suspend fun remarksSave(
        @Path("id") lessonId: Int,
        @Body body: HashMap<String, Any>
    ): Response<Map<String, Boolean>>

    @POST("api/v1/lessons/{id}/attendance")
    suspend fun attendanceSave(
        @Path("id") lessonId: Int,
        @Body body: HashMap<String, Any>
    ): Response<Map<String, Boolean>>

    @POST("api/v1/lessons/{id}/homework")
    suspend fun homeworkSave(
        @Path("id") lessonId: Int,
        @Body body: HashMap<String, Any>
    ): Response<Map<String, Boolean>>

    @POST("api/v1/homework/{id}/delete")
    suspend fun homeworkDelete(
        @Path("id") homeworkId: Int
    ): Response<Map<String, Boolean>>

    @GET("api/v1/students")
    suspend fun students(@Query("class_id") classId: Int = 0): Response<Map<String, List<Student>>>

    @GET("api/v1/quarters")
    suspend fun quarters(): Response<Map<String, List<Quarter>>>

    @GET("api/v1/grades")
    suspend fun grades(
        @Query("class_id") classId: Int = 0,
        @Query("quarter_id") quarterId: Int = 0,
        @Query("subject_id") subjectId: Int = 0
    ): Response<Map<String, Any>>

    @GET("api/v1/class-journal")
    suspend fun classJournal(
        @Query("class_id") classId: Int,
        @Query("subject_id") subjectId: Int
    ): Response<ClassJournalData>
}
