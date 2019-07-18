/*
 * Copyright 2019-2019 Simon Ogorodnik.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package me.semoro.tabnine

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase.DIRECT_INSERTION
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

class TabNineCompletionContributor : CompletionContributor() {
    init {
        val provider = object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                result.restartCompletionOnAnyPrefixChange()
                performCompletion(parameters, result)
            }
        }

        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), provider)
        extend(CompletionType.SMART, PlatformPatterns.psiElement(), provider)
    }

    private fun performCompletion(parameters: CompletionParameters, result: CompletionResultSet) {
//        parameters.originalFile.
        val project = parameters.editor.project ?: return

        val document = parameters.editor.document


        val beginningOffset = maxOf(parameters.offset - 50000, 0)
        val endingOffset = minOf(document.textLength, parameters.offset + 50000)



        val before = document.getText(TextRange.create(beginningOffset, parameters.offset))
        val after = document.getText(TextRange.create(parameters.offset, endingOffset))

        val response = TabNineCommunicationHandler.getInstance(project)
            .autocomplete(
                Protocol.Autocomplete(
                    before,
                    after,
                    parameters.originalFile.virtualFile.path,
                    beginningOffset == 0,
                    endingOffset == document.textLength
                )
            )

        val insertionHandler = TabNineInsertionHandler()

        for ((index, entry) in response.results.withIndex()) {

            var element = LookupElementBuilder.create(ItemData(response.old_prefix, entry, index))
            if (entry.deprecated == true)
                element = element.strikeout()
            element = element.withInsertHandler(insertionHandler)
            element.putUserData(DIRECT_INSERTION, true)


            result.addElement(PrioritizedLookupElement.withPriority(element, 10.0))
        }

        for (userMessage in response.user_message) {
            result.addLookupAdvertisement(userMessage)
        }

    }



}