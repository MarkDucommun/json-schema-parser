package com.hcsc.de.claims.jsonSizing

import com.hcsc.de.claims.helpers.*
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import kotlin.reflect.KClass

class JsonSizeAnalyzer(private val scheduler: Scheduler = Schedulers.trampoline()) {

    fun generateJsonSizeOverview(nodes: List<JsonSizeNode>): SingleResult<String, JsonSizeOverview> {

        return nodes.generateOverview()
    }

    fun List<JsonSizeNode>.generateOverview(): SingleResult<String, JsonSizeOverview> {

        return ensureNodesHaveSameName()
                .flatMapSuccess { ensureNodesAreSameType() }
                .flatMapSuccess { type ->
                    when (type) {
                        "JsonSizeLeafNode" -> this.normalizeNodes<JsonSizeLeafNode>().generateAveragedLeafNode()
                        "JsonSizeArray" -> this.normalizeNodes<JsonSizeArray>().generateAveragedArrayNode()
                        "JsonSizeObject" -> this.normalizeNodes<JsonSizeObject>().generateAveragedObjectNode()
                        else -> singleEmptyLeafOverview(name = firstOrNull()?.name ?: "")
                    }
                }
    }

    private inline fun <reified T : JsonSizeNode> List<JsonSizeNode>.normalizeNodes(): List<T> {
        return this.map { node: JsonSizeNode ->
            when (node) {
                is JsonSizeEmpty -> {
                    when (T::class) {
                        JsonSizeArray::class -> emptySizeArray(name = node.name) as T
                        JsonSizeObject::class -> emptySizeObject(name = node.name) as T
                        JsonSizeLeafNode::class -> emptySizeLeafNode(name = node.name) as T
                        else -> throw RuntimeException("Should not be normalizing to empty")
                    }
                }
                else -> node as T
            }
        }
    }

    private fun List<JsonSizeLeafNode>.generateAveragedLeafNode(): SingleResult<String, JsonSizeOverview> {

        return doOnComputationThread {

            Success<String, JsonSizeOverview>(JsonSizeLeafOverview(
                    name = first().name,
                    size = sizeDistribution
            ))
        }
    }

    private fun List<JsonSizeObject>.generateAveragedObjectNode(): SingleResult<String, JsonSizeOverview> {

        return this.collectAllChildrenNames().flatMapSuccess { childrenNames ->

            val a: List<SingleResult<String, JsonSizeOverview>> = this.generateOverviewsForEachChildByNames(childrenNames)

            childrenNames
                    .map { name ->

                        doOnComputationThreadAndFlatten {

                            val findAllChildrenByName = this.findAllChildrenByName(name)

                            val difference = size - findAllChildrenByName.size

                            val presence = List(difference) { 0.0 }.plus(List(findAllChildrenByName.size) { 1.0 }).distribution

                            findAllChildrenByName.generateOverview().flatMapSuccess { it: JsonSizeOverview ->
                                val child: JsonSizeObjectChild = JsonSizeObjectChild(overview = it, presence = presence)
                                Single.just(Success<String, JsonSizeObjectChild>(child)) as SingleResult<String, JsonSizeObjectChild> // TODO send this to Intellij
                            }
                        }
                    }
                    .concat()
                    .toList()
                    .map { results ->
                        results.traverse().map {
                            JsonSizeObjectOverview(
                                    name = first().name,
                                    size = this.sizeDistribution,
                                    children = it
                            ) as JsonSizeOverview
                        }
                    }
        }
    }

    private fun List<JsonSizeObject>.generateOverviewsForEachChildByNames(names: Set<String>) =
            names.map { name -> doOnComputationThreadAndFlatten { this.findAllChildrenByName(name).generateOverview() } }

    private fun List<JsonSizeObject>.findAllChildrenByName(name: String): List<JsonSizeNode> {
        return map { it.children.find { it.name == name } }.filterNotNull()
    }

    private fun List<JsonSizeArray>.generateAveragedArrayNode(): SingleResult<String, JsonSizeOverview> {

        return flatMap { array -> array.childrenWithNormalizedNames }.generateOverview().mapSuccess { averageChild ->

            Success<String, JsonSizeOverview>(content = JsonSizeArrayOverview(
                    name = first().name,
                    size = this.sizeDistribution,
                    averageChild = averageChild,
                    numberOfChildren = numberOfChildrenDistribution
            ))
        }
    }

