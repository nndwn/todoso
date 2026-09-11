# Todoso - IntelliJ Todo Manager

An IntelliJ plugin to manage your todo list directly from a `todo.md` file in the project root. Designed for simplicity, it supports modern standards inspired by **Obsidian** and **Notion**.

I created Todoso because I wanted a way to manage tasks without leaving my IDE. Instead of switching to external apps like Notion or Sticky Notes, you can keep your focus where you code. It's built for developers who appreciate clean Markdown and efficient workflows.

## Usage
just type task in field and click right select priority and select tags or if you want fast
```txt
[H] task description #feature #development #v0.0.1 
```

I’m not good at typing in English but this AI agent typing is more pathetic than me



#### File-Based Workflow
* **Automatic Integration**: Reads from `todo.md` at your project root by default.
* **Flexible Casing**: Automatically detects `todo.md`, `TODO.md`, `Todo.md`, or any casing variation without issues.
* **Absolute & Relative Path Support**: Select any Markdown file inside your project directory (relative path) or connect an external file from your personal **Obsidian Vault** anywhere on your system (absolute path).
* **Customizable Settings**: Select your file interactively via the Toolbar search icon or specify its path in `.idea/TodosoSettings.xml`:
    ```xml
    <component name="com.github.nndwn.todoso.services.TodosoSettingsService">
        <!-- Relative path inside project OR absolute path to external file -->
        <option name="todoFilePath" value="docs/todo.md"/>
    </component>
    ```
  
> [!IMPORTANT]
> **Personalized Tasks**: Since `todo.md` is stored in the project root, it may cause conflicts in shared repositories. To keep your tasks private and avoid merge issues. we highly recommend adding todo.md (or your custom path) to your `.gitignore` file.

#### Data Synchronization & Performance
To ensure a seamless experience when working with external Markdown editors (like Obsidian) and large task lists, Todoso uses a specialized synchronization engine:
* **Why Caching?** Parsing Markdown with regex is resource-intensive. We cache tasks in memory so that switching tags or sorting feels instant, without laggy reparsing on every click.
* **Smart Component Re-use**: Unlike standard lists that redraw everything, Todoso re-uses existing UI components when data updates. This prevents screen flickering and significantly reduces CPU usage during background file syncs.
* **Why Path Awareness?** Switching between different todo files or clearing settings triggers an immediate cache invalidation. This ensures you never see "ghost tasks" from a previously selected file.
* **Why a Refresh Button?** While we auto-sync via IntelliJ's VFS, external file changes can sometimes lag. The Refresh button acts as a "hard reset" that bypasses the cache to read directly from the disk.

#### Modern Component-Based UI
Todoso moves away from traditional, rigid list views to a modern, dynamic component architecture inspired by modern productivity tools:
* **Multi-line Text Wrapping**: Descriptions and tags are no longer truncated. The layout intelligently recalculates row heights to wrap text naturally, ensuring your full task is always visible.
* **Interactive Hover Effects**: A subtle highlight follows your mouse, providing clear visual feedback on which task you are interacting with.
* **Smart Selection Logic**: Select tasks with a single click to perform actions (Delete/Move). Focus management is handled automatically, ensuring that clicking a task won't accidentally clear your current input draft.
* **Refined Metadata Tooltip**: Powered by IntelliJ's `HelpTooltip` API, hovering over a task reveals a structured, rounded overlay containing:
    * **Task ID**: Persistent unique identifier.
    * **Lifecycle Dates**: Created, Started, Due, and Completion timestamps.
    * **Execution Duration**: Real-time calculation of how long a task took to complete.
    * **Sanitized Notes**: Clean presentation of your manual notes and attachments.

#### Flexible Input Field Behavior
A smart single-input field that intelligently processes plain text, Markdown syntax, and dynamic suggestions:
*    **Modern Overlay UI**: Instead of standard IntelliJ popups, Todoso features a sleek, agent-like overlay that floats above the input field for a responsive and modern typing experience.
*    **Contextual Suggestions**:
        * **# Symbol (Tags & Task Search)**: 
            * Typing `#` triggers a categorized overlay showing your most popular tags.
            * **New User Experience**: If a project has no tags yet, Todoso suggests **Quick Tags** (`#feature`, `#issue`, `#production`, `#development`, `#urgent`) to help you get started.
            * **Deep Search**: Typing after `#` searches through *all* tags ever used in the project, not just the top 10.
            * Selecting a task from the suggestions instantly inserts its unique `🆔 ID` for easy cross-referencing.
*    **Input Styles**:
        * **Plain Text**: Type a description like `Update layout navbar`.
        * **Quick Syntax**: Use shortcodes like `[H] Fix bug #ui` to assign priority and tags instantly.
*    **Submission & Navigation**:
        * **Instant Submit**: Press **Enter** to instantly create or update a task.
        * **Line Breaks**: Use **Shift + Enter** if you need to add a manual line break within the input field. (Stored as `\n` literal in the file to maintain one-line-per-task integrity).
