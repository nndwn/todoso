<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Todoso Changelog

## [Unreleased]
### Added
- **Automatic File Creation**: Now automatically creates `todo.md` in the project root if it doesn't exist when adding your first task.
- **Visibility-based Refresh**: Improved performance by only refreshing the task list when the plugin panel is actually visible/opened.
- **Enhanced UI Esthetics**: Tag Cloud chips now feature a modern rounded "pill" design.
- **Visual List Markers**: Added a dot icon for tasks with **Todo** status to clearly distinguish them as list items.

### Improved
- **Smart Input Validation**: The "New Task" button is now automatically disabled for empty inputs or inputs containing only Markdown prefixes (e.g., `- [ ]`).
- **Optimized Edit Mode**: The "Update" button now stays disabled until actual changes are made to the task text.
- **Parsing Flexibility**: Added support for `- []` (status brackets without a space) as a valid Todo status.

### Fixed
- **UI Focus Fix**: Resolved an issue where buttons remained in a "hovered" state after being clicked.
- **Robust Parsing**: Fixed a potential crash when encountering unconventional status bracket formats.

## [1.0.3] - 2026-09-04
- initial release

## [1.0.2] - 2026-09-03
- change logo icon

## [1.0.1] - 2026-09-01
### Added
- **todo.md Integration**: Automatically reads tasks from the project root.
- **Dynamic Tag Cloud**: Horizontal chips area above footer with task counts and filtering.
- **Interactive Footer**: Rounded input area with specialized buttons for New Task and Canceled Task.
- **Priority & Status Filters**: Persistent filters for prioritizing and focusing on tasks.
- **Random Task**: Randomly pick a Todo task to start working on.
- **Edit & Delete**: Full support for editing descriptions/tags and deleting tasks with confirmation.
- **I18n Support**: Centralized all strings into resource bundles for better maintainability.
