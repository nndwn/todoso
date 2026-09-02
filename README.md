# Todoso - IntelliJ Todo Manager

An IntelliJ plugin to manage your todo list directly from a `todo.md` file in the project root, supporting standard formats from Obsidian and Notion.

I created this for my own needs because I often forget what needs to be done for the next day. Usually, I use Sticky Notes, Google Keep, Notion, and Obsidian, but I found myself lazy to open those apps. Instead of opening another app every time I work on something, I thought it would be better to just create a `todo.md` file within the project. My goal is simplicity in reading and writing project tasks. I've added features that match my own standards while also integrating with Notion and Obsidian for easier analysis.

## 🚀 Key Features
*   **todo.md Integration**: Reads tasks directly from a Markdown file in the project root.
    * The plugin reads a file named `todo.md`. If the file does not exist, it will be automatically created when you add your first task via the plugin.
    * You can also customize the path and filename in `.idea/TodosoSettings.xml` like this:
    ```xml
    <?xml version="1.0" encoding="UTF-8"?>
       <project version="4">
        <component name="com.github.nndwn.todoso.services.MyProjectSettingsService">
            <option name="todoFileName" value="todo.md"/>  // Path relative to the project root
        </component>
       </project>
    ```
*   **Interactive Tool Window**: A clean, selectable task list.
    * I have some knowledge of UX, so I've tried to make it as simple as possible for users.
*   **Comprehensive Task Status**:
    * Tasks can be created in two ways: you can write them directly in `todo.md` or edit them within the plugin. Common statuses are as follows:
    *   `[ ]` : Todo (Not started).
    *   `[/]` : **Doing** (In progress) - Bold green text, automatically moves to the top.
    *   `[x]` : Done (Completed) - Strikethrough gray text.
    *   `[-]` or `❌` : Cancelled.
    
*   **Dual Priority System**:
    * **Obsidian Style**: `🔺` (Highest), `⏫` (High), `🔼` (Medium), `🔽` (Low), `⏬` (Lowest).
    * **My Style**: `[HH]` (Highest), `[H]` (High), `[M]` (Medium), `[L]` (Low), `[LL]` (Lowest).
    * These are the standard ones I know, as shown above. I added priority because Obsidian uses emoji standards, so I included them as follows:
    * In the plugin, writing uses the standard Obsidian style, but since I usually write directly, I made a version where both are readable and then added tags after.
    
*   **Dynamic Hashtags (`#tag`)**: Automatic tag detection (e.g., `#ui`, `#bug`, `#v1.3.1`) with Cyan color highlighting. 
    * Basically, every tag created will be collected. If there is a `#` inside a task, don't worry—the plugin is smart enough to handle it. For my scenarios, there seem to be no issues. Additionally, I tried to add a track record starting from when the user marks a task as 'Doing' until it is 'Done'.
 
* **Time Management & Metadata**:
  *   Supports Obsidian date emojis with optional time: `🛫` (Start), `📅` (Due), `⏳` (Scheduled), `✅` (Completed), `➕` (Created).
  *   Format supported: `YYYY-MM-DD` or `YYYY-MM-DD HH:mm` or `YYYY-MM-DD HH:mm:ss`.
  *   Supports additional metadata using a space and ` // ` at the end of the line.
  * You don't need to worry about the number of records in the plugin; there's a certain satisfaction in seeing how long a task took to complete.
     
* **Persistent Settings**:
  * Currently, there are settings for visual mode, todo.md path, and filters.
  *   Toggle **Visual Mode** via the toolbar to show/hide priority colors and emojis (helps you focus!).
  *   **Persistent Filters**: Your choices for **Priority Filter** and **Status Filter** are saved per project, so your focus remains consistent even after restarting the IDE.
  *   Settings are saved per project in `.idea/TodosoSettings.xml`.
  
*   **Smart UI**:
  * This UX is based on my own needs; if you have suggestions, feel free to open an issue.
    *   Hides marker icons/dates from the main list for a clean look, but shows them fully in the **Tooltip**.
    *   Automatic sorting: **Doing > Todo (by Priority) > Done**.
    *   Integrated toolbar for instant task list **Refresh**.
    *   Double-click to navigate to the specific line in `todo.md`.
    *   Right-click to access the menu for 
        * Change Status 
        * Edit Task and Tags 
        * Change Priority 
        * Delete 
        * priority filtering 
        * Challenge Task 
        * Refresh  
        * Filter Priority 
        * viewMode
    *   **Change Status**:
        * TODO -> DOING : OK (Add Start Date)
        * DOING -> DONE : OK (Add End Date)
        * DONE -> DOING : OK (Remove End Date, Update Start Date)
        * ANY -> CANCELLED : OK (Strikethrough, removes Start/End dates, adds ❌ and reason)
        * DONE -> CANCELLED : Blocked (Logically, a finished item cannot be cancelled).
    *   Duplicate status actions are not allowed.
    *   If a task was previously marked as 'Done', its previous time is reset when moved back to 'Todo' or 'Doing'.
    *   Automatic duration calculation is displayed on hover when a task is completed.
    *   **Challenge Task**: A "gamification" feature that randomly picks a task from your **Todo** list and marks it as **Doing** (`🛫`), helping you overcome procrastination by deciding what to work on next.
    *   **Edit and Priority Restriction**: Editing text or changing priority is only allowed for tasks with **Todo** or **Doing** status. Completed (**Done**) or **Cancelled** tasks are read-only to preserve history (but can still be deleted).
    *   **Task Deletion**: Tasks can be deleted permanently from the `todo.md` file. A confirmation dialog will appear to prevent accidental deletion.
    *   **Mandatory Cancellation Reason**: Cancelled tasks must include a reason. When cancelled via the plugin, existing Start (`🛫`) and Done (`✅`) dates are automatically removed to keep the record clean, and replaced with the Cancellation date (`❌`) and the provided reason.

## 📝 Writing Rules for `todo.md`

These rules apply **both** when writing directly in the `todo.md` file and when using the **New Task** input field in the plugin. 

Write your tasks using the following format:
`- [status] [Priority] Task Description #tag1 #tag2 [Date Emoji] // Metadata`

> [!TIP]
> If you omit the `- [ ]` prefix in the plugin's input field, it will be added automatically. You can also directly type a full line (e.g., `- [/] ⏫ Task #tag`) to create a task with a specific status and priority immediately.

### Usage Example:
```markdown
- [/] ⏫ Fix SlideUpPanel layout bug #ui #bug 🛫 2026-09-01
- [ ] 🔺 Migration to Navigation3 #migration ⏳ 2026-09-05 14:00
- [x] 🔼 Finished cleaning up icons #design ✅ 2026-08-30 18:00
- [ ] [HH] Urgent legacy task #refactor
- [ ] [LL] Very low priority legacy task
- [ ] Regular task with metadata // additional notes here
```