*    **Empty Checkbox Protection**: Prevents creation of blank tasks.
*    **Automatic Note Prefixing**: Note Mode injects the `//` prefix automatically.
*    **Insert File & Image (Attachment)**:
        * **Relative Path Mapping**: Automatically calculates the path relative to your **Project Root**. If `todo.md` is outside the project, it still prioritizes relative paths for files within the current project.
        * **Markdown Formatting**: 
            * **Images**: `![filename](path)` (Supports `jpg, png, gif, svg, webp`).
            * **Other Files**: `[filename](path)`.
        * **Smart Injection**: Appends the attachment to the **Note** section (`//`). It automatically adds the `//` separator if it's missing.
        * **In-List Preview (Tooltip)**: Displays the file location clearly as text in the task tooltip. Visual image rendering is disabled by design to prevent tooltip bloat.
        * **Integrity Check**: Adding an attachment alone does not enable the submit button; a task description is always required to prevent empty tasks.


####  Multi-Criteria Toolbar & Sorting Features
Todoso provides an interactive toolbar with dynamic view options and multi-criteria sorting to help you organize your tasks effortlessly:

* **File Selection**: Click the file search icon on the toolbar to choose any Markdown file directly from your project directory or your computer
*   **Dynamic Chain Sorting (Power Feature)**:
      * Unlike other apps with fixed sorting, Todoso uses a **selection-based hierarchy**. The order in which you enable sorting options determines the priority of the rules.
      * **How it works**:
          1. Enable **Status** → All tasks are grouped by their progress (Doing, Todo, etc.).
          2. Enable **Priority** (while Status is active) → Inside each status group, tasks are now sorted by urgency.
          3. Enable **Date** (third) → Tasks with the *same status* AND *same priority* will then be ordered by date.
      * **Pro Tip**: To change the hierarchy, simply click "Default" to clear the chain and re-enable them in your preferred order!
      * **RecommendedMe ** choose `Priority first and Status`.
*   **Persistent Sort State**: Your custom sorting chain is automatically saved and restored across IDE restarts.
*   **Visual Mode Toggle**: Toggle custom priority background colors and emoji highlights on demand for a clean list presentation.
*   **Random Task Picker**: Click the lightning action button to randomly select an available `TODO` task and mark it `DOING` to beat procrastination.
     
#### Strict Line Parsing Rules

To ensure reliable parsing and prevent false positives, Todoso enforces strict syntax rules when scanning your `todo.md` file:

1. **Valid Task Prefixes**:
    * A line is recognized as a valid task **only** if it begins with a dash (`-`), optionally preceded by indentation (spaces or tabs).
    * Allowed status markers inside brackets are strictly limited to:
        * `- [ ]` or `-[]` : **Todo**
        * `- [/]` : **Doing**
        * `- [x]` or `- [X]` : **Done**
        * `- [-]` : **Cancelled**

2. **Rejected Formats (Ignored Lines)**:
    * **Blockquotes (`> - [ ]`)**: Lines wrapped in Markdown blockquotes are treated as plain text quote references and will not be parsed as active tasks.
    * **Escaped Syntax (`\- [ ]`)**: Lines starting with a backslash escape character are explicitly ignored.
    * **Unknown Status Codes (`- [?]`, `- [a]`)**: Any brackets containing unrecognized symbols or arbitrary characters will be rejected.
    * **Embedded Brackets**: Bracket syntax appearing in the middle or end of a sentence (e.g., `Fix bug - [x] in module`) will be preserved as part of the task description text, not parsed as a status marker.


#### Strict Priority Parsing Rules

To deliver accurate priority detection and support both Obsidian standards and legacy code formats, Todoso applies strict rules when parsing task priorities:

1. **Strict Positioning (Prefix-Only)**:
   * Priority markers (either Obsidian emojis or bracketed text) **must appear immediately after the task status bracket** (e.g., `- [ ] 🔺` or `- [ ] [H]`).
   * Spacing between the status bracket and the priority marker is optional (e.g., `- [ ]🔺` and `- [ ] 🔺` are both valid).
   * Any priority emoji appearing in the middle or end of a sentence (e.g., `- [ ] Fix graph 🔺 bug`) will be treated as plain description text, not a priority token.

2. **First-Match Precedence**:
   * If a task line accidentally contains multiple priority emojis, only the **first priority emoji appearing right after the status** is captured. Subsequent priority emojis are ignored.

3. **Fallback for Unrecognized Bracket Codes**:
   * Standard supported codes: `[HH]`, `[H]`, `[M]`, `[L]`, `[LL]` (case-insensitive, padding space tolerant e.g., `[  h  ]`, `[HIGHEST]`).
   * If brackets contain an unrecognized code (e.g., `[URGENT]`, `[ABC]`), the parser will **not** fail or strip the text. It gracefully falls back to `Priority.NONE` and preserves `[URGENT]` as part of the normal task description

#### Strict Tag Parsing Rules

Todoso adheres strictly to the Obsidian tag standard with enhanced sanitization and edge-case safety:

