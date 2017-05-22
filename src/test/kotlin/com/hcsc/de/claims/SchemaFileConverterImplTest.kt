package com.hcsc.de.claims

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.javafaker.Faker
import com.hcsc.de.claims.jsonDeIdentifier.FileDeidentifier
import com.hcsc.de.claims.jsonSizing.JsonSizeAverager
import com.hcsc.de.claims.jsonSizing.JsonSizeNode
import com.hcsc.de.claims.jsonSizing.JsonSizer
import org.assertj.core.api.KotlinAssertions.assertThat
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.io.FileWriter
import java.util.regex.Pattern
import kotlin.Double.Companion.NaN

class SchemaFileConverterImplTest {

    val objectMapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())
    val fileReader = JacksonFileReader()
    val nodeConverter = NodeConverterImpl()
    val listConverter = ListConverterImpl(nodeConverter = nodeConverter)
    val schemaFileConverter = SchemaFileConverterImpl(
            reader = fileReader,
            listConverter = listConverter
    )

    @Test
    fun `converts the simplest objects - string`() {
        val testNode: JsonNode = mapOf(
                "type" to "string",
                "maxLength" to 30
        ).convert()

        val schema = nodeConverter.convert(testNode)

        assertThat(schema).isEqualTo(Text(maxLength = 30))
    }

    @Test
    fun `converts the simplest objects - date`() {
        val testNode: JsonNode = mapOf(
                "type" to "string",
                "format" to "date"
        ).convert()

        val schema = nodeConverter.convert(testNode)

        assertThat(schema).isEqualTo(Date)
    }

    @Test
    fun `converts the simplest objects - date-time`() {
        val testNode: JsonNode = mapOf(
                "type" to "string",
                "format" to "date-time"
        ).convert()

        val schema = nodeConverter.convert(testNode)

        assertThat(schema).isEqualTo(DateTime)
    }

    @Test
    fun `converts the simplest objects - number`() {
        val testNode: JsonNode = mapOf(
                "type" to "number"
        ).convert()

        val schema = nodeConverter.convert(testNode)

        assertThat(schema).isEqualTo(Number)
    }

    @Test
    fun `converts the simplest objects - integer`() {
        val testNode: JsonNode = mapOf(
                "type" to "integer"
        ).convert()

        val schema = nodeConverter.convert(testNode)

        assertThat(schema).isEqualTo(Integer)
    }

    @Test
    fun `converts a simple object`() {

        val testNode: JsonNode = mapOf(
                "type" to "object",
                "properties" to mapOf(
                        "fieldA" to mapOf("type" to "string", "maxLength" to 30),
                        "fieldB" to mapOf("type" to "string", "maxLength" to 15)
                )
        ).convert()

        val schema = nodeConverter.convert(testNode)

        assertThat(schema).isEqualTo(ComplexObject(
                properties = listOf(
                        SchemaObject(name = "fieldA", detail = Text(maxLength = 30)),
                        SchemaObject(name = "fieldB", detail = Text(maxLength = 15))
                )
        ))
    }

    @Test
    fun `it converts a simple array`() {
        val testNode: JsonNode = mapOf(
                "type" to "array",
                "items" to mapOf(
                        "type" to "string",
                        "maxLength" to 15
                )
        ).convert()

        val schema = nodeConverter.convert(testNode)

        assertThat(schema).isEqualTo(Array(
                itemType = Text(maxLength = 15),
                maxItems = null
        ))
    }

    @Test
    fun `it converts a simple array with maxItems`() {
        val testNode: JsonNode = mapOf(
                "type" to "array",
                "items" to mapOf(
                        "type" to "string",
                        "maxLength" to 15
                ),
                "maxItems" to 100
        ).convert()

        val schema = nodeConverter.convert(testNode)

        assertThat(schema).isEqualTo(Array(
                itemType = Text(maxLength = 15),
                maxItems = 100
        ))
    }

    @Test
    fun `it converts a more complex array`() {
        val testNode: JsonNode = mapOf(
                "type" to "array",
                "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                                "fieldA" to mapOf("type" to "string", "maxLength" to 30),
                                "fieldB" to mapOf("type" to "string", "maxLength" to 15)
                        )
                ),
                "maxItems" to 100
        ).convert()

        val schema = nodeConverter.convert(testNode)

        assertThat(schema).isEqualTo(Array(
                itemType = ComplexObject(
                        properties = listOf(
                                SchemaObject(name = "fieldA", detail = Text(maxLength = 30)),
                                SchemaObject(name = "fieldB", detail = Text(maxLength = 15))
                        )
                ),
                maxItems = 100
        ))
    }

    @Test
    fun `it converts a Reference`() {
        val testNode: JsonNode = mapOf("\$ref" to "definition").convert()

        val schema = nodeConverter.convert(testNode)

        assertThat(schema).isEqualTo(Reference(type = "definition"))
    }

    @Test
    fun `it converts a OneOf`() {
        val testNode: JsonNode = mapOf(
                "oneOf" to listOf(
                        mapOf("\$ref" to "definition"),
                        mapOf("type" to "string", "maxLength" to 15)
                )
        ).convert()

        val schema = nodeConverter.convert(testNode)

        assertThat(schema).isEqualTo(OneOf(
                list = listOf(
                        Reference(type = "definition"),
                        Text(maxLength = 15)
                )
        ))
    }

    @Test
    fun `a test`() {

        val result = FieldObject(name = "A").simpleFlatten(listOf("B", "C"))

        assertThat(result).isEqualTo(FieldObject(
                name = "A",
                properties = listOf(FieldObject(
                        name = "B",
                        properties = listOf(FieldObject(
                                name = "C",
                                properties = emptyList()))))))
    }

    @Test
    fun `merge`() {

        val result = listOf(
                FieldObject(name = "A", properties = listOf(FieldObject(name = "B", properties = listOf(FieldObject(name = "C", properties = emptyList()))))),
                FieldObject(name = "A", properties = listOf(FieldObject(name = "B", properties = listOf(FieldObject(name = "D", properties = emptyList()))))),
                FieldObject(name = "A", properties = listOf(FieldObject(name = "E", properties = emptyList())))
        ).sum()

        assertThat(result).isEqualTo(FieldObject(name = "A", properties = listOf(
                FieldObject(name = "B", properties = listOf(
                        FieldObject(name = "C"),
                        FieldObject(name = "D")
                )),
                FieldObject(name = "E")
        )))
    }

    @Test
    fun `test`() {

        val list = listOf(listOf("A", "B"), listOf("A", "C"))

        val fieldObject = flatten(list)

        assertThat(fieldObject).isEqualTo(FieldObject(
                name = "A",
                properties = listOf(FieldObject(name = "B"), FieldObject(name = "C"))
        ))
    }

    @Test
    fun `simple schema modifier`() {

        val schema = SchemaObject(name = "B", detail = Number)

        val fields = FieldObject(name = "B")

        val newSchema = SchemaReducerImpl().reduce(schemaObject = schema, fieldObject = fields)

        assertThat(newSchema).isEqualTo(schema)
    }

    @Test
    fun `schema modifier`() {

        val schema = SchemaObject(name = "A", detail = ComplexObject(properties = listOf(
                SchemaObject(name = "B", detail = Number),
                SchemaObject(name = "C", detail = Number),
                SchemaObject(name = "D", detail = Number)
        )))

        val fields = FieldObject(name = "A", properties = listOf(
                FieldObject(name = "C"),
                FieldObject(name = "D")
        ))

        val newSchema = SchemaReducerImpl().reduce(schemaObject = schema, fieldObject = fields)

        assertThat(newSchema).isEqualTo(SchemaObject(name = "A", detail = ComplexObject(properties = listOf(
                SchemaObject(name = "C", detail = Number),
                SchemaObject(name = "D", detail = Number)
        ))))
    }

    @Test
    fun `schema modifier more`() {

        val schema = SchemaObject(name = "A", detail = ComplexObject(properties = listOf(
                SchemaObject(name = "C", detail = Number),
                SchemaObject(name = "D", detail = ComplexObject(properties = listOf(
                        SchemaObject(name = "E", detail = Number),
                        SchemaObject(name = "F", detail = Number)
                )))
        )))

        val fields = FieldObject(name = "A", properties = listOf(
                FieldObject(name = "D", properties = listOf(
                        FieldObject(name = "E")
                ))
        ))

        val newSchema = SchemaReducerImpl().reduce(schemaObject = schema, fieldObject = fields)

        assertThat(newSchema).isEqualTo(SchemaObject(name = "A", detail = ComplexObject(properties = listOf(
                SchemaObject(name = "D", detail = ComplexObject(properties = listOf(
                        SchemaObject(name = "E", detail = Number)
                )))
        ))))
    }

    @Test
    fun `schema modifier array`() {

        val schema = SchemaObject(name = "A", detail = ComplexObject(properties = listOf(
                SchemaObject(name = "C", detail = Number),
                SchemaObject(name = "D", detail = Array(
                        itemType = ComplexObject(properties = listOf(
                                SchemaObject(name = "E", detail = Number),
                                SchemaObject(name = "F", detail = Number)
                        )),
                        maxItems = null
                ))
        )))

        val fields = FieldObject(name = "A", properties = listOf(
                FieldObject(name = "D", properties = listOf(
                        FieldObject(name = "E")
                ))
        ))

        val newSchema = SchemaReducerImpl().reduce(schemaObject = schema, fieldObject = fields)

        assertThat(newSchema).isEqualTo(SchemaObject(name = "A", detail = ComplexObject(properties = listOf(
                SchemaObject(name = "D", detail = Array(
                        itemType = ComplexObject(properties = listOf(
                                SchemaObject(name = "E", detail = Number)
                        )),
                        maxItems = null
                ))
        ))))
    }

    @Test
    fun `schema modifier array of arrays`() {

        val schema = SchemaObject(name = "A", detail = ComplexObject(properties = listOf(
                SchemaObject(name = "C", detail = Number),
                SchemaObject(name = "D", detail = Array(
                        itemType = Number,
                        maxItems = null
                ))
        )))

        val fields = FieldObject(name = "A", properties = listOf(
                FieldObject(name = "D")
        ))

        val newSchema = SchemaReducerImpl().reduce(schemaObject = schema, fieldObject = fields)

        assertThat(newSchema).isEqualTo(SchemaObject(name = "A", detail = ComplexObject(properties = listOf(
                SchemaObject(name = "D", detail = Array(
                        itemType = Number,
                        maxItems = null
                ))
        ))))
    }

    @Test
    fun `schema modifier one of`() {

        val schema = SchemaObject(name = "A", detail = ComplexObject(properties = listOf(
                SchemaObject(name = "C", detail = Number),
                SchemaObject(name = "ClaimDetail", detail = OneOf(list = listOf(
                        ComplexObject(properties = listOf(
                                SchemaObject(name = "E", detail = Number),
                                SchemaObject(name = "F", detail = Number)
                        ))
                )))
        )))

        val fields = FieldObject(name = "A", properties = listOf(
                FieldObject(name = "MedicalClaim", properties = listOf(
                        FieldObject(name = "E")
                ))
        ))

        val newSchema = SchemaReducerImpl().reduce(schemaObject = schema, fieldObject = fields)

        assertThat(newSchema).isEqualTo(SchemaObject(name = "A", detail = ComplexObject(properties = listOf(
                SchemaObject(name = "ClaimDetail", detail = OneOf(list = listOf(
                        ComplexObject(properties = listOf(
                                SchemaObject(name = "E", detail = Number)
                        ))
                )))
        ))))
    }

    @Test
    @Ignore //TODO Am I a thing? Maybe delete me
    fun `it converts the thing`() {

        val schema = schemaFileConverter.convert("src/main/resources/cts-schema.json")

//        val cinqFieldsFilePath = "/Users/xpdesktop/workspace/demo/fake-claims-generator/src/main/resources/cinq-fields.csv"

//        val fieldSetRows = cinqFieldsFilePath.readAndConvertCsvToFieldSetList()

//        val reducedSchemaFields = flatten(fieldSetRows)

//        val reducedSchema = SchemaReducerImpl().reduce(schema, reducedSchemaFields)

        val faker = Faker()

        val jsonSizer = JsonSizer()
        val jsonSizeAverager = JsonSizeAverager()

        val start = System.currentTimeMillis()

        val overview = 1.rangeTo(50)
                .map { schema.toJson() }
                .map { jsonSizer.calculateSize(it) }
                .map {
                    when (it) {
                        is Success -> it.content
                        is Failure -> throw RuntimeException("Error")
                    }
                }

        val end = System.currentTimeMillis() - start

//        val node = objectMapper.readValue<JsonNode>(schemaString)
//        val a = node.get("InsuranceClaim").get("ClaimDetail").get("ServiceLine").map { objectMapper.writeValueAsString(it).length }.average()

        println()
//        FileWriter("schema-${System.currentTimeMillis()}.json").apply {
//            write(schemaString)
//            close()
//        }
    }

    @Test
    @Ignore
    fun `find accumulator key histories`() {
        val listOfFileClaims = listOf("/Users/pivotal/workspace/json-schema-parser/deidentifiedClaims1.json",
                "/Users/pivotal/workspace/json-schema-parser/deidentifiedClaims2.json",
                "/Users/pivotal/workspace/json-schema-parser/deidentifiedClaims3.json",
                "/Users/pivotal/workspace/json-schema-parser/deidentifiedClaims4.json")


        val listOfFilterClaimStrings = listOfFileClaims.flatMap { String(File(it).readBytes()).split("\n") }.filterNot { it.isEmpty() }

        val accumulatorKeyHistories: List<JsonNode> = listOfFilterClaimStrings.map {
            objectMapper.readValue<JsonNode>(it).get("medicalClaim").get("accumulatorKeyHistory")
        }
    }

    @Test
    fun `it writes a Text as a string`() {

        val json = Text(maxLength = 10).toJsonable()

        assertThat(json.length).isLessThan(10)
    }

    @Test
    fun `it writes Text as X`() {
        assertThat(Text(maxLength = 1).toJsonable()).isEqualTo("X")
    }

    @Test
    fun `it writes all the object type SchemaDetails`() {
        assertThat(Date.toJsonable()).isEqualTo("1111/11/11")
        assertThat(DateTime.toJsonable()).isEqualTo("1111/11/11 11:11:11 UTC")
        assertThat(Number.toJsonable()).isEqualTo("100000000000.00000")
        assertThat(Integer.toJsonable()).isEqualTo("10000000000")
    }

    @Test
    fun `it writes a simple Complex Object to json`() {
        val map: Map<String, Any> = ComplexObject(properties = listOf(
                SchemaObject("A", detail = Text(maxLength = 2)),
                SchemaObject("B", detail = Text(maxLength = 4)),
                SchemaObject("C", detail = Text(maxLength = 6))
        )).toJsonable()

        map.forEach { entry: Map.Entry<String, Any> ->
            assertThat(entry.value as String).contains("X")
        }
    }

    @Test
    fun `it writes a complex Complex Object to json`() {
        val map = ComplexObject(properties = listOf(
                SchemaObject(name = "A", detail = ComplexObject(
                        properties = listOf(
                                SchemaObject(name = "1", detail = Text(maxLength = 1))
                        )
                ))
        )).toJsonable()

        assertThat(map).isEqualTo(mapOf("A" to mapOf("1" to "X")))
    }

    @Test
    fun `it writes a simple Array to List`() {
        val list = Array(itemType = Text(maxLength = 1), maxItems = 3).toJsonable()

        assertThat(list).containsExactlyInAnyOrder("X", "X", "X")
    }

    @Test
    fun `it writes a complex Array to List`() {
        val list = Array(itemType = Array(itemType = Text(maxLength = 1), maxItems = 3), maxItems = 1).toJsonable()

        assertThat((list.first() as List<*>).size).isLessThan(4)
    }

    @Test
    fun `it writes a complex Object Array to List`() {

        val itemType = ComplexObject(properties = listOf(SchemaObject(name = "A", detail = Text(maxLength = 1))))

        val list = Array(itemType = itemType, maxItems = 2).toJsonable()

        assertThat(list).contains(mapOf("A" to "X"))
    }

    @Test
    fun `it writes OneOf to something?`() {
        val thing = OneOf(list = listOf(
                Text(maxLength = 1)
        )).toJsonable()

        assertThat(thing).isEqualTo("X")
    }

    @Test
    fun `it converts a SchemaObject`() {
        val json = SchemaObject(name = "thing", detail = Text(maxLength = 5)).toJson()

        assertThat(json).matches("^(.*)thing(.*)(X+)(.*)$")
    }

    private inline fun <T, reified U : Any> T.convert() = objectMapper.convertValue(this, U::class.java)
}