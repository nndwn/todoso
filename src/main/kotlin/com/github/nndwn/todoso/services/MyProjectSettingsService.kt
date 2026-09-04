package com.github.nndwn.todoso.services

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
  name = "com.github.nndwn.todoso.services.MyProjectSettingsService",
  storages = [Storage("TodosoSettings.xml")],
)
class MyProjectSettingsService(val project: Project) : PersistentStateComponent<MyProjectSettingsService.State> {

  class State {
    var visualEnabled: Boolean = true
    var todoFileName: String = "todo.md"
    var priorityFilterName: String? = null
    var statusFilterName: String? = null
    var isProtocolInjected: Boolean = false
  }

  private var myState = State()

  override fun getState(): State = myState

  override fun loadState(state: State) {
    myState = state
  }

  companion object {
    fun getInstance(project: Project): MyProjectSettingsService = project.service()
  }
}
