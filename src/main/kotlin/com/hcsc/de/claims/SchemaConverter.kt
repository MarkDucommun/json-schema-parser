package com.hcsc.de.claims

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.util.*

interface SchemaFileConverter {

    fun convert(filePath: String): SchemaObject<*>
}

interface SchemaReducer {

    fun reduce(schemaObject: SchemaObject<*>, fieldObject: FieldObject): SchemaObject<*>
}

class SchemaReducerImpl : SchemaReducer {

    override fun reduce(schemaObject: SchemaObject<*>, fieldObject: FieldObject): SchemaObject<*> {

        return when (schemaObject.detail) {
            is ComplexObject -> schemaObject.copy(detail = schemaObject.detail.copy(
                    properties = schemaObject.detail.properties.reduceProperties(fieldObject = fieldObject)
            ))
            is Array -> {
                when (schemaObject.detail.itemType) {
                    is ComplexObject -> schemaObject.copy(detail = schemaObject.detail.copy(
                            itemType = schemaObject.detail.itemType.copy(
                                    properties = schemaObject.detail.itemType.properties.reduceProperties(fieldObject = fieldObject)
                            )
                    ))
                    else -> schemaObject
                }
            }
            is OneOf -> {
                if (schemaObject.name == "ClaimDetail" && fieldObject.name == "MedicalClaim") {
                    schemaObject.copy(detail = schemaObject.detail.copy(
                            list = schemaObject.detail.list.map { detail ->
                                when (detail) {
                                    is ComplexObject -> detail.copy(properties = detail.properties.reduceProperties(fieldObject = fieldObject))
                                    else -> detail
                                }
                            }
                    ))
                } else {
                    schemaObject
                }
            }
            else -> if (schemaObject.name == fieldObject.name) {
                schemaObject
            } else {
                throw RuntimeException("Names don't match, something went wrong")
            }
        }
    }

    fun List<SchemaObject<*>>.reduceProperties(fieldObject: FieldObject): List<SchemaObject<*>> {
        return this.map { property ->
            fieldObject
                    .properties
                    .find { it.name == property.name || it.name == "MedicalClaim" && property.name == "ClaimDetail" }
                    ?.let { innerFieldObject ->
                        reduce(property, innerFieldObject)
                    }
        }.filterNotNull()
    }
}


interface ListConverter {

    fun convert(node: JsonNode): List<SchemaObject<*>>
}

interface NodeConverter {

    fun convert(node: JsonNode): SchemaDetail
}

interface FileReader {

    fun read(filePath: String): JsonNode
}

fun flatten(nestedFieldList: List<List<String>>): FieldObject {

    return nestedFieldList.map { fieldList ->

        val fieldObjectName = fieldList.first()

        val remainingFields = fieldList.subList(1, fieldList.size)

        FieldObject(name = fieldObjectName).simpleFlatten(remainingFields)
    }.sum()
}

fun FieldObject.simpleFlatten(fieldList: List<String>): FieldObject {

    val fieldObjectName = fieldList.first()

    val remainingFields = fieldList.subList(1, fieldList.size)

    val newFieldObject = FieldObject(name = fieldObjectName)

    return copy(properties = this.properties.plus(if (remainingFields.isNotEmpty()) {
        newFieldObject.simpleFlatten(remainingFields)
    } else {
        newFieldObject
    }))
}

fun List<FieldObject>.sum(): FieldObject {
    return reduce(FieldObject::merge)
}

fun FieldObject.merge(other: FieldObject): FieldObject {

    val sharedProperties = properties
            .filter { (propertyName) -> other.properties.find { it.name == propertyName } != null }
            .map { property -> property.merge(other.properties.find { it.name == property.name }!!) }

    val unsharedProperties = properties.filterNot { (propertyName) -> other.properties.find { it.name == propertyName } != null }

    val unsharedOtherProperties = other.properties.filterNot { (propertyName) -> properties.find { it.name == propertyName } != null }

    val finalProperties = listOf(unsharedProperties, sharedProperties, unsharedOtherProperties).flatten()

    return this.copy(properties = finalProperties)
}

data class FieldObject(
        val name: String = "",
        val properties: List<FieldObject> = emptyList()
)

class SchemaFileConverterImpl(
        private val reader: FileReader,
        private val listConverter: ListConverter
) : SchemaFileConverter {

    override fun convert(filePath: String): SchemaObject<*> {

        val baseNode = reader.read(filePath = filePath)

        val convertedDefinitions = listConverter.convert(node = baseNode.definitions)

        val replacedConvertedDefinitions = convertedDefinitions.map {
            it.recursivelyReplaceReferences(definitions = convertedDefinitions)
        }

        val initialSchema = listConverter.convert(node = baseNode.properties).first()

        return initialSchema.recursivelyReplaceReferences(definitions = replacedConvertedDefinitions)

    }
}

