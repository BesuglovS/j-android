package ru.nayanovaacademy.journal.data.api

import ru.nayanovaacademy.journal.util.CookieStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val cookieStore: CookieStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val cookie = cookieStore.getCookie()
        if (cookie.isNotEmpty()) {
            val request = original.newBuilder()
                .header("Cookie", "auth_session=$cookie")
                .build()
            return chain.proceed(request)
        }
        return chain.proceed(original)
    }
}
