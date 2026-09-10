package com.github.nndwn.todoso.services

import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.toolWindow.TodosoMainPanel
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TodosoCacheSystemTest : BasePlatformTestCase() {

  private lateinit var service: TodosoService
  private lateinit var settings: TodosoSettingsService

  override fun setUp() {
    super.setUp()
    service = project.getService(TodosoService::class.java)
    settings = TodosoSettingsService.getInstance(project)
    settings.state.todoFilePath = TodosoConstants.FILENAME
    service.markCacheDirty()
  }

  fun testReloadOnPathChange() {
    myFixture.addFileToProject("file1.md", "- [ ] Task in File 1")
    myFixture.addFileToProject("file2.md", "- [ ] Task in File 2")

    settings.state.todoFilePath = "file1.md"
    assertEquals("Task in File 1", service.loadTask()[0].description)

    settings.state.todoFilePath = "file2.md"
    assertEquals("Harusnya memuat ulang karena path berubah", "Task in File 2", service.loadTask()[0].description)
  }

  fun testVfsListenerUpdatesCache() {
    val file = myFixture.addFileToProject(TodosoConstants.FILENAME, "- [ ] Initial Task").virtualFile

    assertEquals(1, service.loadTask().size)

    // Simulasikan edit file (memicu VfsListener)
    WriteCommandAction.runWriteCommandAction(project) {
      VfsUtil.saveText(file, "- [ ] Task 1\n- [ ] Task 2")
    }

    // VfsListener seharusnya memanggil markCacheDirty()
    val tasks = service.loadTask()
    assertEquals("VfsListener harusnya sudah memicu invalidasi cache", 2, tasks.size)
  }

  fun testManualRefreshForcesReload() {
    val file = myFixture.addFileToProject(TodosoConstants.FILENAME, "- [ ] Task A").virtualFile
    val panel = TodosoMainPanel(project)

    assertEquals(1, service.loadTask().size)

    // Kita modifikasi file tanpa memicu event VFS jika memungkinkan,
    // tapi di test environment ini sulit. Kita cukup verifikasi bahwa
    // refreshTasks() memanggil markCacheDirty() secara tidak langsung
    // dengan memastikan data terbaru terbaca.

    WriteCommandAction.runWriteCommandAction(project) {
      VfsUtil.saveText(file, "- [ ] Task A\n- [ ] Task B")
    }

    panel.refreshTasks()

    assertEquals(2, service.loadTask().size)
  }
}
