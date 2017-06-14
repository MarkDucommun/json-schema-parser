package com.hcsc.de.claims.results

import com.hcsc.de.claims.results.observableResults.SingleResult

infix fun <failureType, successType> Result<failureType, successType>.succeedsAnd(onSuccess: (successType) -> Unit) {
    when (this) {
        is Success -> onSuccess(this.content)
        is Failure -> org.junit.Assert.fail("Result should have been a Success: $content")
    }
}

infix fun <failureType, successType> Result<failureType, successType>.succeedsAndShouldReturn(expectedObject: successType) {
    when (this) {
        is Success -> org.assertj.core.api.KotlinAssertions.assertThat(content).isEqualTo(expectedObject)
        is Failure -> org.junit.Assert.fail("Result should have been a Success: $content")
    }
}

infix fun <failureType, successType> Result<failureType, successType>.failsAnd(onFailure: (failureType) -> Unit) {
    when (this) {
        is Success -> org.junit.Assert.fail("Result should have been a Failure")
        is Failure -> onFailure(this.content)
    }
}

infix fun <successType> Result<String, successType>.failsWithMessage(expectedMessage: String) {
    when (this) {
        is Success -> org.junit.Assert.fail("Result should have been a Failure")
        is Failure -> org.assertj.core.api.KotlinAssertions.assertThat(content).isEqualTo(expectedMessage)
    }
}

val <failureType, successType> Result<failureType, successType>.get: successType get() = when (this) {
    is Success -> content
    is Failure -> throw RuntimeException("Failure: ${content.toString()}")
}