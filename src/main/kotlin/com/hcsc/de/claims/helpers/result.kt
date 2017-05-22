package com.hcsc.de.claims.helpers

import io.reactivex.Observable
import io.reactivex.Single

sealed class Result<failureType, successType>

data class Success<failureType, successType>(
        val content: successType
) : Result<failureType, successType>()

data class Failure<failureType, successType>(
        val content: failureType
) : Result<failureType, successType>()

typealias ObservableResult<failureType, successType> = Observable<Result<failureType, successType>>
typealias SingleResult<failureType, successType> = Single<Result<failureType, successType>>

fun <failureType, successType, newSuccessType> SingleResult<failureType, successType>.flatMapSuccess(
        onSuccess: (successType) -> SingleResult<failureType, newSuccessType>
): SingleResult<failureType, newSuccessType> {

    return this.flatMap { result ->

        when (result) {
            is Success -> onSuccess.invoke(result.content)
            is Failure -> Single.just(Failure<failureType, newSuccessType>(result.content))
        }
    }
}

fun <failureType, successType> SingleResult<failureType, successType>.mapSuccess(
        onSuccess: (successType) -> Result<failureType, successType>
): SingleResult<failureType, successType> {

    return this.map { result ->

        when (result) {
            is Success -> onSuccess.invoke(result.content)
            is Failure -> result
        }
    }
}

fun <failureType, successType> List<Result<failureType, successType>>.traverse() : Result<failureType, List<successType>>{
    val list : Result<failureType, List<successType>> = Success(content = emptyList())
    return this.fold(list) { accum: Result<failureType, List<successType>>, result : Result<failureType, successType> ->
        when(accum) {
            is Success -> {
                when(result) {
                    is Success -> Success<failureType, List<successType>>(content = accum.content.plus(result.content))
                    is Failure ->  Failure<failureType, List<successType>>(content = result.content)
                }
            }
            is Failure -> Failure<failureType, List<successType>>(content = accum.content)
        }
    }
}

fun <failureType, successType> List<SingleResult<failureType, successType>>.concat() = Single.concat(this)
//fun <failureType, successType> List<SingleResult<failureType, successType>>.concat(): Flowable<Result<failureType, successType>> = Single.concat(this)

val EMPTY_SUCCESS = Success<String, Unit>(Unit)

fun <failureType, successType, newSuccessType> Result<failureType, successType>.flatMap(
        transform: (successType) -> Result<failureType, newSuccessType>
): Result<failureType, newSuccessType> {

    return when (this) {
        is Success -> transform(this.content)
        is Failure -> Failure<failureType, newSuccessType>(content = this.content)
    }
}

fun <failureType, successType, newSuccessType> Result<failureType, successType>.map(
        transform: (successType) -> newSuccessType
): Result<failureType, newSuccessType> {

    return when (this) {
        is Success -> Success(transform(this.content))
        is Failure -> Failure<failureType, newSuccessType>(content = this.content)
    }
}