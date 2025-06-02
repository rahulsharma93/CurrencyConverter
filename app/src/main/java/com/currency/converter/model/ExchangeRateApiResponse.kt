package com.currency.converter.model

import com.google.gson.annotations.SerializedName

data class ExchangeRateApiResponse(
    @SerializedName("disclaimer")
    val disclaimer: String? = null,
    @SerializedName("license")
    val license: String? = null,
    @SerializedName("timestamp")
    val timestamp: Long?,
    @SerializedName("base")
    val base: String? = "USD",
    @SerializedName("rates")
    val rates: HashMap<String, Double>?
)