1. **Word Boundary Awareness**:
   * Tags must start with a `#` preceded by a whitespace or a line boundary (e.g., `#feature`, `#project/ui`).
   * Embedded hash symbols in URLs (`https://site.com#readme`) or email addresses (`user#domain`) are automatically ignored.

2. **Hierarchical & Technical Tag Support**:
   * Supports nested tag hierarchy using slashes (e.g., `#project/feature/v1`).
   * Explicitly supports technical language tags such as `#C#` and `#F#`.

3. **No Pure Numeric Tags**:
   * Tags consisting purely of digits (e.g., `#123`) are rejected to avoid conflicts with issue numbers or ticket IDs.
   * Version tags containing numbers alongside letters or punctuation (e.g., `#v1.0.1`, `#v1`) remain fully valid.

4. **Automatic Trailing Punctuation Sanitization**:
   * Trailing punctuation attached to tags within sentences (such as `#issue,`, `#core.`, or `#v1.0.1!`) is cleanly stripped (`issue`, `core`, `v1.0.1`).

5. **Metadata Comment Isolation**:
   * Any tags located after the metadata comment separator `//` (e.g., `- [ ] Task #ui // review #note`) are ignored by the task tag parser and reserved for metadata notes.

6. **Mutual Exclusive Tag Groups (Automation)**:
   * To keep task categorization logical, certain tags are programmed to be mutually exclusive when applied via automated tools (like the context menu):
      * `#feature` ↔ `#issue`
      * `#development` ↔ `#production`
   * Applying one tag from these groups will automatically remove its "opposite" tag, preventing contradictory labels.

#### Unique Task ID & Persistence Rules

Todoso follows the Obsidian Tasks convention for unique task identification:

1. **Obsidian Compatibility**:
   * Reads task IDs prefixed with the `🆔` emoji (e.g., `🆔 8x2k1a`).
   * Supports flexible ID lengths (3–12 alphanumeric characters) to ensure seamless import of external Obsidian Markdown files.

2. **Collision Resolution**:
   * If duplicate IDs exist in `todo.md` (e.g., from manual copy-pasting), Todoso automatically resolves the conflict by generating a new temporary in-memory ID for the duplicate line.

3. **Lazy Persistence**:
   * Tasks lacking a physical `🆔` in `todo.md` are assigned a 6-character ID in-memory (`isPersistentId = false`).
   * Temporary IDs are only written permanently to `todo.md` upon the first user interaction (e.g., editing, toggling status, or changing priority).
     Todoso extracts standard Obsidian Tasks date emojis to track task lifecycles and completion duration:
4. **Line Drift Safety Net**:
   File mutations include an automatic ID fallback check (`findTaskIndex`) to prevent accidental line overwrites if external edits shift line positions before background VFS listeners trigger.

#### Date Metadata & Duration Tracking Rules

1. **Supported Date Emojis**:
   * 🛫 **Start Date** (`startDate`): Recorded when a task transitions to `DOING`.
   * 📅 **Due Date** (`dueDate`): Optional deadline date for the task.
   * ✅ **Completion Date** (`endDate`): Recorded when a task is marked `DONE`.
   * ❌ **Cancelled Date** (`cancelDate`): Recorded when a task is marked `CANCELLED`.
   * ➕ **Created Date** (`createdDate`): Optional task creation timestamp.

2. **Flexible DateTime Format**:
   * Supports full timestamps (`YYYY-MM-DD HH:mm`) and date-only formats (`YYYY-MM-DD`).
   * Date-only strings automatically fall back to `00:00` for time calculation safety.

3. **Smart Lifecycle Tracking (Emojis)**:
   Todoso automatically tracks every stage of a task's life using dynamic emojis:
   *   ➕ **Created**: Injected automatically when a task is first added.
   *   📝 **Edited**: Updated every time you modify the task description or its tags.
   *   🛫 **Started**: Recorded when a task transitions to the `DOING` state.
   *   ✅ **Completed**: Captured when marked as `DONE`, triggering duration calculation.
   *   ❌ **Cancelled**: Logged when a task is moved to the `CANCELLED` state.

4. **Execution Duration Calculation**:
   * Automatically calculates execution duration between Start Date (🛫) and Completion Date (✅) upon completion (e.g., `1h 45m` or `30m`).

#### Strict Content Integrity & Validation
To keep your `todo.md` clean and professional, Todoso enforces a **Strict Validator** across both the UI and Service layers:
*   **No Ghost Tasks**: You cannot create or save a task that only contains metadata (e.g., just tags, dates, or priority). A real description is always required.
*   **Intelligent Stripping**: During validation, the system "peels off" all status brackets, priority markers, tags, and date emojis to ensure that actual human-readable content is present before enabling the submit button.


#### Metadata Comment Isolation (//)
1.   Any text placed after the metadata comment separator `//` is isolated as `metadata.notes` (e.g.,` - [ ] Fix UI #ui // check details`).
2.   URL protocol slashes (such as `http://` or `https://`) are protected and will never be falsely parsed as comment separators


---
*Developed with focus and UX in mind. If you have suggestions, feel free to open an issue!*


