package com.currency.converter.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * response helper class for converting response into flows
 */
sealed class Response<out T> {
    data class Success<out T>(val data: T) : Response<T>()
    data class Error(val message: String?) : Response<Nothing>()
    data object Loading : Response<Nothing>()
}

fun <T> flowResponse(
    onLoading: () -> Unit = {},
    onSuccess: (T) -> Unit = {},
    onError: (String) -> Unit = {},
    api: suspend () -> retrofit2.Response<T>,
): Flow<Response<T>> {
    return flow {
        onLoading.invoke()
        emit(Response.Loading)
        try {
            val response = api.invoke()
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                onSuccess.invoke(data)
                emit(Response.Success(data))
            } else {
                val errorMsg = response.message()
                onError.invoke(errorMsg)
                emit(Response.Error(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = e.message.orEmpty()
            onError.invoke(errorMsg)
            emit(Response.Error(errorMsg))
        }
    }.flowOn(Dispatchers.IO)
}