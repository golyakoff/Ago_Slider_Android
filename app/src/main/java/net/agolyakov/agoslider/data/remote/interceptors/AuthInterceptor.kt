package net.agolyakov.agoslider.data.remote.interceptors

import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.http.Header
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val token: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "token $token")
            .header("User-Agent", "AgoSlider-Android-App") // GitHub requires it
            .build()

        return chain.proceed(authenticatedRequest)
    }
}