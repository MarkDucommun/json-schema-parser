package com.hcsc.de.claims

import org.assertj.core.api.KotlinAssertions.assertThat
import org.junit.Test

class ResultKtTest {

    @Test
    fun `traverse list of successes returns result of that list of successes`() {
        val list: List<Result<String, Int>> = listOf(
                Success(content = 1),
                Success(content = 2),
                Success(content = 3))

        assertThat(list.traverse()).isEqualTo(
                Success<String, List<Int>>(content = listOf(1, 2, 3))
        )
    }

    @Test
    fun `traversing list of results that contains a failure returns result of that failure`() {
        val list: List<Result<String, Int>> = listOf(
                Success(content = 1),
                Failure(content = "a failure!"),
                Success(content = 3))

        assertThat(list.traverse()).isEqualTo(
                Failure<String, List<Int>>(content = "a failure!")
        )
    }

    @Test
    fun `traversing a list of results with multiple failures returns a result with a list of those failures`() {
        val list: List<Result<String, Int>> = listOf(
                Failure(content = "a failure!"),
                Failure(content = "another failure!"),
                Success(content = 3))


        assertThat(list.traverse()).isEqualTo(
                Failure<String, List<Int>>(content = "a failure!")
        )
    }
}