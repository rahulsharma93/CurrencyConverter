package com.currency.converter.repository

import com.currency.converter.utils.Response
import com.currency.converter.model.ExchangeRateApiResponse
import kotlinx.coroutines.flow.Flow

interface CurrencyConversionRepository {
    suspend fun getExchangeRates(): Flow<Response<ExchangeRateApiResponse>>
}