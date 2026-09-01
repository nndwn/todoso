package com.github.nndwn.todoso

import com.intellij.openapi.util.IconLoader

object MyIcons {
  @JvmField val Logo = IconLoader.getIcon("/icons/todo.svg", MyIcons::class.java)
  @JvmField val TaskTodo = IconLoader.getIcon("/icons/taskTodo.svg", MyIcons::class.java)
  @JvmField val TaskDoing = IconLoader.getIcon("/icons/taskDoing.svg", MyIcons::class.java)
  @JvmField val TaskDone = IconLoader.getIcon("/icons/taskDone.svg", MyIcons::class.java)
  @JvmField val TaskCancelled = IconLoader.getIcon("/icons/taskCancelled.svg", MyIcons::class.java)
}
