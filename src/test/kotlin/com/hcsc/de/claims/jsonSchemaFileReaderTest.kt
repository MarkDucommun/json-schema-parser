package com.hcsc.de.claims

import com.fasterxml.jackson.databind.JsonNode
import org.assertj.core.api.KotlinAssertions.assertThat
import org.junit.Test

class jsonSchemaFileReaderTest {

    val fileReader = JacksonFileReader()

    @Test
    fun `file reader reads from a file into a JSON node`() {

        val node = fileReader.read("src/main/resources/cts-schema.json")

        assertThat(node is JsonNode).isTrue()
    }
}