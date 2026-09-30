package com.github.nndwn.todoso.toolWindow.contextMenu

import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.services.TodosoService
import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

object TodosoBookmarkHelper {
  private val BOOKMARK_REGEX = Regex("""🔖\s*([^\s]+)""")

  fun getProjectBookmarks(project: Project): List<Bookmark> {
    val manager = BookmarksManager.getInstance(project) ?: return emptyList()
    return manager.bookmarks.toList()
  }

  fun getAttachedBookmarkString(task: TodoTask?): String? {
    if (task == null) return null
    val notes = task.metadata.notes
    val match = BOOKMARK_REGEX.find(notes) ?: return null
    return match.groupValues[1].trim()
  }

  fun getNotesWithoutBookmark(task: TodoTask?): String {
    if (task == null) return ""
    val notes = task.metadata.notes
    return BOOKMARK_REGEX.replace(notes, "").trim()
  }

  fun findMatchingBookmark(task: TodoTask?, project: Project): LineBookmark? {
    val attached = getAttachedBookmarkString(task) ?: return null
    val parts = attached.split(":")
    if (parts.isEmpty()) return null
    val targetPath = parts[0]
    val targetLine = if (parts.size > 1) parts[1].toIntOrNull() else null

    val bookmarks = getProjectBookmarks(project)
    val lineBookmarks = bookmarks.mapNotNull { it as? LineBookmark }

    // 1. Exact match (file path + line number)
    val exactMatch = lineBookmarks.find { lb ->
      val relPath = getRelativePath(project, lb.file)
      val line = lb.line + 1
      relPath == targetPath && line == targetLine
    }
    if (exactMatch != null) return exactMatch

    // 2. Fallback by file path (if lines moved in editor)
    return lineBookmarks.find { lb ->
      val relPath = getRelativePath(project, lb.file)
      relPath == targetPath
    }
  }

  fun isAttachedBookmarkValid(task: TodoTask?, project: Project): Boolean {
    return findMatchingBookmark(task, project) != null
  }

  fun attachBookmark(task: TodoTask, bookmark: Bookmark, project: Project, service: TodosoService) {
    val lineBookmark = bookmark as? LineBookmark
    val file = lineBookmark?.file
    val line = lineBookmark?.line?.plus(1) ?: 1
    if (file == null) return

    val relativePath = getRelativePath(project, file)
    val bookmarkToken = "🔖 $relativePath:$line"

    val currentNotes = task.metadata.notes
    val newNotes =
      if (BOOKMARK_REGEX.containsMatchIn(currentNotes)) {
        currentNotes.replace(BOOKMARK_REGEX, bookmarkToken).trim()
      } else {
        if (currentNotes.isBlank()) bookmarkToken else "$currentNotes $bookmarkToken"
      }

    service.updateTaskNote(task, newNotes)
  }

  fun removeBookmarkFromNote(task: TodoTask, service: TodosoService) {
    val currentNotes = task.metadata.notes
    if (BOOKMARK_REGEX.containsMatchIn(currentNotes)) {
      val newNotes = currentNotes.replace(BOOKMARK_REGEX, "").trim()
      service.updateTaskNote(task, newNotes)
    }
  }

  fun navigateToBookmark(task: TodoTask, project: Project, service: TodosoService? = null): Boolean {
    val matchingBookmark = findMatchingBookmark(task, project)
    if (matchingBookmark != null) {
      val file = matchingBookmark.file
      val line = matchingBookmark.line
      val descriptor = OpenFileDescriptor(project, file, line, 0)
      if (descriptor.canNavigate()) {
        descriptor.navigate(true)

        // If line number shifted in editor, auto-update the task note to stay synced!
        if (service != null) {
          val relPath = getRelativePath(project, file)
          val currentLineDisplay = line + 1
          val attachedStr = getAttachedBookmarkString(task)
          if (attachedStr != "$relPath:$currentLineDisplay") {
            attachBookmark(task, matchingBookmark, project, service)
          }
        }
        return true
      }
    }

    val bookmarkStr = getAttachedBookmarkString(task) ?: return false
    val parts = bookmarkStr.split(":")
    if (parts.isEmpty()) return false

    val pathPart = parts[0]
    val linePart = if (parts.size > 1) parts[1].toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0 else 0

    val file = resolveFile(project, pathPart) ?: return false
    val descriptor = OpenFileDescriptor(project, file, linePart, 0)
    if (descriptor.canNavigate()) {
      descriptor.navigate(true)
      return true
    }
    return false
  }

  fun getRelativePath(project: Project, file: VirtualFile): String {
    val basePath = project.basePath
    if (basePath != null) {
      val filePath = file.path
      if (filePath.startsWith(basePath)) {
        return filePath.substring(basePath.length).removePrefix("/").removePrefix("\\")
      }
    }
    return file.name
  }

  private fun resolveFile(project: Project, pathPart: String): VirtualFile? {
    val projectDir = project.guessProjectDir()
    val relativeVf = projectDir?.findFileByRelativePath(pathPart)
    if (relativeVf != null && relativeVf.exists()) {
      return relativeVf
    }

    val basePath = project.basePath
    if (basePath != null) {
      val absoluteFile = File(basePath, pathPart)
      if (absoluteFile.exists()) {
        val vf = LocalFileSystem.getInstance().findFileByIoFile(absoluteFile)
        if (vf != null) return vf
      }
    }
    val directFile = File(pathPart)
    if (directFile.exists()) {
      return LocalFileSystem.getInstance().findFileByIoFile(directFile)
    }
    return null
  }
}
