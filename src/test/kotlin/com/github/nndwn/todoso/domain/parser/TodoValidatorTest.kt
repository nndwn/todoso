package com.github.nndwn.todoso.domain.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TodoValidatorTest : BasePlatformTestCase() {

  fun testNullOrBlank() {
    assertFalse(TodoValidator.isContentValid(null))
    assertFalse(TodoValidator.isContentValid(""))
    assertFalse(TodoValidator.isContentValid("   "))
  }

  fun testOnlyCheckboxPrefix() {
    assertFalse("Should reject empty todo", TodoValidator.isContentValid("- [ ] "))
    assertFalse("Should reject empty doing", TodoValidator.isContentValid("- [/] "))
    assertFalse("Should reject empty done", TodoValidator.isContentValid("- [x] "))
    assertFalse("Should reject empty cancelled", TodoValidator.isContentValid("- [-] "))
  }

  fun testOnlyTags() {
    assertFalse(TodoValidator.isContentValid("#tag"))
    assertFalse(TodoValidator.isContentValid("#tag1 #tag2"))
    assertFalse(TodoValidator.isContentValid("- [ ] #tag"))
  }

  fun testOnlyPriority() {
    // Emoji style
    assertFalse(TodoValidator.isContentValid("🔺"))
    assertFalse(TodoValidator.isContentValid("⏫"))
    assertFalse(TodoValidator.isContentValid("- [ ] 🔼"))
    
    // Bracket style
    assertFalse(TodoValidator.isContentValid("[H]"))
    assertFalse(TodoValidator.isContentValid("[High]"))
    assertFalse(TodoValidator.isContentValid("- [ ] [LOW]"))
  }

  fun testOnlyDates() {
    assertFalse(TodoValidator.isContentValid("🛫 2026-09-10"))
    assertFalse(TodoValidator.isContentValid("📅 2026-09-10 10:00"))
    assertFalse(TodoValidator.isContentValid("✅ 2026-09-10"))
    assertFalse(TodoValidator.isContentValid("📝 2026-09-10 14:00"))
    assertFalse(TodoValidator.isContentValid("- [ ] 🛫 2026-09-10 ➕ 2026-09-01"))
  }

  fun testOnlyId() {
    assertFalse(TodoValidator.isContentValid("🆔 8x2k1a"))
    assertFalse(TodoValidator.isContentValid("- [ ] 🆔 a1b2c3d4"))
  }

  fun testOnlyComments() {
    assertFalse(TodoValidator.isContentValid("// hanya catatan"))
    assertFalse(TodoValidator.isContentValid("- [ ] // catatan kosong"))
  }

  fun testMixedMetadataWithoutDescription() {
    val mixed = "- [ ] 🔺 #urgent 📅 2026-12-31 🆔 abc123 // Note"
    assertFalse("Should reject if no actual description text", TodoValidator.isContentValid(mixed))
  }

  fun testValidDescriptionWithMetadata() {
    assertTrue(TodoValidator.isContentValid("Beli Susu"))
    assertTrue(TodoValidator.isContentValid("- [ ] Beli Susu #grocery"))
    assertTrue(TodoValidator.isContentValid("Meeting 🔺 📅 2026-09-10"))
    assertTrue(TodoValidator.isContentValid("Task with 🆔 in middle"))
  }

  fun testSpecialCharactersAndUnicode() {
    assertTrue("Should allow Japanese text", TodoValidator.isContentValid("🇯🇵 タスク"))
    assertTrue("Should allow symbols", TodoValidator.isContentValid("!!! Important !!!"))
    assertTrue("Should allow math", TodoValidator.isContentValid("1 + 1 = 2"))
  }
}
