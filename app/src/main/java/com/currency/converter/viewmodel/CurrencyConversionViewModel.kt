package com.currency.converter.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.currency.converter.R
import com.currency.converter.utils.Response
import com.currency.converter.db.ExchangeRate
import com.currency.converter.db.ExchangeRateDao
import com.currency.converter.model.ExchangeRateApiResponse
import com.currency.converter.model.ExchangeRateItem
import com.currency.converter.repository.CurrencyConversionRepository
import com.currency.converter.utils.AppUtils
import com.currency.converter.utils.NetworkHelper
import com.currency.converter.utils.RateLimiter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class CurrencyConversionViewModel @Inject constructor(
    private val currencyRepository: CurrencyConversionRepository,
    private val networkHelper: NetworkHelper,
    private val exchangeRateDao: ExchangeRateDao
) : ViewModel() {

    companion object {
        const val TAG = "CurrencyConversionViewModel"
        const val DEFAULT_BASE_CURRENCY = "USD"
        const val FETCH_DATA_TIMEOUT_IN = 30
    }

    private var usdCurrencyPosition: Int = 0
    var _exchangeRatesMapLiveData = MutableLiveData<Map<String, Double>?>()
    var exchangeRatesLiveData: LiveData<Map<String, Double>?> = _exchangeRatesMapLiveData
    var currencyList = MutableLiveData<ArrayList<String>>()

    private var defaultCurrency = MutableLiveData(DEFAULT_BASE_CURRENCY)
    var computedExchangeRates = MutableLiveData<ArrayList<ExchangeRateItem>>()

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _responseState = MutableLiveData<Response<Any>>()
    val responseState: LiveData<Response<Any>>
        get() = _responseState

    private val apiRateLimiter =
        RateLimiter(FETCH_DATA_TIMEOUT_IN, TimeUnit.MINUTES)

    fun fetchExchangeRatesFromNetwork() {
        viewModelScope.launch {
            try {
                if (networkHelper.isNetworkConnected()) {
                    val result = currencyRepository.getExchangeRates()
                    result.collect {
                        it.process()
                    }
                }
            } catch (e: Exception) {
                _responseState.postValue(Response.Error(AppUtils.getString(R.string.something_went_wrong)))
                _errorMessage.postValue(AppUtils.getString(R.string.something_went_wrong))
            }
        }
    }

    @JvmName("processGetExchangeRateResponse")
    private suspend fun Response<ExchangeRateApiResponse>.process(
        onLoading: (Boolean) -> Unit = {},
    ) = withContext(Dispatchers.Main) {
        when (this@process) {
            Response.Loading -> {
                onLoading.invoke(true)
                _responseState.value = Response.Loading
            }

            is Response.Success -> {
                _responseState.value = Response.Success(data)
                data.rates.let {
                    setCurrencyList(extractCurrencies(it))
                    setExchangeRates(it, data.timestamp, System.currentTimeMillis())
                }
            }

            is Response.Error -> {
                _responseState.value = Response.Error( AppUtils.getString(R.string.something_went_wrong))
                _errorMessage.value = AppUtils.getString(R.string.something_went_wrong)
            }
        }
    }

    fun fetchCurrencyExchangeData() {
        viewModelScope.launch {
            try {
                exchangeRateDao.getAllExchangeRates().collect { exchangeRates ->
                    if (exchangeRates.isEmpty()) {
                        fetchExchangeRatesFromNetwork()
                    } else {
                        val lastFetchTime = exchangeRates.first().lastFetchTime
                        if (apiRateLimiter.shouldFetch(lastFetched = lastFetchTime)) {
                            fetchExchangeRatesFromNetwork()
                        } else {
                            setCurrencyList(fetchCurrencyList(exchangeRates))
                            val dataMap = exchangeRates.associate {
                                it.currency to it.usdConvertibleAmount
                            }
                            _exchangeRatesMapLiveData.postValue(dataMap)
                        }
                    }
                }
            } catch (e: Exception) {
                fetchExchangeRatesFromNetwork()
            }
        }
    }

    private fun setCurrencyList(currencies: ArrayList<String>) {
        usdCurrencyPosition = getItemPositionForCurrency(currencies, DEFAULT_BASE_CURRENCY)
        currencyList.value = currencies
    }

    fun getItemPositionForCurrency(currencies: List<String>, targetCurrency: String): Int {
        for ((index, value) in currencies.withIndex()) {
            if (value == targetCurrency) {
                return index
            }
        }
        return 0
    }

    fun getPositionForUSD(): Int {
        return usdCurrencyPosition
    }

    private fun fetchCurrencyList(rates: List<ExchangeRate>): ArrayList<String> {
        val currencies = ArrayList<String>()
        for (exchangeRate in rates) {
            currencies.add(exchangeRate.currency)
        }
        currencies.sort()
        return currencies
    }

    private fun extractCurrencies(rates: HashMap<String, Double>?): ArrayList<String> {
        val currencies = ArrayList<String>()
        if (rates != null) {
            for ((key, _) in rates) {
                currencies.add(key)
            }
        }
        currencies.sort()
        return currencies
    }

    fun getCurrencyFromItemPosition(position: Int): String {
        val currencies = getCurrencyList()
        return if (currencies != null) {
            currencies[position]
        } else {
            ""
        }
    }

    private fun setExchangeRates(
        rates: HashMap<String, Double>?,
        timestamp: Long?,
        lastFetchTime: Long
    ) {
        _exchangeRatesMapLiveData.postValue(rates)
        val dataList = ArrayList<ExchangeRate>()
        if (rates != null) {
            for ((currency, amount) in rates) {
                dataList.add(
                    ExchangeRate(
                        currency = currency,
                        usdConvertibleAmount = amount,
                        timestamp = timestamp ?: -1,
                        lastFetchTime = lastFetchTime
                    )
                )
            }
        }
        insertExchangeRatesIntoDB(dataList, Dispatchers.IO)
    }

    fun insertExchangeRatesIntoDB(
        dataList: List<ExchangeRate>,
        coroutineDispatcher: CoroutineDispatcher
    ) {
        viewModelScope.launch(coroutineDispatcher) {
            exchangeRateDao.insertExchangeRateList(dataList)
        }
    }

    fun computeExchangeRateForCurrencies(
        inputValue: Double = 0.0, baseCurrency: String = DEFAULT_BASE_CURRENCY
    ) {
        val exchangeRates = getExchangeRates()
        exchangeRates?.let {
            val newExchangeRates = arrayListOf<ExchangeRateItem>()
            val baseExchangeRate = it[baseCurrency]
            if (baseExchangeRate == null) {
                _errorMessage.postValue(AppUtils.getString(R.string.base_currency_not_available))
                return
            }
            val baseCurrencyToUSD = 1 / baseExchangeRate
            val currencies = getCurrencyList() ?: ArrayList()
            for (currency in currencies) {
                val exchangeRate = it[currency]
                if (exchangeRate == null) {
                    _errorMessage.postValue(AppUtils.getString(R.string.exchange_rate_not_available,currency))
                    continue
                }
                val currencyToUSD = 1 / exchangeRate
                var convertedValue = (baseCurrencyToUSD / currencyToUSD * inputValue)
                if (currency == DEFAULT_BASE_CURRENCY) {
                    convertedValue = baseExchangeRate * inputValue
                }
                newExchangeRates.add(
                    ExchangeRateItem(
                        currency = currency, value = convertedValue
                    )
                )
            }
            computedExchangeRates.postValue(newExchangeRates)
        }
    }


    fun setBaseCurrency(currency: String) {
        defaultCurrency.postValue(currency)
    }

    fun getBaseCurrency(): String? {
        return defaultCurrency.value
    }

    private fun getExchangeRates(): Map<String, Double>? {
        return exchangeRatesLiveData.value
    }

    fun getCurrencyList(): ArrayList<String>? {
        return currencyList.value
    }

}