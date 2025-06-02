package com.currency.converter.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import com.currency.converter.BuildConfig
import com.currency.converter.api.CurrencyConversionApi
import com.currency.converter.db.ExchangeRateDao
import com.currency.converter.db.ExchangeRateDb
import com.currency.converter.repository.CurrencyConversionRepoImpl
import com.currency.converter.repository.CurrencyConversionRepository
import dagger.hilt.android.qualifiers.ApplicationContext

@Module
@InstallIn(SingletonComponent::class)
class ApplicationModule {

    @Provides
    @Singleton
    fun provideOkHttpClient() = if (BuildConfig.DEBUG) {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    } else OkHttpClient
        .Builder()
        .build()


    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
    ): Retrofit =
        Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .build()

    @Provides
    @Singleton
    fun provideCurrencyConversionService(retrofit: Retrofit): CurrencyConversionApi =
        retrofit.create(CurrencyConversionApi::class.java)

    @Provides
    @Singleton
    fun provideCurrencyRepository(currencyConversionApi: CurrencyConversionApi): CurrencyConversionRepository {
        return CurrencyConversionRepoImpl(currencyConversionApi)
    }

    @Provides
    fun provideExchangeRateDao(@ApplicationContext appContext: Context): ExchangeRateDao {
        val database = ExchangeRateDb.getInstance(appContext)
        return database.exchangeRateDao()
    }

}
