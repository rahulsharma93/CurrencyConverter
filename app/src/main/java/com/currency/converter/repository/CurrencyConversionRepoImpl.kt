package com.currency.converter.repository

import com.currency.converter.BuildConfig
import com.currency.converter.utils.Response
import com.currency.converter.api.CurrencyConversionApi
import com.currency.converter.utils.flowResponse
import com.currency.converter.model.ExchangeRateApiResponse
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CurrencyConversionRepoImpl @Inject constructor(private val currencyConversionApi: CurrencyConversionApi) :
    CurrencyConversionRepository {
    override suspend fun getExchangeRates(): Flow<Response<ExchangeRateApiResponse>> {
        return flowResponse {
            currencyConversionApi.getExchangeRates(BuildConfig.APIKEY)
        }
    }
}