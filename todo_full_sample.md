# Todoso Full Feature Sample (100 Tasks)

## Core Features & Priorities
- [ ] 🔺 Highest Priority Task #urgent #core 🆔 c-001 ➕ 2026-09-01 09:00 // Initial creation note
- [/] ⏫ High Priority Task with multiline description\nSecond line of desc #development 🆔 c-002 🛫 2026-09-02 10:00 📅 2026-09-05 // Working on it\nAdded more details
- [x] 🔼 Medium Priority Completed Task #production 🆔 c-003 🛫 2026-09-01 ✅ 2026-09-02 18:00 // Finished early!
- [-] 🔽 Low Priority Cancelled Task #cleanup 🆔 c-004 ❌ 2026-09-03 // Not needed anymore
- [ ] ⏬ Lowest Priority Task #backlog 🆔 c-005 📅 2026-12-31
- [ ] [HH] Highest Priority using Code marker #urgent 🆔 c-006 ➕ 2026-09-04
- [ ] [H] High Priority using Code marker #feature 🆔 c-007 📅 2026-09-15
- [ ] [M] Medium Priority using Code marker #ui 🆔 c-008
- [ ] [L] Low Priority using Code marker #issue 🆔 c-009
- [ ] [LL] Lowest Priority using Code marker #doc 🆔 c-010

## Date & Metadata Variations
- [ ] Task with all dates 🆔 d-001 ➕ 2026-09-01 🛫 2026-09-02 📅 2026-09-10 ✅ 2026-09-05 ❌ 2026-09-06 📝 2026-09-04 // Complete metadata test
- [ ] Task with time in dates 🆔 d-002 ➕ 2026-09-01 08:30 📅 2026-09-01 17:00
- [ ] Task with only created date 🆔 d-003 ➕ 2026-09-10
- [x] Task with duration 🆔 d-004 🛫 2026-09-05 09:00 ✅ 2026-09-05 11:30 // Should show 2h 30m duration
- [ ] Task with edited date 🆔 d-005 📝 2026-09-10 14:45
- [ ] Task without any dates or metadata 🆔 d-006
- [ ] Task with dates in different order 🆔 d-007 📅 2026-09-20 🛫 2026-09-15 🆔 custom-id-123
- [ ] Task with partial time 🆔 d-008 ➕ 2026-09-05 12:00
- [x] Task done today 🆔 d-009 ✅ 2026-09-10
- [/] Task started today 🆔 d-010 🛫 2026-09-10

## Tag & Note Scenarios
- [ ] Task with multiple tags #tag1 #tag2 #tag3/subtag 🆔 t-001
- [ ] Task with tags containing special chars #c++ #c# #v1.0 🆔 t-002
- [ ] Task with note containing markdown link 🆔 t-003 // Check this: [Google](https://google.com)
- [ ] Task with note containing markdown image 🆔 t-004 // Look: Image placeholder
- [ ] Task with multiline notes 🆔 t-005 // Note line 1\nNote line 2\nNote line 3
- [ ] Task with tags and notes #work #home 🆔 t-006 // Remember to buy milk
- [ ] Task with URL in description but not a tag: Visit https://github.com/nndwn/todoso 🆔 t-007
- [ ] Task with tag-like text in notes #note-tag 🆔 t-008 // This #is-not-a-tag-here
- [ ] Task with escaped newline in description: First line\\nSecond line 🆔 t-009
- [ ] Task with empty note 🆔 t-010 //

## UI & Layout Stress
- [ ] Very long description text that should wrap in the UI pane if not handled correctly by the layout manager and scrollable tracks viewport width property set to true in JTextPane 🆔 ui-001
- [ ] 🔺 Short 🆔 ui-002
- [ ] ⏬ #long-tag-name-that-might-overflow-the-ui-rendering-space-in-the-component 🆔 ui-003
- [ ] Task with emojis in description: 🚀 🛠️ 🎨 📝 🆔 ui-004
- [ ] Task with special characters in description: ( ) [ ] { } < > | \ / 🆔 ui-005
- [ ] Task with number starting description: 1. Do something 2. Do another thing 🆔 ui-006
- [ ] Task with markdown-like syntax in description: **Bold** and *Italic* (not parsed but should render as text) 🆔 ui-007
- [ ] Task with mixed Chinese/English: 修复 bug in structure #ui 🆔 ui-008
- [ ] Task with indented spaces:    - [ ] Indented task 🆔 ui-009
- [ ] Task with tab character in description 🆔 ui-010

