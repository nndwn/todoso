# Todoso - IntelliJ Todo Manager

An IntelliJ plugin to manage your todo list directly from a `todo.md` file in the project root. Designed for simplicity, it supports modern standards inspired by **Obsidian** and **Notion**.

I created Todoso because I wanted a way to manage tasks without leaving my IDE. Instead of switching to external apps like Notion or Sticky Notes, you can keep your focus where you code. It's built for developers who appreciate clean Markdown and efficient workflows.


##  Key Features

### File-Based Workflow
*   **Automatic Integration**: Reads from `todo.md` at your project root. If the file doesn't exist, it's created automatically when you add your first task.
*   **Customizable**: You can change the filename and path in `.idea/TodosoSettings.xml`.
    ```xml
    <component name="com.github.nndwn.todoso.services.MyProjectSettingsService">
        <option name="todoFileName" value="todo.md"/>
    </component>
    ```

> [!IMPORTANT]
> **Personalized Tasks**: Since `todo.md` is stored in the project root, it may cause conflicts in shared repositories. To keep your tasks private and avoid merge issues, we highly recommend adding `todo.md` (or your custom filename) to your **`.gitignore`** file.

### Smart Tagging System
Todoso implements a robust tagging system inspired by Obsidian:
*   **Boundary Awareness**: Tags must be preceded by a space or start at the beginning of a line (e.g., `#tag` is valid, but `word#tag` is not).
*   **Hierarchy Support**: Use `/` to create nested tags (e.g., `#project/feature`).
*   **Technical Tags**: Supports special symbols like `#C#`.
*   **Clean Parsing**: Trailing punctuation (like `.`, `,`, `!`, `?`) is automatically excluded from the tag.
*   **Tag Cloud**: A dynamic, scrollable tag cloud allows you to filter tasks instantly.
*   **Recommended Tags**: `#issue` and `#feature` are suggested as default tags for consistency.

### Task Management
*   **Status Tracking**:
    *   `[ ]` : **Todo** (Pending)
    *   `[/]` : **Doing** (In Progress) — Highlights in green and moves to the top.
    *   `[x]` : **Done** (Completed) — Strikethrough and grayed out.
    *   `[-]` or `❌` : **Cancelled** (Requires a noted).
*   **Dual Priority Support**:
    *   **Emoji (Obsidian)**: `🔺`, `⏫`, `🔼`, `🔽`, `⏬`
    *   **Text (Legacy)**: `[HH]`, `[H]`, `[M]`, `[L]`, `[LL]`
*   **Auto-Sorting**: Tasks are automatically ordered: **Doing > Todo (by Priority) > Done**.
*   **Business Logic & Safety**: 
    *   **Status Transitions**: 
        *   `TODO` -> `DOING`: Automatically adds Start Date (`🛫`).
        *   `DOING` -> `DONE`: Automatically adds Completion Date (`✅`).
        *   `ANY` -> `CANCELLED`: Requires a **mandatory noted**, adds `❌`, and clears working dates to preserve history.
        *   `DONE` -> `CANCELLED`: **Blocked**. A completed task cannot be logically cancelled.
    *   **Edit Restrictions**: 
        *   **Done Tasks**: Can be edited (to add tags), but priority is locked as urgency is no longer relevant.
        *   **Cancelled Tasks**: Strictly read-only to prevent accidental history modification.
    *   **Deletion**: Requires manual confirmation to prevent accidental loss of data.
*   **Quick Labels & Dynamic Versions**: 
    *   Easily toggle essential tags via the context menu:
        *   **`#feature`**: Used for new ideas, enhancements, or planned improvements.
        *   **`#issue`**: Used for bugs, code debt, or visual glitches.
    *   **Exclusivity**: Adding `#feature` automatically removes `#issue` (and vice versa) to maintain a clear distinction between new work and fixes.
    *   **Smart Versioning**: The plugin scans your `todo.md` for any tags starting with **`#v`** (e.g., `#v1.0.5`). It automatically picks the top 3 unique versions and offers them as quick-select options in the menu.

### Interactive Tool Window
*   **Seamless Sync**: Auto-refreshes when you open the tool window or edit the file.
*   **Navigation**: Double-click any task to jump directly to its line in `todo.md`.
*   **Gamification**: Use the **Random Task** feature to pick your next item and beat procrastination.
*   **Visual Mode**: Toggle priority colors and emojis via the toolbar for a cleaner look.
*   **Duration Tracking**: Automatically calculates how long a task took once marked as Done.
*   **Copy Task**: Right-click any task and select "Copy Task" to copy the full Markdown line (including ID and tags) to your clipboard—perfect for sharing context with AI assistants.

### AI-Ready Context
Todoso is designed to bridge the gap between your intent and AI assistance. By maintaining a structured `todo.md` file at the project root, you provide AI coding assistants with a clear map of your goals.
*   **Unique Task IDs**: Every task is assigned a unique 6-character ID (`🆔`). This allows AI to reference specific tasks accurately, even if their descriptions change.
*   **Intent Mapping**: Helps AI understand the "why" and "when" behind your code, not just the "what".
*   **Roadmap Clarity**: AI can scan your roadmap to provide suggestions that align with your current `#feature` or `#issue` focus.
*   **Seamless Debugging**: Structured tags help AI assistants quickly identify and relate tasks to your codebase context.

### Task Metadata
Todoso uses the `🆔` emoji to store unique identifiers for each task, following the Obsidian Tasks convention.
*   **Automatic Management**: IDs are generated automatically by the plugin. You don't need to type them manually.
*   **Lazy Persistence**: For existing tasks without IDs, an ID is created **in-memory** first. It is only written permanently to your `todo.md` file the first time you interact with that task (e.g., changing status, editing text, or updating priority).
*   **New Tasks**: Tasks created via the plugin's "New Task" input will have an ID assigned and saved immediately.
*   **Safe for Collaboration**: Duplicate IDs (e.g., from copy-pasting lines in the Markdown file) are automatically detected and resolved by assigning a new unique ID to the duplicate.

## Writing Rules

You can write directly in `todo.md` or use the plugin's UI. The format is:
`- [status] [Priority] Task Description #tag1 #tag2 [Date Emoji] // Metadata`

### Time & Metadata
*   **Date Emojis**: `🛫` (Start), `📅` (Due), `⏳` (Scheduled), `✅` (Completed), `➕` (Created).
*   **Metadata**: Add notes at the end of a line using ` // your notes`.

### Usage Example:
```markdown
- [/] ⏫ Fix SlideUpPanel layout bug #ui #bug 🛫 2026-09-01
- [ ] 🔺 Migration to Navigation3 #migration ⏳ 2026-09-05 14:00
- [x] 🔼 Finished cleaning up icons #design ✅ 2026-08-30 18:00
- [ ] [HH] Urgent legacy task #refactor
- [ ] Regular task with metadata // additional notes here
```

---
*Developed with focus and UX in mind. If you have suggestions, feel free to open an issue!*
