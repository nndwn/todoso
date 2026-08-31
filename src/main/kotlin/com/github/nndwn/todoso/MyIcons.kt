package com.github.nndwn.todoso

import com.intellij.openapi.util.IconLoader

object MyIcons {
  @JvmField val Todo = IconLoader.getIcon("/icons/todo.svg", MyIcons::class.java)

  @JvmField val Add = IconLoader.getIcon("/icons/add.svg", MyIcons::class.java)

  @JvmField val Refresh = IconLoader.getIcon("/icons/refresh.svg", MyIcons::class.java)

  @JvmField val Done = IconLoader.getIcon("/icons/done.svg", MyIcons::class.java)
}