private fun SchemaObject<*>.recursivelyReplaceReferences(
        definitions: List<SchemaObject<*>>
): SchemaObject<*> {

    return copy(detail = when (this.detail) {
        is ComplexObject -> this.detail.transformProperties { it.recursivelyReplaceReferences(definitions) }
        is Array -> this.detail.transformItemType { itemType ->
            when (itemType) {
                is Reference -> definitions.findSchemaDetail(itemType.type)
                is ComplexObject -> itemType.transformProperties { it.recursivelyReplaceReferences(definitions) }
                else -> itemType
            }
        }
        is OneOf -> this.detail.transformList { item ->
            when (item) {
                is Reference -> definitions.findSchemaDetail(item.type)
                else -> this.detail
            }
        }
        is Reference -> definitions.findSchemaDetail(this.detail.type)
        else -> this.detail
    })
}

private fun Array.transformItemType(transform: (SchemaDetail) -> SchemaDetail): SchemaDetail {
    return copy(itemType = transform(itemType))
}

private fun ComplexObject.transformProperties(transform: (SchemaObject<*>) -> SchemaObject<*>): ComplexObject {
    return ComplexObject(properties = this.properties.map(transform))
}

private fun OneOf.transformList(transform: (SchemaDetail) -> SchemaDetail): OneOf {
    return OneOf(list = this.list.map(transform))
}

private fun List<SchemaObject<*>>.findSchemaDetail(string: String): SchemaDetail =
        this.find { string.contains(it.name) }?.detail ?: throw Exception("Reference matching $string was not found")

private val JsonNode.definitions get() = getOrThrow("definitions")

private val JsonNode.properties get() = getOrThrow("properties")

private val JsonNode.type get() = get("type")?.asText() ?: "other"

private val JsonNode.format get() = get("format")?.asText() ?: "other"

private val JsonNode.maxLength get() = getOrThrow("maxLength")

private val JsonNode.items get() = getOrThrow("items")

private val JsonNode.maxItems get() = get("maxItems")?.asInt()

private val JsonNode.ref get() = get("\$ref")

private val JsonNode.oneOf get() = get("oneOf")

private fun JsonNode.getOrThrow(key: String) = get(key) ?: throw Exception("No $key exist(s) on this node")

class ListConverterImpl(
        val nodeConverter: NodeConverter
) : ListConverter {

    override fun convert(node: JsonNode): List<SchemaObject<*>> {
        return node.fieldNames.map {
            SchemaObject(name = it, detail = nodeConverter.convert(node.get(it)))
        }
    }
}

class NodeConverterImpl : NodeConverter {

    override fun convert(node: JsonNode): SchemaDetail =
            when (node.type) {
                "string" -> when (node.format) {
                    "date" -> Date
                    "date-time" -> DateTime
                    else -> Text(maxLength = node.maxLength.asInt())
                }
                "number" -> Number
                "integer" -> Integer
                "object" -> ComplexObject(properties = node.properties.fieldNames.map {
                    SchemaObject(name = it, detail = convert(node.properties.get(it)))
                })
                "array" -> Array(itemType = convert(node.items), maxItems = node.maxItems)
                "other" -> when {
                    node.ref != null -> Reference(type = node.ref.asText())
                    node.oneOf != null -> OneOf(list = (node.oneOf as ArrayNode).map { convert(node = it) })
                    else -> throw Exception("Unhandled schema type")
                }
                else -> throw Exception("Unhandled schema type")
            }
}

data class SchemaObject<out detailType : SchemaDetail>(
        val name: String,
        val detail: detailType
) {
    fun toJson(): String {

        return ObjectMapper().writeValueAsString(mapOf(name to detail.toJsonable()))
    }
}

sealed class SchemaDetail {
    abstract fun toJsonable(): Any
}

data class Text(
        val maxLength: Int
) : SchemaDetail() {

    override fun toJsonable(): String {
        return 1.rangeTo(random(maxLength) + 1).fold("") { acc, _ -> acc.plus("X") }
    }
}

object Date : SchemaDetail() {

    override fun toJsonable(): String {
        return "1111/11/11"
    }
}

object DateTime : SchemaDetail() {

    override fun toJsonable(): String {
        return "1111/11/11 11:11:11 UTC"
    }
}

object Number : SchemaDetail() {

    override fun toJsonable(): String {
        return "100000000000.00000"
    }
}

object Integer : SchemaDetail() {

    override fun toJsonable(): String {
        return "10000000000"
    }
}

data class Reference(val type: String) : SchemaDetail() {

    override fun toJsonable(): String {
        throw Exception("I should have been replaced")
    }
}

val random = Random()

fun random(i: Int): Int = random.nextInt(i)

data class Array(
        val itemType: SchemaDetail,
        val maxItems: Int?
) : SchemaDetail() {
    override fun toJsonable(): List<Any> {
        return List(maxItems?.let { random(it) + 1 } ?: random(5) + 1 ) {
            itemType.toJsonable()
        }
    }
}

data class ComplexObject(
        val properties: List<SchemaObject<*>>
) : SchemaDetail() {

    override fun toJsonable(): Map<String, Any> {
        return properties.map {
            val toJson = it.detail.toJsonable()
            it.name to toJson
        }.toMap()
    }
}

data class OneOf(
        val list: List<SchemaDetail>
) : SchemaDetail() {

    override fun toJsonable(): Any {
        return list.first().toJsonable()
    }
}
