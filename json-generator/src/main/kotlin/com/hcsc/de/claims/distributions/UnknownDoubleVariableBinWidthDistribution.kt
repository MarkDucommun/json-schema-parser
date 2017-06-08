package com.hcsc.de.claims.distributions

import com.hcsc.de.claims.helpers.median
import com.hcsc.de.claims.helpers.mode
import java.util.*

data class UnknownDoubleVariableBinWidthDistribution(
        override val average: Double,
        override val minimum: Double,
        override val maximum: Double,
        override val median: Double,
        override val mode: Double,
        override val bins: List<VariableWidthBin<Double>>
) : UnknownVariableBinWidthDistribution<Double> {

    private val random = Random()

    override fun random(): Double {

        val index = random.nextInt(bins.size)

        val bin = bins[index]

        return bin.members[random.nextInt(bin.members.size)]
    }

    override val numberOfBins: Int = bins.size
}

fun List<Double>.unknownVariableBinWidthDistribution(
        binCount: Int = 5,
        rangeMinimum: Double? = null,
        rangeMaximum: Double? = null
): UnknownVariableBinWidthDistribution<Double> {

    val sorted: List<Double> = this.sorted()

    val sortedAndFilteredByMinimum = rangeMinimum
            ?.let { sorted.filterNot { it < rangeMinimum } }
            ?: sorted

    val sortedAndFilteredByMinimumAndMaximum = rangeMaximum
            ?.let { sortedAndFilteredByMinimum.filterNot { it > rangeMaximum } }
            ?: sortedAndFilteredByMinimum

    val observedMinimum = sortedAndFilteredByMinimumAndMaximum.min() ?: 0.0
    val observedMaximum = sortedAndFilteredByMinimumAndMaximum.max() ?: 0.0

    return UnknownDoubleVariableBinWidthDistribution(
            average = sortedAndFilteredByMinimumAndMaximum.average(),
            minimum = observedMinimum,
            maximum = observedMaximum,
            median = sortedAndFilteredByMinimumAndMaximum.median(),
            mode = sortedAndFilteredByMinimumAndMaximum.mode(),
            bins = sortedAndFilteredByMinimumAndMaximum.asDoubleBin.maximizeBins(binCount)
    )
}

private fun VariableWidthBin<Double>.maximizeBins(binCount: Int): List<VariableWidthBin<Double>> {

    val median = members.average()

    val lower = members.filterNot { it >= median }

    val upper = members.filter { it >= median }

    return if (endValue - startValue > 0 && lower.count() > 5 && upper.count() > binCount) {

        listOf(lower.asDoubleBin.maximizeBins(binCount), upper.asDoubleBin.maximizeBins(binCount)).flatten()
    } else {
        listOf(this)
    }
}

private val List<Double>.asDoubleBin: VariableWidthBin<Double>
    get() = VariableWidthBin(
            startValue = this.min() ?: 0.0,
            endValue = this.max() ?: 0.0,
            members = this.sorted()
    )