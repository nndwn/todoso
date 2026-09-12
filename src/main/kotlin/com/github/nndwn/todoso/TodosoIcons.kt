package com.github.nndwn.todoso

import com.intellij.openapi.util.IconLoader

object TodosoIcons {
  @JvmField val Logo = IconLoader.getIcon("/icons/todo.svg", TodosoIcons::class.java)
  @JvmField val TaskTodo = IconLoader.getIcon("/icons/taskTodo.svg", TodosoIcons::class.java)
  @JvmField val TaskDoing = IconLoader.getIcon("/icons/taskDoing.svg", TodosoIcons::class.java)
  @JvmField val TaskDone = IconLoader.getIcon("/icons/taskDone.svg", TodosoIcons::class.java)
  @JvmField val TaskCancelled = IconLoader.getIcon("/icons/taskCancelled.svg", TodosoIcons::class.java)


  @JvmField val AddFile = IconLoader.getIcon("/icons/addfile.svg", TodosoIcons::class.java)
  @JvmField val FolderMd = IconLoader.getIcon("/icons/foldermd.svg", TodosoIcons::class.java)

}
