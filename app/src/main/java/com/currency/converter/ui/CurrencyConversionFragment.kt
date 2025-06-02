package com.currency.converter.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.currency.converter.databinding.FragmentCurrencyConversionBinding
import com.currency.converter.model.ExchangeRateItem
import com.currency.converter.viewmodel.CurrencyConversionViewModel
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import android.R.layout.simple_spinner_dropdown_item
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CurrencyConversionFragment : Fragment() {

    private lateinit var binding: FragmentCurrencyConversionBinding
    private lateinit var exchangeRateInfoAdapter: ExchangeRateListAdapter
    private lateinit var viewModel: CurrencyConversionViewModel

    companion object {
        private const val TAG = "CurrencyConversionFragment"
        fun newInstance(): CurrencyConversionFragment {
            return CurrencyConversionFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCurrencyConversionBinding.inflate(inflater)
        viewModel = ViewModelProvider(this)[CurrencyConversionViewModel::class.java]
        setExchangeRateAdapter()
        addTextWatcher()
        setSpinnerOnItemListener()
        observeLiveData()
        return binding.root
    }

    private fun setExchangeRateAdapter() {
        exchangeRateInfoAdapter = ExchangeRateListAdapter { item -> handleOnItemClick(item) }
        binding.exchangeRateList.layoutManager = LinearLayoutManager(context)
        binding.exchangeRateList.adapter = exchangeRateInfoAdapter
    }

    private fun addTextWatcher() {
        binding.amountEditText.doAfterTextChanged { inputString ->
            println("Input: $inputString")
            var inputValue = 0.0
            if (!inputString.isNullOrEmpty()) {
                inputValue = inputString.toString().toDouble()
            }
            viewModel.computeExchangeRateForCurrencies(
                inputValue = inputValue,
                baseCurrency = viewModel.getBaseCurrency()
                    ?: CurrencyConversionViewModel.DEFAULT_BASE_CURRENCY
            )
        }
    }

    private fun setSpinnerOnItemListener() {
        binding.currencySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    changeSelectedCurrency(position)
                }
            }
    }

    private fun observeLiveData() {
        viewModel.currencyList.observe(
            viewLifecycleOwner
        ) { currencyList ->
            val adapter: ArrayAdapter<String> = ArrayAdapter<String>(
                requireContext(), simple_spinner_dropdown_item, currencyList
            )
            adapter.setDropDownViewResource(simple_spinner_dropdown_item)
            binding.currencySpinner.adapter = adapter
            binding.currencySpinner.setSelection(viewModel.getPositionForUSD())
        }

        viewModel.computedExchangeRates.observe(viewLifecycleOwner) {
            binding.progressBar.visibility = View.GONE
            exchangeRateInfoAdapter.submitList(it)
        }

        viewModel.exchangeRatesLiveData.observe(viewLifecycleOwner) {
            viewModel.computeExchangeRateForCurrencies(
                inputValue = binding.amountEditText.text.toString().toDouble(),
                baseCurrency = viewModel.getBaseCurrency()
                    ?: CurrencyConversionViewModel.DEFAULT_BASE_CURRENCY
            )
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) {
            Toast.makeText(activity, it, Toast.LENGTH_SHORT).show()
        }

        viewModel.fetchCurrencyExchangeData()
    }

    private fun handleOnItemClick(exchangeRateItem: ExchangeRateItem) {
        val position = viewModel.getItemPositionForCurrency(
            viewModel.getCurrencyList() ?: ArrayList(), exchangeRateItem.currency
        )
        if (position >= 0) {
            changeSelectedCurrency(position)
            binding.currencySpinner.setSelection(position)
        }
    }

    private fun changeSelectedCurrency(itemPosition: Int) {
        val currency = viewModel.getCurrencyFromItemPosition(itemPosition)
        if (viewModel.getBaseCurrency() == currency) {
            return
        }
        viewModel.setBaseCurrency(currency)
        var inputValue = 0.0
        if (binding.amountEditText.text.toString().isNotEmpty()) {
            inputValue = binding.amountEditText.text.toString().toDouble()
        }
        viewModel.computeExchangeRateForCurrencies(
            inputValue = inputValue, baseCurrency = currency
        )
    }


}