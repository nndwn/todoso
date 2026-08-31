package com.github.nndwn.todoso.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {

  override suspend fun execute(project: Project) {
    // Project startup logic if needed
  }
}
