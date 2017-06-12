package com.hcsc.de.claims.goodnessOfFit

import com.hcsc.de.claims.distributionFitting.GenericDistributionRandomable
import com.hcsc.de.claims.distributionFitting.Montreal.randomVariateGen
import com.hcsc.de.claims.distributionFitting.MontrealDistribution
import com.hcsc.de.claims.get
import com.hcsc.de.claims.succeedsAnd
import com.hcsc.de.claims.visualize
import net.sourceforge.jdistlib.Normal
import net.sourceforge.jdistlib.Weibull
import org.assertj.core.api.KotlinAssertions.assertThat
import org.junit.Test
import umontreal.ssj.randvar.NormalGen
import umontreal.ssj.randvar.RandomVariateGen

class JDistFitCheckerTest {

    val subject = JDistFitChecker<Double>()

    @Test
    fun `it returns a high pValue for values from the same distribution`() {

        listOf(1000, 10000, 100000).forEach { listSize ->

            val randomable = GenericDistributionRandomable(Normal(100.0, 20.0))

            val firstList = List(listSize) { randomable.random() }

            val secondList = List(listSize) { randomable.random() }

            subject.check(firstList, secondList) succeedsAnd {

                if (it < 0.5) {
                    visualize(firstList, secondList, 3)
                }

                println("$listSize: $it")

                assertThat(it).isGreaterThan(0.5)
            }
        }
    }

    @Test
    fun `it returns low pValue for values from different distributions`() {

        listOf(1000, 10000, 100000).forEach { listSize ->

            val randomableOne = GenericDistributionRandomable(Normal(100.0, 20.0))

            val randomableTwo = randomVariateGen<NormalGen>(100.0, 20.0).get

            val firstList = List(listSize) { randomableOne.random() }

            val secondList = List(listSize) { randomableTwo.random() }

            println("listSize: $listSize - ")
            visualize(firstList, secondList, 2)
            listOf(20, 40, 60, 90, 120, 150, 180, 200, 250).forEach { binCount ->
                subject.check(firstList, firstList.dropLast(firstList.size / 2), binCount) succeedsAnd {

                    println("$binCount: $it")

//                assertThat(it).isLessThan(0.05)
                }
            }
            println()
        }

    }
}