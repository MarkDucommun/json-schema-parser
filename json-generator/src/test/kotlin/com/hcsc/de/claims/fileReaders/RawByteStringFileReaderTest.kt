package com.hcsc.de.claims.fileReaders

import com.hcsc.de.claims.failsAnd
import com.hcsc.de.claims.succeedsAnd
import org.assertj.core.api.KotlinAssertions.assertThat
import org.junit.Test

class RawByteStringFileReaderTest {

    val stringFileReader = RawByteStringFileReader()

    @Test
    fun `it reads the test file into a string`() {

        stringFileReader.read("src/test/resources/test.txt") succeedsAnd { string ->

            assertThat(string).isEqualTo("Hello, world!")
        }
    }

    @Test
    fun `if it cannot find the file, it returns a Failure`() {

        stringFileReader.read("file-that-does-not-exist") failsAnd { message ->

            assertThat(message).isEqualTo("File not found")
        }
    }
}