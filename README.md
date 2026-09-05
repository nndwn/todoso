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

---
*Developed with focus and UX in mind. If you have suggestions, feel free to open an issue!*


