package com.github.nndwn.todoso.services

import com.github.nndwn.todoso.TodosoConstants
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil


@State(
    name = "com.github.nndwn.todoso.services.TodosoSettingsService",
    storages = [Storage("TodosoSettings.xml")]
)
@Service(Service.Level.PROJECT)
class TodosoSettingsService : PersistentStateComponent<TodosoSettingsService.State> {

    data class State(
        var visualEnabled: Boolean = true,
        var todoFilePath: String = TodosoConstants.FILENAME,
        var priorityFilterName: String? = null,
        var statusFilterName: String? = null,
        var sortOption: String = "DEFAULT"
    )

    private val myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    companion object {
        fun getInstance(project: Project): TodosoSettingsService = project.service()
    }
}