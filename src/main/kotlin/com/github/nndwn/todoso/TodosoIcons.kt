package com.github.nndwn.todoso

import com.intellij.openapi.util.IconLoader

object TodosoIcons {
  @Suppress("unused") @JvmField val Logo = IconLoader.getIcon("/icons/todo.svg", TodosoIcons::class.java)
  @JvmField val TaskTodo = IconLoader.getIcon("/icons/taskTodo.svg", TodosoIcons::class.java)
  @JvmField val TaskDoing = IconLoader.getIcon("/icons/taskDoing.svg", TodosoIcons::class.java)
  @JvmField val TaskDone = IconLoader.getIcon("/icons/taskDone.svg", TodosoIcons::class.java)
  @JvmField val TaskCancelled = IconLoader.getIcon("/icons/taskCancelled.svg", TodosoIcons::class.java)

  @JvmField val AddFile = IconLoader.getIcon("/icons/addfile.svg", TodosoIcons::class.java)
  @JvmField val FolderMd = IconLoader.getIcon("/icons/foldermd.svg", TodosoIcons::class.java)
  @JvmField val Highest = IconLoader.getIcon("/icons/highest.svg", TodosoIcons::class.java)
  @JvmField val High = IconLoader.getIcon("/icons/high.svg", TodosoIcons::class.java)
  @JvmField val Medium = IconLoader.getIcon("/icons/medium.svg", TodosoIcons::class.java)
  @JvmField val Low = IconLoader.getIcon("/icons/low.svg", TodosoIcons::class.java)
  @JvmField val Lowest = IconLoader.getIcon("/icons/lowest.svg", TodosoIcons::class.java)
}
