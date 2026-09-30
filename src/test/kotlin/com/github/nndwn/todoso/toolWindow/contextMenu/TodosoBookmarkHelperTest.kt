package com.github.nndwn.todoso.toolWindow.contextMenu

import com.github.nndwn.todoso.domain.model.Metadata
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TodosoBookmarkHelperTest : BasePlatformTestCase() {

  private fun createTaskWithNote(note: String): TodoTask {
    return TodoTask(
      id = "test1",
      isPersistentId = true,
      rawText = "- [ ] Test task // $note",
      description = "Test task",
      status = TaskStatus.TODO,
      priority = Priority.NONE,
      tags = emptyList(),
      lineNumber = 1,
      metadata = Metadata(notes = note),
    )
  }

  fun testGetAttachedBookmarkString() {
    val taskWithBookmark = createTaskWithNote("Review code 🔖 src/main/Main.kt:25")
    val bookmarkStr = TodosoBookmarkHelper.getAttachedBookmarkString(taskWithBookmark)
    assertEquals("src/main/Main.kt:25", bookmarkStr)

    val taskWithoutBookmark = createTaskWithNote("Review code without bookmark")
    assertNull(TodosoBookmarkHelper.getAttachedBookmarkString(taskWithoutBookmark))
  }

  fun testGetAttachedBookmarkStringWithMultipleNotes() {
    val task = createTaskWithNote("🔖 src/App.kt:100 additional notes here")
    assertEquals("src/App.kt:100", TodosoBookmarkHelper.getAttachedBookmarkString(task))
  }

  fun testIsAttachedBookmarkValidWhenNoBookmarksExist() {
    val task = createTaskWithNote("🔖 src/App.kt:100")
    assertFalse(TodosoBookmarkHelper.isAttachedBookmarkValid(task, project))
  }

  fun testNoteRegexReplacement() {
    val note = "My note 🔖 src/App.kt:100 extra info"
    val regex = Regex("""🔖\s*([^\s]+)""")
    val cleaned = note.replace(regex, "").trim().replace(Regex("""\s+"""), " ")
    assertEquals("My note extra info", cleaned)
  }

  fun testGetNotesWithoutBookmark() {
    val taskPureBookmark = createTaskWithNote("🔖 src/App.kt:100")
    assertEquals("", TodosoBookmarkHelper.getNotesWithoutBookmark(taskPureBookmark))

    val taskWithExtraNotes = createTaskWithNote("Review this code 🔖 src/App.kt:100")
    assertEquals("Review this code", TodosoBookmarkHelper.getNotesWithoutBookmark(taskWithExtraNotes))
  }
}
