package uk.yaylali.cellseg.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import uk.yaylali.cellseg.BuildConfig
import uk.yaylali.cellseg.data.local.TokenStore
import uk.yaylali.cellseg.data.remote.gradio.GradioApiService
import uk.yaylali.cellseg.data.remote.gradio.GradioSseParser
import uk.yaylali.cellseg.data.remote.gradio.HFAuthInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    @Named("hf_client")
    fun provideOkHttpClient(tokenStore: TokenStore): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(HFAuthInterceptor { tokenStore.getToken() })

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor { msg ->
                Timber.tag("OkHttp-HF").d(msg)
            }.apply {
                level = HttpLoggingInterceptor.Level.HEADERS
                redactHeader("Authorization")
            }
            builder.addNetworkInterceptor(logging)
        }
        return builder.build()
    }

    @Provides
    @Singleton
    @Named("basic_client")
    fun provideBasicOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor { msg ->
                Timber.tag("OkHttp").d(msg)
            }.apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addNetworkInterceptor(logging)
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(@Named("hf_client") okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://huggingface.co/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideGradioApiService(retrofit: Retrofit): GradioApiService =
        retrofit.create(GradioApiService::class.java)

    @Provides
    @Singleton
    fun provideGradioSseParser(moshi: Moshi): GradioSseParser = GradioSseParser(moshi)
}
