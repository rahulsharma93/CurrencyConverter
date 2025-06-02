package com.currency.converter.api

import com.currency.converter.model.ExchangeRateApiResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface CurrencyConversionApi {
    @GET("/api/latest.json")
    suspend fun getExchangeRates(
        @Query("app_id") appId: String, @Query("base") base: String? = "USD"
    ): Response<ExchangeRateApiResponse>
}