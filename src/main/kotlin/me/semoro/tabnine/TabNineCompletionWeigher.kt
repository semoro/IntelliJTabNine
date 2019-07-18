/*
 * Copyright 2019-2019 Simon Ogorodnik.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package me.semoro.tabnine

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.lookup.LookupElement

class TabNineCompletionWeigher : CompletionWeigher() {
    override fun weigh(element: LookupElement, location: CompletionLocation): Comparable<Nothing> {
        val itemData = element.`object` as? ItemData
        return if (itemData != null) {
            Weight(element.lookupString, itemData.order, isTabNine = true)
        } else {
            Weight(element.lookupString)
        }
    }


    class Weight(private val str: String, private val order: Int = 0, val isTabNine: Boolean = false) : Comparable<Weight> {

        override fun compareTo(other: Weight): Int {
            if (!this.isTabNine && !other.isTabNine) return 0
            if (this.isTabNine && other.isTabNine) return this.order.compareTo(other.order)

            if (this.isTabNine && !other.isTabNine) {
                if (other.str == this.str) return 1
                return other.str.length.compareTo(this.str.length)
            }

            return other.compareTo(this)
        }
    }
}