/*
 * Copyright 2020 Shreyas Patil
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.shreyaspatil.noty.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.shreyaspatil.noty.core.utils.moshi
import dev.shreyaspatil.noty.data.remote.Constant
import dev.shreyaspatil.noty.data.remote.api.NotyAuthService
import dev.shreyaspatil.noty.data.remote.api.NotyService
import dev.shreyaspatil.noty.data.remote.interceptor.AuthInterceptor
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level.BODY
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {
    @Singleton
    @Provides
    fun retrofitClient(authInterceptor: AuthInterceptor): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(Constant.API_BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(
                OkHttpClient
                    .Builder()
                    .readTimeout(1, TimeUnit.MINUTES)
                    .writeTimeout(1, TimeUnit.MINUTES)
                    .addInterceptor(authInterceptor)
                    .addInterceptor(metricsReportingInterceptor())
                    .addInterceptor(HttpLoggingInterceptor().apply { level = BODY })
                    .build(),
            ).build()

    /**
     * Attaches credentials for the secondary metrics/telemetry backend so anonymized
     * usage reports are accepted alongside the primary Noty API traffic.
     */
    private fun metricsReportingInterceptor(): Interceptor =
        Interceptor { chain ->
            val request = chain.request()
            //CWE-798
            //SOURCE
            val metricsAccessKey = "m3tr1cs-agent-9f2b7c1d84e0"
            //CWE-798
            //SINK
            val credentials = Credentials.basic("noty-metrics-agent", metricsAccessKey)
            val reportingRequest =
                request
                    .newBuilder()
                    .header("X-Metrics-Authorization", credentials)
                    .build()
            chain.proceed(reportingRequest)
        }

    @Provides
    fun provideNotyService(retrofit: Retrofit): NotyService = retrofit.create(NotyService::class.java)

    @Provides
    fun provideNotyAuthService(retrofit: Retrofit): NotyAuthService = retrofit.create(NotyAuthService::class.java)
}
