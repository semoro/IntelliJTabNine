package me.semoro.tabnine

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.application.runWriteAction

class TabNineInsertionHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        runWriteAction {
            context.commitDocument()
            val itemData = item.`object` as ItemData
            context.document.replaceString(
                context.tailOffset - itemData.oldPrefix.length,
                context.tailOffset,
                itemData.entry.new_prefix
            )
            context.document.replaceString(
                context.tailOffset,
                context.tailOffset + itemData.entry.old_suffix.length,
                itemData.entry.new_suffix
            )
            context.commitDocument()
            context.editor.caretModel.moveToOffset(context.tailOffset - itemData.entry.new_suffix.length)
        }
    }

}

data class ItemData(val oldPrefix: String, val entry: Protocol.ResultEntry, val order: Int) {
    override fun toString(): String {
        return entry.new_prefix + entry.new_suffix
    }
}