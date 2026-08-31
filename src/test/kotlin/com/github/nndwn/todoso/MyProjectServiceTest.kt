package com.github.nndwn.todoso

import com.github.nndwn.todoso.services.MyProjectService
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MyProjectServiceTest : BasePlatformTestCase() {

  fun testObsidianTaskParsing() {
    val content =
      """
      - [/] ⏫ Doing task #ui 🛫 2026-09-01 08:30
      - [ ] 🔼 Todo task #dev 📅 2026-09-10 14:00
      - [x] 🔽 Done task #bug ✅ 2026-08-31 18:45:30
      - [ ] Simple task
      - [ ] Task with URL https://google.com // note
      - [-] 🔺 Cancelled task
      """
        .trimIndent()

    myFixture.addFileToProject("todo.md", content)
    val service = project.service<MyProjectService>()
    val tasks = service.getTodoTasks()

    assertEquals(6, tasks.size)

    // Verify Doing Task with Time
    tasks
      .find { it.status == MyProjectService.TaskStatus.DOING }
      ?.let {
        assertEquals("Doing task #ui", it.description)
        assertEquals("2026-09-01 08:30", it.dates["🛫"])
      }

    // Verify Due Date with Time
    tasks
      .find { it.description.contains("Todo task") }
      ?.let {
        assertEquals("2026-09-10 14:00", it.dates["📅"])
      }

    // Verify Done Date with Full Time (Seconds)
    tasks
      .find { it.status == MyProjectService.TaskStatus.DONE }
      ?.let {
        assertEquals("2026-08-31 18:45:30", it.dates["✅"])
      }
  }

  fun testLegacyParsing() {
    val content =
      """
      - [ ] [H] Legacy task #old // info here
      """
        .trimIndent()

    myFixture.addFileToProject("todo.md", content)
    val service = project.service<MyProjectService>()
    val tasks = service.getTodoTasks()

    tasks[0].let {
      assertEquals("Legacy task #old", it.description)
      assertEquals(MyProjectService.Priority.HIGH, it.priority)
      assertEquals("info here", it.dates["//"])
    }
  }

  fun testTagParsing() {
    val content =
      """
      - [ ] Task with tags #v1.2.3 #feature-login #bug_fix
      """
        .trimIndent()

    myFixture.addFileToProject("todo.md", content)
    val service = project.service<MyProjectService>()
    val tasks = service.getTodoTasks()

    assertEquals(1, tasks.size)
    assertEquals(listOf("v1.2.3", "feature-login", "bug_fix"), tasks[0].tags)

    val allTags = service.getAllTags()
    assertTrue(allTags.contains("v1.2.3"))
    assertTrue(allTags.contains("feature-login"))
    assertTrue(allTags.contains("bug_fix"))
  }

  fun testDuplicateEmojiSafety() {
    val content =
      """
      - [ ] 🔺 Baca artikel tentang icon 🔺 #ui
      - [ ] ⏫ Prioritas ⏫ di dalam teks ⏫
      """
        .trimIndent()

    myFixture.addFileToProject("todo.md", content)
    val service = project.service<MyProjectService>()
    val tasks = service.getTodoTasks()

    // Baris 1: 🔺 pertama harus hilang (priority), 🔺 kedua harus TETAP ada
    tasks[0].let {
      assertEquals("Baca artikel tentang icon 🔺 #ui", it.description)
      assertEquals(MyProjectService.Priority.HIGHEST, it.priority)
    }

    // Baris 2: ⏫ pertama hilang, sisa ⏫ tetap ada
    tasks[1].let {
      assertEquals("Prioritas ⏫ di dalam teks ⏫", it.description)
      assertEquals(MyProjectService.Priority.HIGH, it.priority)
    }
  }
}
