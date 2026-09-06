package com.github.nndwn.todoso.services

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project



@State(
    name = "com.github.nndwn.todoso.services.TodosoSettingsService",
    storages = [Storage("TodosoSettings.xml")]
)
@Service(Service.Level.PROJECT)
class TodosoSettingsService (val project : Project) : PersistentStateComponent<TodosoSettingsService.State>{

    data class State(
        var visualEnabled: Boolean = true,
        var todoFilePath: String = "",
        var priorityFilterName: String? = null,
        var statusFilterName: String? = null
    )

    private var stateSettings = State()

    override fun getState(): State = stateSettings

    override fun loadState(state: State) {
        stateSettings = state
    }
    companion object {
        fun getInstance(project: Project): TodosoSettingsService = project.service()
    }
}