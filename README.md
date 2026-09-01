# Todoso - IntelliJ Todo Manager

An IntelliJ plugin to manage your todo list directly from a `todo.md` file in the project root, supporting standard formats from Obsidian and Notion.

## 🚀 Key Features

*   **todo.md Integration**: Reads tasks directly from a Markdown file in the project root.
*   **Interactive Tool Window**: A clean, selectable task list.
*   **Comprehensive Task Status**:
    *   `[ ]` : Todo (Not started).
    *   `[/]` : **Doing** (In progress) - Bold green text, automatically moves to the top.
    *   `[x]` : Done (Completed) - Strikethrough gray text.
    *   `[-]` or `❌` : Cancelled.
*   **Dual Priority System**:
    *   **Obsidian Style**: `🔺` (Highest), `⏫` (High), `🔼` (Medium), `🔽` (Low), `⏬` (Lowest).
    *   **Legacy Style**: `[HH]` (Highest), `[H]` (High), `[M]` (Medium), `[L]` (Low), `[LL]` (Lowest).
*   **Dynamic Hashtags (`#tag`)**: Automatic tag detection (e.g., `#ui`, `#bug`, `#v1.3.1`) with Cyan color highlighting.
*   **Time Management & Metadata**:
    *   Supports Obsidian date emojis with optional time: `🛫` (Start), `📅` (Due), `⏳` (Scheduled), `✅` (Completed), `➕` (Created).
    *   Format supported: `YYYY-MM-DD` or `YYYY-MM-DD HH:mm` or `YYYY-MM-DD HH:mm:ss`.
    *   Supports additional metadata using a space and ` // ` at the end of the line.
*   **Persistent Settings**:
    *   Toggle **Visual Mode** via the toolbar to show/hide priority colors and emojis (helps you focus!).
    *   Settings are saved per project in `.idea/TodosoSettings.xml`.
*   **Smart UI**:
    *   Hides marker icons/dates from the main list for a clean look, but shows them fully in the **Tooltip**.
    *   Automatic sorting: **Doing > Todo (by Priority) > Done**.
    *   Integrated toolbar for instant task list **Refresh**.

## 📝 Writing Rules for `todo.md`

Write your tasks in the `todo.md` file using the following format:
`- [status] [Priority] Task Description #tag1 #tag2 [Date Emoji] // Metadata`

### Usage Example:
```markdown
- [/] ⏫ Fix SlideUpPanel layout bug #ui #bug 🛫 2026-09-01
- [ ] 🔺 Migration to Navigation3 #migration ⏳ 2026-09-05 14:00
- [x] 🔼 Finished cleaning up icons #design ✅ 2026-08-30 18:00
- [ ] [HH] Urgent legacy task #refactor
- [ ] [LL] Very low priority legacy task
- [ ] Regular task with metadata // additional notes here
```

## 🛠️ Development & Testing

To run unit tests and ensure all parsing functions work correctly (especially for validation in GitHub Actions), use the following command:

```bash
./gradlew test
```

This command will validate:
1.  Parsing accuracy of various task statuses.
2.  Emoji detection safety (ensuring emojis in the middle of sentences are not removed).
3.  Safe separation of time metadata and URLs.
4.  Correctness of task sorting logic.
