/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package test.collections

import org.junit.Assume
import org.junit.Test
import kotlin.test.*

class IndexOverflowJVMTest {

    @BeforeTest
    fun checkIsNotIgnored() {
        Assume.assumeTrue(System.getProperty("long.sequences.tests")?.toBoolean() ?: false)
    }


    companion object {
        fun <T> repeatInfinite(value: T): Sequence<T> = Sequence {
            object : Iterator<T> {
                override fun hasNext(): Boolean = true
                override fun next(): T = value
            }
        }

        val infiniteSequence = repeatInfinite("k")
        val infiniteIterable = infiniteSequence.asIterable()

        val longCountSequence = Sequence {
            object : Iterator<Long> {
                var counter = 0L
                override fun hasNext(): Boolean = true
                override fun next(): Long = counter++
            }
        }
    }



    @Test
    fun dropTwiceMaxValue() {

        val halfMax = (1 shl 30) + 1

        val dropOnce = longCountSequence.drop(halfMax)
        val dropTwice = dropOnce.drop(halfMax)

        val expectedEnd = halfMax.toLong() * 2

        assertEquals(expectedEnd, dropTwice.first())

        val dropTake = dropOnce.take(halfMax + 1)

        assertEquals(expectedEnd, dropTake.last())
    }

}