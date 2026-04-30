package com.affiliatemonitor.app.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface ApiService {
    @GET("api/health") suspend fun health(): HealthOut

    @GET("api/sources") suspend fun listSources(): List<SourceOut>
    @POST("api/sources") suspend fun createSource(@Body body: SourceCreate): SourceOut
    @PATCH("api/sources/{id}") suspend fun updateSource(@Path("id") id: Int, @Body body: SourceUpdate): SourceOut
    @DELETE("api/sources/{id}") suspend fun deleteSource(@Path("id") id: Int)
    @POST("api/sources/{id}/scan") suspend fun scanSource(@Path("id") id: Int): Map<String, Any?>

    @POST("api/scan") suspend fun scanAll(): ScanResult

    @GET("api/dashboard") suspend fun dashboard(): DashboardOut

    @GET("api/queue") suspend fun queue(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): List<PostOut>

    @GET("api/posted") suspend fun posted(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): List<PostOut>

    @GET("api/posts/{id}") suspend fun getPost(@Path("id") id: Int): PostOut
    @POST("api/posts/{id}/mark-posted") suspend fun markPosted(@Path("id") id: Int): PostOut
    @POST("api/posts/{id}/reject") suspend fun rejectPost(@Path("id") id: Int): PostOut

    @GET("api/logs") suspend fun logs(
        @Query("category") category: String? = null,
        @Query("level") level: String? = null,
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0,
    ): List<LogOut>

    @GET("api/settings") suspend fun getSettings(): SettingsOut
    @PATCH("api/settings") suspend fun patchSettings(@Body body: SettingsUpdate): SettingsOut
}

object ApiClient {

    @Volatile private var retrofit: Retrofit? = null
    @Volatile private var currentBase: String = ""

    fun service(baseUrl: String): ApiService {
        val normalized = normalize(baseUrl)
        val r = retrofit
        if (r != null && currentBase == normalized) return r.create(ApiService::class.java)

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
        val built = Retrofit.Builder()
            .baseUrl(normalized)
            .client(http)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit = built
        currentBase = normalized
        return built.create(ApiService::class.java)
    }

    private fun normalize(raw: String): String {
        val trimmed = raw.trim().ifBlank { "http://10.0.2.2:8000/" }
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }
}
