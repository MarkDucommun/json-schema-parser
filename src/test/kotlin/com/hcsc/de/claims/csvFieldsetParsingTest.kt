package com.hcsc.de.claims

import org.assertj.core.api.KotlinAssertions.assertThat
import org.junit.Test

class csvFieldsetParsingTest {

    @Test
    fun `reads the CSV file`() {

        val shittyHardcodedFileLocation = "src/main/resources/cinq-fields.csv"

        val rows = shittyHardcodedFileLocation.readAndConvertCsvToFieldSetList()

        assertThat(rows is List<List<String>>).isTrue()

        assertThat(rows.first()).containsExactly("InsuranceClaim","ClaimHeader","AdjudicationClaimStatusCode")
    }
}