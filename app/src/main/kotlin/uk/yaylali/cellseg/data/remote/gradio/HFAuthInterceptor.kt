package uk.yaylali.cellseg.data.remote.gradio

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <token>` when an HF token is available.
 * The token is fetched lazily via [tokenProvider] so it reflects the latest
 * value from encrypted storage without recreating the OkHttpClient.
 *
 * SECURITY: never log the token value. Only log "present" / "absent".
 */
class HFAuthInterceptor(
    private val tokenProvider: () -> String?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenProvider()
        return if (!token.isNullOrBlank()) {
            chain.proceed(
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            )
        } else {
            chain.proceed(original)
        }
    }
}
