package uk.yaylali.cellseg.data.remote.gradio

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

/**
 * Retrofit service for the non-SSE Gradio API calls.
 * Base URL is set dynamically per Space slug.
 */
interface GradioApiService {

    @GET
    suspend fun getSpaceInfo(@Url url: String): SpaceInfoResponse

    @Multipart
    @POST
    suspend fun uploadFile(
        @Url url: String,
        @Part file: MultipartBody.Part,
    ): List<String>   // raw array of server-side paths

    @POST
    suspend fun queueJoin(
        @Url url: String,
        @Body request: RequestBody,
    ): QueueJoinResponse
}
