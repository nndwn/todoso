package com.github.nndwn.todoso

import com.github.nndwn.todoso.services.MyProjectService
import com.github.nndwn.todoso.services.MyProjectSettingsService
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MyProjectServiceTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    val settings = MyProjectSettingsService.getInstance(project)
    settings.state.todoFileName = "todo.md"
  }

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

    tasks
      .find { it.status == MyProjectService.TaskStatus.DOING }
      ?.let {
        assertEquals("Doing task #ui", it.description)
        assertEquals("2026-09-01 08:30", it.dates["🛫"])
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

    assertEquals(1, tasks.size)
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

    val tagCounts = service.getTagCounts()
    assertTrue(tagCounts.containsKey("v1.2.3"))
    assertTrue(tagCounts.containsKey("feature-login"))
    assertTrue(tagCounts.containsKey("bug_fix"))
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

    tasks[0].let {
      assertEquals("Baca artikel tentang icon 🔺 #ui", it.description)
      assertEquals(MyProjectService.Priority.HIGHEST, it.priority)
    }
  }

  fun testStatusTransitionAndDuration() {
    val content =
      """
      - [/] Working task 🛫 2026-09-01 08:00
      """
        .trimIndent()

    val fileName = "transition_test.md"
    myFixture.addFileToProject(fileName, content)
    MyProjectSettingsService.getInstance(project).state.todoFileName = fileName

    val service = project.service<MyProjectService>()

    val task = service.getTodoTasks()[0]
    service.updateTaskStatus(task, MyProjectService.TaskStatus.TODO)

    val updatedTask = service.getTodoTasks()[0]
    assertEquals(MyProjectService.TaskStatus.TODO, updatedTask.status)
    assertFalse(updatedTask.dates.containsKey("🛫"))

    val doneTaskText = "- [x] Finished task 🛫 2026-09-01 08:00 ✅ 2026-09-01 10:30"
    val doneTask =
      MyProjectService.TodoTask(
        id = "abc123",
        isPersistentId = true,
        rawText = doneTaskText,
        description = "Finished task",
        status = MyProjectService.TaskStatus.DONE,
        priority = MyProjectService.Priority.NONE,
        tags = emptyList(),
        dates = mapOf("🛫" to "2026-09-01 08:00", "✅" to "2026-09-01 10:30"),
        lineNumber = 0,
      )

    val duration = service.calculateDuration(doneTask)
    assertEquals("2h 30m", duration)
  }

  fun testUpdatePriority() {
    val content = "- [ ] Task without priority"
    myFixture.addFileToProject("todo.md", content)
    val service = project.service<MyProjectService>()

    val task = service.getTodoTasks()[0]
    service.updateTaskPriority(task, MyProjectService.Priority.HIGHEST)

    val updatedTask = service.getTodoTasks()[0]
    assertEquals(MyProjectService.Priority.HIGHEST, updatedTask.priority)
    assertTrue(updatedTask.rawText.contains("🔺"))
    assertEquals("Task without priority", updatedTask.description)

    service.updateTaskPriority(updatedTask, MyProjectService.Priority.LOW)
    val updatedTask2 = service.getTodoTasks()[0]
    assertEquals(MyProjectService.Priority.LOW, updatedTask2.priority)
    assertTrue(updatedTask2.rawText.contains("🔽"))
  }

  fun testEditDoneTaskPreservesMetadata() {
    val content = "- [x] ⏫ Done task 🛫 2026-09-01 08:00 ✅ 2026-09-01 10:30 #old"
    myFixture.addFileToProject("todo.md", content)
    val service = project.service<MyProjectService>()

    val task = service.getTodoTasks()[0]
    service.editTask(task, "Updated task description #new")

    val updatedTask = service.getTodoTasks()[0]
    assertEquals(MyProjectService.TaskStatus.DONE, updatedTask.status)
    assertEquals(MyProjectService.Priority.HIGH, updatedTask.priority)
    assertEquals("Updated task description #new", updatedTask.description)
    assertEquals("2026-09-01 08:00", updatedTask.dates["🛫"])
    assertEquals("2026-09-01 10:30", updatedTask.dates["✅"])
    assertEquals(listOf("new"), updatedTask.tags)
    assertTrue(updatedTask.rawText.contains("✅ 2026-09-01 10:30"))
  }

  fun testEditCancelledTaskPreservesMetadata() {
    val content = "- [-] ⏫ Cancelled task ❌ 2026-09-01 12:00 // noted: too busy"
    myFixture.addFileToProject("todo.md", content)
    val service = project.service<MyProjectService>()

    val task = service.getTodoTasks()[0]
    service.editTask(task, "Cancelled but edited task #update")

    val updatedTask = service.getTodoTasks()[0]
    assertEquals(MyProjectService.TaskStatus.CANCELLED, updatedTask.status)
    assertEquals("Cancelled but edited task #update", updatedTask.description)
    assertEquals("2026-09-01 12:00", updatedTask.dates["❌"])
    assertEquals("noted: too busy", updatedTask.dates["//"])
    assertTrue(updatedTask.rawText.contains("❌ 2026-09-01 12:00"))
    assertTrue(updatedTask.rawText.contains("// noted: too busy"))
  }

  fun testUpdatePriorityDoneTaskPreservesMetadata() {
    val content = "- [x] ⏫ Done task 🛫 2026-09-01 08:00 ✅ 2026-09-01 10:30"
    myFixture.addFileToProject("todo.md", content)
    val service = project.service<MyProjectService>()

    val task = service.getTodoTasks()[0]
    service.updateTaskPriority(task, MyProjectService.Priority.LOW)

    val updatedTask = service.getTodoTasks()[0]
    assertEquals(MyProjectService.TaskStatus.DONE, updatedTask.status)
    assertEquals(MyProjectService.Priority.LOW, updatedTask.priority)
    assertEquals("2026-09-01 08:00", updatedTask.dates["🛫"])
    assertEquals("2026-09-01 10:30", updatedTask.dates["✅"])
    assertTrue(updatedTask.rawText.contains("✅ 2026-09-01 10:30"))
  }

  fun testTaskIdParsingAndGeneration() {
    val content =
      """
      - [ ] Task with existing ID 🆔 id123
      - [ ] Task without ID
      - [ ] Task with duplicate ID 🆔 id123
      """
        .trimIndent()

    myFixture.addFileToProject("todo.md", content)
    val service = project.service<MyProjectService>()
    val tasks = service.getTodoTasks()

    assertEquals(3, tasks.size)
    assertEquals("id123", tasks[0].id)
    assertNotNull(tasks[1].id)
    assertEquals(6, tasks[1].id.length)
    assertNotNull(tasks[2].id)
    assertFalse(tasks[2].id == "id123")
  }

  fun testTaskIdPersistenceOnStatusChange() {
    val content = "- [ ] Task for persistence"
    myFixture.addFileToProject("todo.md", content)
    val service = project.service<MyProjectService>()

    val task = service.getTodoTasks()[0]
    val originalId = task.id

    service.updateTaskStatus(task, MyProjectService.TaskStatus.DOING)

    val updatedTask = service.getTodoTasks()[0]
    assertEquals(originalId, updatedTask.id)
    assertTrue(updatedTask.rawText.contains("🆔 $originalId"))
  }

  fun testTaskIdPersistenceOnEdit() {
    val content = "- [ ] Task to edit 🆔 myid77"
    myFixture.addFileToProject("todo.md", content)
    val service = project.service<MyProjectService>()

    val task = service.getTodoTasks()[0]
    service.editTask(task, "Edited description")

    val updatedTask = service.getTodoTasks()[0]
    assertEquals("myid77", updatedTask.id)
    assertTrue(updatedTask.rawText.contains("🆔 myid77"))
    assertEquals("Edited description", updatedTask.description)
  }

  fun testGetRecentVersions() {
    val content =
      """
      - [ ] Task 1 #v1.0.1
      - [ ] Task 2 #v1.0.2
      - [ ] Task 3 #v1.0.3
      - [ ] Task 4 #v1.0.4
      - [ ] Task 5 #v1.0.1
      """
        .trimIndent()
    myFixture.addFileToProject("todo.md", content)
    val service = project.service<MyProjectService>()

    val versions = service.getRecentVersions(3)
    assertEquals(listOf("#v1.0.1", "#v1.0.2", "#v1.0.3"), versions)
  }

  fun testToggleTaskTagExclusivity() {
    val content = "- [ ] Task with issue #issue 🆔 testid"
    myFixture.addFileToProject("todo.md", content)
    val service = project.service<MyProjectService>()

    val task = service.getTodoTasks()[0]
    service.toggleTaskTag(task, "#feature", listOf("#issue"))

    val updatedTask = service.getTodoTasks()[0]
    assertTrue(updatedTask.tags.contains("feature"))
    assertFalse(updatedTask.tags.contains("issue"))
    assertTrue(updatedTask.rawText.contains("#feature"))
    assertFalse(updatedTask.rawText.contains("#issue"))

    // Toggle off
    service.toggleTaskTag(updatedTask, "#feature")
    val updatedTask2 = service.getTodoTasks()[0]
    assertFalse(updatedTask2.tags.contains("feature"))
    assertFalse(updatedTask2.rawText.contains("#feature"))
  }
}
