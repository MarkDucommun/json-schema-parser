package com.hcsc.de.claims

import org.junit.Assert.fail

infix fun <failureType, successType> Result<failureType, successType>.succeedsAnd(onSuccess: (successType) -> Unit) {
    when (this) {
        is Success -> onSuccess(this.content)
        is Failure -> fail("Result should have been a Success")
    }
}

infix fun <failureType, successType> Result<failureType, successType>.failsAnd(onFailure: (failureType) -> Unit) {
    when (this) {
        is Success -> fail("Result should have been a Failure")
        is Failure -> onFailure(this.content)
    }
}

infix fun <failureType, successType> SingleResult<failureType, successType>.succeedsAnd(onSuccess: (successType) -> Unit) {
    blockingGet().let { result ->
        when (result) {
            is Success -> onSuccess(result.content)
            is Failure -> fail("Result should have been a Success")
        }
    }
}

infix fun <failureType, successType> SingleResult<failureType, successType>.failsAnd(onFailure: (failureType) -> Unit) {
    blockingGet().let { result ->
        when (result) {
            is Success -> fail("Result should have been a Failure")
            is Failure -> onFailure(result.content)
        }
    }
}