    private fun List<JsonSizeNode>.ensureNodesHaveSameName(): SingleResult<String, Unit> {

        return doOnComputationThread {

            if (this.map(JsonSizeNode::name).toSet().size > 1) {
                Failure<String, Unit>(content = "Nodes do not match")
            } else {
                EMPTY_SUCCESS
            }
        }
    }

    private fun List<JsonSizeNode>.ensureNodesAreSameType(): SingleResult<String, String> {

        return doOnComputationThread {

            val associatedByType: Map<KClass<out JsonSizeNode>, List<JsonSizeNode>> = groupBy { it::class }
            val types = associatedByType.keys.map { it.simpleName }.filterNotNull()
            val numberOfTypes = associatedByType.keys.size

            when (numberOfTypes) {
                2 -> {
                    if (types.contains("JsonSizeEmpty")) {
                        Success<String, String>(content = types.filterNot { it == "JsonSizeEmpty" }.first())
                    } else {
                        Failure<String, String>(content = "Nodes are not the same type")
                    }
                }
                1 -> Success<String, String>(content = types.first())
                0 -> Success<String, String>(content = "")
                else -> Failure<String, String>(content = "Nodes are not the same type")
            }
        }
    }

    private fun List<JsonSizeObject>.collectAllChildrenNames(): SingleResult<String, Set<String>> {

        return doOnComputationThread {
            Success<String, Set<String>>(flatMap { it.children.map { it.name } }.toSet())
        }
    }

    private val JsonSizeObject.fields get() = children.map(JsonSizeNode::name)

    private val JsonSizeArray.childrenWithNormalizedNames: List<JsonSizeNode> get() {
        return children.map {
            when (it) {
                is JsonSizeLeafNode -> it.copy(name = "averageChild")
                is JsonSizeObject -> it.copy(name = "averageChild")
                is JsonSizeArray -> it.copy(name = "averageChild")
                is JsonSizeEmpty -> it.copy(name = "averageChild")
            }
        }
    }

    private val List<JsonSizeNode>.sizeDistribution: NormalIntDistribution get() = map(JsonSizeNode::size).distribution

    private val JsonSizeArray.numberOfChildren: Int get() = children.size

    private val List<JsonSizeArray>.numberOfChildrenDistribution: NormalIntDistribution
        get() = map { it.numberOfChildren }.distribution

    private val List<Int>.distribution: NormalIntDistribution get() {

        val average = averageInt()

        return NormalIntDistribution(
                average = average,
                minimum = min() ?: 0,
                maximum = max() ?: 0,
                standardDeviation = map { member -> (member - average).square() }.average().sqrt()
        )
    }

    // TODO COMMONIZE THIS FUNCTION
    private val List<Double>.distribution: NormalDoubleDistribution get() {

        val average = average()

        return NormalDoubleDistribution(
                average = average,
                minimum = min() ?: 0.0,
                maximum = max() ?: 0.0,
                standardDeviation = map { member -> (member - average).square() }.average().sqrt()
        )
    }

    private val EMPTY_DISTRIBUTION = NormalIntDistribution(average = 0, minimum = 0, maximum = 0, standardDeviation = 0.0)

    private fun <T> doOnComputationThread(fn: () -> T): Single<T> {
        return doOnThread(scheduler = scheduler, fn = fn)
    }

    private fun <T> doOnComputationThreadAndFlatten(fn: () -> Single<T>): Single<T> {
        return doOnThreadAndFlatten(scheduler = scheduler, fn = fn)
    }

    private fun singleEmptyLeafOverview(name: String): SingleResult<String, JsonSizeOverview> =
            Single.just(Success<String, JsonSizeOverview>(content = emptyLeafOverview(name = name)))

    private fun emptyLeafOverview(name: String): JsonSizeOverview = JsonSizeLeafOverview(name = name, size = EMPTY_DISTRIBUTION)

    private fun emptySizeArray(name: String) = JsonSizeArray(name = name, size = 0, children = emptyList())

    private fun emptySizeObject(name: String) = JsonSizeObject(name = name, size = 0, children = emptyList())

    private fun emptySizeLeafNode(name: String) = JsonSizeLeafNode(name = name, size = 0)
}
