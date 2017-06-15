package com.hcsc.de.claims.distributions.binDistributions

import com.hcsc.de.claims.distributions.bins.BinWithMembers
import java.util.*

data class BinsWithMembersDistribution<numberType : Number>(
        override val average: numberType,
        override val minimum: numberType,
        override val maximum: numberType,
        override val mode: numberType,
        override val median: numberType,
        override val bins: List<BinWithMembers<numberType>>
) : BinDistribution<numberType, BinWithMembers<numberType>> {

    private val random = Random()

    override val binCount: Int = bins.size

    override fun random(): numberType {

        val index = random.nextInt(binCount)

        val bin = bins[index]

        return bin.members[random.nextInt(bin.members.size)]
    }
}