## Mixed 50 Tasks (41-90)
- [ ] 🔺 #bug Fix null pointer in VfsListener 🆔 m-041 📅 2026-09-12 // Urgent fix required
- [/] ⏫ #feature Implement cloud sync 🆔 m-042 🛫 2026-09-10 // Phase 1 started
- [x] 🔼 #doc Update API documentation 🆔 m-043 ✅ 2026-09-05 // Published to portal
- [-] 🔽 #issue Old dependency cleanup 🆔 m-044 ❌ 2026-09-01 // Obsolete
- [ ] ⏬ #test Write unit tests for DateParser 🆔 m-045 📅 2026-10-01
- [ ] 🔺 #security Fix XSS vulnerability in HTML tooltip 🆔 m-046 📅 2026-09-11
- [/] ⏫ #ui Refactor TodosoItemComponent 🆔 m-047 🛫 2026-09-10 // Extracting metadata logic
- [x] 🔼 #feature Add tag suggestions 🆔 m-048 ✅ 2026-09-09
- [ ] 🔽 #cleanup Remove unused resource files 🆔 m-049
- [ ] ⏬ #doc Write user guide 🆔 m-050
- [ ] 🔺 #urgent Server migration 🆔 m-051 📅 2026-09-15
- [/] ⏫ #development Database optimization 🆔 m-052 🛫 2026-09-10
- [x] 🔼 #production Hotfix for crash on startup 🆔 m-053 ✅ 2026-09-08
- [ ] 🔽 #issue Typo in error message 🆔 m-054
- [ ] ⏬ #test Integration tests for search 🆔 m-055
- [ ] 🔺 #feature Multi-file support 🆔 m-056 📅 2026-11-01
- [/] ⏫ #ui Implement search bar in toolbar 🆔 m-057 🛫 2026-09-10
- [x] 🔼 #bug Fix icon colorization 🆔 m-058 ✅ 2026-09-09
- [ ] 🔽 #cleanup Delete old branch 🆔 m-059
- [ ] ⏬ #doc Javadoc for services 🆔 m-060
- [ ] 🔺 #urgent Client meeting demo 🆔 m-061 📅 2026-09-13
- [/] ⏫ #feature Export to CSV 🆔 m-062 🛫 2026-09-10
- [x] 🔼 #production Release version 1.2.0 🆔 m-063 ✅ 2026-09-07
- [ ] 🔽 #issue Rename project 🆔 m-064
- [ ] ⏬ #test Performance benchmarks 🆔 m-065
- [ ] 🔺 #feature Task recurrence 🆔 m-066 📅 2026-12-01
- [/] ⏫ #ui Animation for tool window 🆔 m-067 🛫 2026-09-10
- [x] 🔼 #bug Fix tag deletion bug 🆔 m-068 ✅ 2026-09-06
- [ ] 🔽 #cleanup Minify CSS 🆔 m-069
- [ ] ⏬ #doc License update 🆔 m-070
- [ ] 🔺 #urgent Fix build failure on CI 🆔 m-071 📅 2026-09-11
- [/] ⏫ #feature Dark mode toggle 🆔 m-072 🛫 2026-09-10
- [x] 🔼 #production Fix memory leak in cache 🆔 m-073 ✅ 2026-09-05
- [ ] 🔽 #issue Icon too small on 4K 🆔 m-074
- [ ] ⏬ #test Code coverage report 🆔 m-075
- [ ] 🔺 #feature Drag and drop tasks 🆔 m-076 📅 2026-10-15
- [/] ⏫ #ui Improve input panel accessibility 🆔 m-077 🛫 2026-09-10
- [x] 🔼 #bug Fix time parsing error 🆔 m-078 ✅ 2026-09-09
- [ ] 🔽 #cleanup Optimize imports 🆔 m-079
- [ ] ⏬ #doc Readme translation 🆔 m-080
- [ ] 🔺 #urgent Security patch for SSL 🆔 m-081 📅 2026-09-12
- [/] ⏫ #feature Git integration 🆔 m-082 🛫 2026-09-10
- [x] 🔼 #production Update to Gradle 8.0 🆔 m-083 ✅ 2026-09-01
- [ ] 🔽 #issue Tab order in input panel 🆔 m-084
- [ ] ⏬ #test Stress test with 5000 tasks 🆔 m-085
- [ ] 🔺 #feature Voice commands 🆔 m-086 📅 2027-01-01
- [/] ⏫ #ui Custom task colors 🆔 m-087 🛫 2026-09-10
- [x] 🔼 #bug Fix search case sensitivity 🆔 m-088 ✅ 2026-09-10
- [ ] 🔽 #cleanup Formatting codebase 🆔 m-089
- [ ] ⏬ #doc API changelog 🆔 m-090

## Final 10 Tasks (91-100)
- [ ] 🔺 Final task 91 #last 🆔 f-091 📅 2026-09-15 // Almost there
- [/] ⏫ Final task 92 #last 🆔 f-092 🛫 2026-09-10
- [x] 🔼 Final task 93 #last 🆔 f-093 ✅ 2026-09-10
- [-] 🔽 Final task 94 #last 🆔 f-094 ❌ 2026-09-09
- [ ] ⏬ Final task 95 #last 🆔 f-095
- [ ] 🔺 Final task 96 #last 🆔 f-096 📅 2026-09-20
- [/] ⏫ Final task 97 #last 🆔 f-097 🛫 2026-09-10
- [x] 🔼 Final task 98 #last 🆔 f-098 ✅ 2026-09-10 16:00
- [ ] 🔽 Final task 99 #last 🆔 f-099
- [x] ⏬ Final task 100 - Mission Accomplished! #complete #test 🆔 f-100 ✅ 2026-09-10 16:30 // Successfully generated 100 tasks with full format support.
