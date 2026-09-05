# Todoso - IntelliJ Todo Manager

An IntelliJ plugin to manage your todo list directly from a `todo.md` file in the project root. Designed for simplicity, it supports modern standards inspired by **Obsidian** and **Notion**.

I created Todoso because I wanted a way to manage tasks without leaving my IDE. Instead of switching to external apps like Notion or Sticky Notes, you can keep your focus where you code. It's built for developers who appreciate clean Markdown and efficient workflows.

## Usage
just type task in field and click right select priority and select tags or if you want fast
```txt
[H] task description #tags1 #tag2 #v1.0.1
```

I’m not good at typing in English but this AI agent typing is more pathetic than me

##  Key Features

# Todoso - IntelliJ Todo Manager

An IntelliJ plugin to manage your todo list directly from a `todo.md` file in the project root. Designed for simplicity, it supports modern standards inspired by **Obsidian** and **Notion**.

I created Todoso because I wanted a way to manage tasks without leaving my IDE. Instead of switching to external apps like Notion or Sticky Notes, you can keep your focus where you code. It's built for developers who appreciate clean Markdown and efficient workflows.

## Usage
just type task in field and click right select priority and select tags or if you want fast
```txt
[H] task description #tags1 #tag2 #v1.0.1
```

I’m not good at typing in English but this AI agent typing is more pathetic than me

Sebuah baris hanya boleh diakui sebagai TodoTask jika karakter - [ atau -[] berada di paling awal baris (pola prefix baris), bukan di tengah-tengah kalimat deskripsi.

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

---
*Developed with focus and UX in mind. If you have suggestions, feel free to open an issue!*


