/*
 * Copyright 2019-2019 Simon Ogorodnik.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package me.semoro.tabnine

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ReindexAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        TabNineIndexService.getInstance(project).index()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

}