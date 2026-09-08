# Todoso - IntelliJ Todo Manager

An IntelliJ plugin to manage your todo list directly from a `todo.md` file in the project root. Designed for simplicity, it supports modern standards inspired by **Obsidian** and **Notion**.

I created Todoso because I wanted a way to manage tasks without leaving my IDE. Instead of switching to external apps like Notion or Sticky Notes, you can keep your focus where you code. It's built for developers who appreciate clean Markdown and efficient workflows.

## Usage
just type task in field and click right select priority and select tags or if you want fast
```txt
[H] task description #tags1 #tag2 #v1.0.1
```

I’m not good at typing in English but this AI agent typing is more pathetic than me



#### File-Based Workflow
* **Automatic Integration**: Reads from `todo.md` at your project root by default[cite: 1].
* **Flexible Casing**: Automatically detects `todo.md`, `TODO.md`, `Todo.md`, or any casing variation without issues[cite: 1].
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



#### Flexible Input Field Behavior
flexible single-input field that intelligently processes both plain text descriptions and full Markdown task syntax.
Supported Input Styles
*    **Plain Text (Simple Task)**
        * Simply type your task description (e.g., `Update layout navbar`). 
        * Priority and existing tags are preserved or assigned default values.
*    **Explicit Format & Shortcodes (Quick Input)**
        * Type priority bracket shortcodes or emojis along with tags directly in the input bar (e.g., `[H] Fix navbar bug #ui #v1.0.1`). 
        * The parser automatically extracts and assigns the priority (HIGH / ⏫) and tags (#ui, #v1.0.1), removing syntax tokens to keep the description clean.
*    **Empty Checkbox Protection**: Submitting inputs containing only empty status checkboxes (e.g., `- [ ]` or `- [/]`) is automatically rejected to prevent blank task creation.
*    **Multi-State UI Modes**: Supports seamless UI transitions between **New Task**, **Edit Mode**, **Cancel Task Mode**, and **Note Mode** with contextual background highlights.
*    **Automatic Note Prefixing**: Entering Note Mode automatically injects the `//`  prefix and guarantees proper formatting.


####  Multi-Criteria Toolbar & Sorting Features
Todoso provides an interactive toolbar with dynamic view options and multi-criteria sorting to help you organize your tasks effortlessly:

* **File Selection**: Click the file search icon on the toolbar to choose any Markdown file directly from your project directory or your computer
*   **Multi-Criteria Sorting Options**:
      * **Default (File Order)**: Keeps the natural line order as written in `todo.md`.
      * **By Priority**: Orders tasks by urgency (`Highest` 🔺 → `Lowest` ⏬).
      * **By Status**: Groups tasks by progress state (`Doing` →  `Todo` →  `Done` / `Canceled`).
      * **By Date**: Sorts tasks chronologically using the earliest available date token (`Due Date` 📅 → `Start Date` 🛫 → `Created Date` ➕).
      * **Date then Priority (Combined Sort)**: Evaluates tasks by date first; if dates are equal or missing, it automatically falls back to sorting by priority.
*   **Persistent Sort State**: Selected sorting preferences are automatically saved in .idea/TodosoSettings.xml and restored across IDE restarts.
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

3. **Execution Duration Calculation**:
   * Automatically calculates execution duration between Start Date (🛫) and Completion Date (✅) upon completion (e.g., `1h 45m` or `30m`).


#### Metadata Comment Isolation (//)
1.   Any text placed after the metadata comment separator `//` is isolated as `metadata.notes` (e.g.,` - [ ] Fix UI #ui // check details`).
2.   URL protocol slashes (such as `http://` or `https://`) are protected and will never be falsely parsed as comment separators


---
*Developed with focus and UX in mind. If you have suggestions, feel free to open an issue!*


