package app.shosetsu.android.data.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object CortexHttpClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "CortexLibrary/1.0 (mailto:opensource@example.com)")
                    .header("Accept-Encoding", "gzip")
                    .build()
                chain.proceed(request)
            })
            .build()
    }
}
