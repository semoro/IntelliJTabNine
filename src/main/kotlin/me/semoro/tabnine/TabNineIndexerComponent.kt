/*
 * Copyright 2019-2019 Simon Ogorodnik.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package me.semoro.tabnine

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScopes


class TabNineIndexerComponent : BaseComponent {

    private val connectionDisposable = Disposer.newDisposable()
    override fun initComponent() {
        super.initComponent()
        ApplicationManager.getApplication().messageBus.connect(connectionDisposable).subscribe(ProjectManager.TOPIC,
            TabNineProjectListener()
        )
    }

    override fun disposeComponent() {
        super.disposeComponent()
        connectionDisposable.dispose()
    }

}

class TabNineIndexService(val project: Project) {

    private var runningTask: IndexingTask? = null

    fun index() {
        val task = object : IndexingTask(project) {
            override fun onFinished() {
                runningTask = null
            }

            override fun onCancel() {
                runningTask = null
            }
        }
        runningTask = task
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(
            task,
            BackgroundableProcessIndicator(task)
        )


    }

    fun cancelIndex() {
        runningTask?.progressIndicator?.cancel()
    }
    companion object {
        fun getInstance(project: Project): TabNineIndexService {
            return ServiceManager.getService(project, TabNineIndexService::class.java)
        }
    }
}

val supportedFileTypes = setOf(
    "kt",
    "java"
)

class TabNineProjectListener : ProjectManagerListener {



    override fun projectOpened(project: Project) {
        super.projectOpened(project)

        DumbService.getInstance(project).smartInvokeLater { TabNineIndexService.getInstance(project).index() }
    }
    override fun projectClosing(project: Project) {
        super.projectClosing(project)
        TabNineIndexService.getInstance(project).cancelIndex()
    }

}

private open class IndexingTask(project: Project) : Task.Backgroundable(project, "TabNine indexing", true) {
    override fun run(indicator: ProgressIndicator) {
        progressIndicator = indicator
        for (fileTypeExt in supportedFileTypes) {
            DumbService.getInstance(project).waitForSmartMode()
            val fileType = FileTypeRegistry.getInstance().getFileTypeByExtension(fileTypeExt)
            runReadAction {
                val files = FileTypeIndex.getFiles(
                    fileType,
                    GlobalSearchScopes.projectProductionScope(project)
                        .uniteWith(GlobalSearchScopes.projectTestScope(project))
                )

                val communicationHandler = TabNineCommunicationHandler.getInstance(project)
                for ((count, file) in files.withIndex()) {
                    ProgressManager.checkCanceled()
                    indicator.fraction = count / files.size.toDouble()
                    ProgressManager.progress(file.path)
                    communicationHandler.prefetch(file.path)
                }
            }
        }

    }

    var progressIndicator: ProgressIndicator? = null

}
