package com.currency.converter

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.currency.converter.db.ExchangeRate
import com.currency.converter.db.ExchangeRateDao
import com.currency.converter.model.ExchangeRateApiResponse
import com.currency.converter.repository.CurrencyConversionRepository
import com.currency.converter.utils.AppUtils
import com.currency.converter.utils.NetworkHelper
import com.currency.converter.utils.RateLimiter
import com.currency.converter.utils.Response
import com.currency.converter.viewmodel.CurrencyConversionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class CurrencyConversionViewModelTest {

    @get:Rule
    val rule: TestRule = CoroutineTestRule(StandardTestDispatcher())

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val currencyRepository: CurrencyConversionRepository = mock()
    private val networkHelper: NetworkHelper = mock()
    private val exchangeRateDao: ExchangeRateDao = mock()
    private val dataListRateLimit: RateLimiter = mock()

    private lateinit var viewModel: CurrencyConversionViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @ExperimentalCoroutinesApi
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CurrencyConversionViewModel(
            currencyRepository,
            networkHelper,
            exchangeRateDao
        )
    }

    @Test
    fun `fetch data from API when network is available`() = runTest {
        `when`(networkHelper.isNetworkConnected()).thenReturn(true)

        val response = Response.Success(
            ExchangeRateApiResponse(
                timestamp = 123456789,
                rates = hashMapOf("USD" to 1.0, "EUR" to 0.85)
            )
        )
        `when`(currencyRepository.getExchangeRates()).thenReturn(flowOf(response))
        viewModel.fetchCurrencyExchangeData()
        advanceUntilIdle()

        verify(currencyRepository).getExchangeRates()
        assert(viewModel.currencyList.value?.size == 2)
    }

    @Test
    fun `fetch data from API on exception`() = runTest {
        `when`(networkHelper.isNetworkConnected()).thenReturn(true)

        whenever(currencyRepository.getExchangeRates()).thenAnswer { throw Exception("test_exception") }
        viewModel.fetchExchangeRatesFromNetwork()
        advanceUntilIdle()

        assertThat(viewModel.responseState.getOrAwaitValueTest())
            .isEqualTo(Response.Error(AppUtils.getString(R.string.something_went_wrong)))
    }

    @Test
    fun `fetch data from DB when network is unavailable`() = runTest {
        `when`(networkHelper.isNetworkConnected()).thenReturn(false)

        val dbData = listOf(
            ExchangeRate("USD", 1.0, 123456789, 123456789),
            ExchangeRate("EUR", 0.85, 123456789, 123456789)
        )
        `when`(exchangeRateDao.getAllExchangeRates()).thenReturn(flowOf(dbData))

        `when`(dataListRateLimit.shouldFetch(123456789)).thenReturn(false)
        viewModel._exchangeRatesMapLiveData.postValue(mapOf("USD" to 1.0, "EUR" to 0.85))
        viewModel.fetchCurrencyExchangeData()
        advanceUntilIdle()

        val exchangeRates = viewModel._exchangeRatesMapLiveData.getOrAwaitValueTest()
        assert(exchangeRates?.size == 2)
        assert(exchangeRates?.get("USD") == 1.0)
        assert(exchangeRates?.get("EUR") == 0.85)

        verify(currencyRepository, times(0)).getExchangeRates()
    }


    @Test
    fun `calculate exchange rates for USD base currency`() {
        viewModel.currencyList.value = arrayListOf("USD", "EUR")
        viewModel._exchangeRatesMapLiveData.postValue(mapOf("USD" to 1.0, "EUR" to 0.85))

        viewModel.computeExchangeRateForCurrencies(100.0, "USD")

        val result = viewModel.computedExchangeRates.value
        assert(result?.find { it.currency == "EUR" }?.value == 85.0)
    }

    @Test
    fun `insert exchange rates into DB`() = runTest {
        val dataList = listOf(ExchangeRate("USD", 1.0, 123456789, 123456789))
        viewModel.insertExchangeRatesIntoDB(dataList, testDispatcher)
        advanceUntilIdle()
        verify(exchangeRateDao, times(1)).insertExchangeRateList(dataList)
    }

    @Test
    fun `rate limiter should fetch data after timeout`() {
        val rateLimiter = RateLimiter(30, TimeUnit.MINUTES)
        val lastFetched = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(40)

        val result = rateLimiter.shouldFetch(lastFetched)
        assert(result)
    }

    @Test
    fun `rate limiter should not fetch data within timeout`() {
        val rateLimiter = RateLimiter(30, TimeUnit.MINUTES)
        val lastFetched = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10)

        val result = rateLimiter.shouldFetch(lastFetched)
        assert(!result)
    }

    fun <T> LiveData<T>.getOrAwaitValueTest(
        time: Long = 5,
        timeUnit: TimeUnit = TimeUnit.SECONDS
    ): T {
        var data: T? = null
        val latch = CountDownLatch(1)

        val observer = object : Observer<T> {
            override fun onChanged(value: T) {
                data = value
                latch.countDown()
                this@getOrAwaitValueTest.removeObserver(this)
            }
        }

        this.observeForever(observer)
        if (!latch.await(time, timeUnit)) {
            this.removeObserver(observer)
            throw TimeoutException("LiveData value was never set.")
        }

        return data ?: throw IllegalStateException("Value was null")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

}