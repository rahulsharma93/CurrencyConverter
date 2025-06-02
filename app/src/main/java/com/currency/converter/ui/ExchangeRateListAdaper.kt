package com.currency.converter.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.currency.converter.R
import com.currency.converter.databinding.CurrencyListItemBinding
import com.currency.converter.model.ExchangeRateItem
import java.util.Locale

class ExchangeRateListAdapter(private val onClick: (ExchangeRateItem) -> Unit) :
    ListAdapter<ExchangeRateItem, ExchangeRateListAdapter.ViewHolder>(ItemDiffCallback) {

    class ViewHolder(
        private val binding: CurrencyListItemBinding,
        val onClick: (ExchangeRateItem) -> Unit
    ) :
        RecyclerView.ViewHolder(binding.root) {
        private var currentItem: ExchangeRateItem? = null

        init {
            itemView.setOnClickListener {
                currentItem?.let {
                    onClick(it)
                }
            }
        }

        fun bind(item: ExchangeRateItem) {
            currentItem = item
            if (item.value == 0.0) {
                binding.value.text = "0.0"
            } else {
                binding.value.text = String.format(Locale.US, "%.5f", item.value)
            }
            binding.currency.text = item.currency
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = DataBindingUtil.inflate<CurrencyListItemBinding>(
            LayoutInflater.from(parent.context), R.layout.currency_list_item, parent, false
        )
        return ViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)

    }
}

object ItemDiffCallback : DiffUtil.ItemCallback<ExchangeRateItem>() {
    override fun areItemsTheSame(oldItem: ExchangeRateItem, newItem: ExchangeRateItem): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: ExchangeRateItem, newItem: ExchangeRateItem): Boolean {
        return oldItem.currency == newItem.currency
    }
}