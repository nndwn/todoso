package com.github.nndwn.todoso.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TodosoServiceTest : BasePlatformTestCase() {

    private lateinit var service: TodosoService

    override fun setUp() {
        super.setUp()
        service = project.getService(TodosoService::class.java)
    }

    fun testFormatNewTaskLine() {
        // Case: sample description -> - [ ] sample description 🆔 8XnWwK
        val res1 = service.formatNewTaskLine("sample description")
        assertNotNull(res1)
        assertTrue("Expected to start with '- [ ] sample description 🆔 ', but was: $res1", 
            res1!!.startsWith("- [ ] sample description 🆔 "))
        
        // Case: - [ ] sample description -> - [ ] - [ ] sample description 🆔 8XnWwK
        val res2 = service.formatNewTaskLine("- [ ] sample description")
        assertNotNull(res2)
        assertTrue("Expected to start with '- [ ] - [ ] sample description 🆔 ', but was: $res2",
            res2!!.startsWith("- [ ] - [ ] sample description 🆔 "))

        // Case: [H] sample description -> - [ ] ⏫ sample description 🆔 8XnWwK
        val res3 = service.formatNewTaskLine("[H] sample description")
        assertNotNull(res3)
        assertTrue("Expected to start with '- [ ] ⏫ sample description 🆔 ', but was: $res3",
            res3!!.startsWith("- [ ] ⏫ sample description 🆔 "))

        // Case: sample description [H] -> - [ ] sample description [H] 🆔 8XnWwK
        val res4 = service.formatNewTaskLine("sample description [H]")
        assertNotNull(res4)
        assertTrue("Expected to start with '- [ ] sample description [H] 🆔 ', but was: $res4",
            res4!!.startsWith("- [ ] sample description [H] 🆔 "))

        // Case: https://github.com/nndwn/todoso#readme -> - [ ] https://github.com/nndwn/todoso#readme 🆔 8XnWwK
        val res5 = service.formatNewTaskLine("https://github.com/nndwn/todoso#readme")
        assertNotNull(res5)
        assertTrue("Expected to start with '- [ ] https://github.com/nndwn/todoso#readme 🆔 ', but was: $res5",
            res5!!.startsWith("- [ ] https://github.com/nndwn/todoso#readme 🆔 "))

        // Case: "Beli Susu\nBeli Roti" -> - [ ] Beli Susu\nBeli Roti 🆔 ...
        val resMultiline = service.formatNewTaskLine("Beli Susu\nBeli Roti")
        assertNotNull(resMultiline)
        assertTrue("Expected to contain newline, but was: $resMultiline", resMultiline!!.contains("Beli Susu\nBeli Roti"))

        // Case: #apasaja a -> - [ ] #apasaja a 🆔 ...
        val resTagStart = service.formatNewTaskLine("#apasaja a")
        assertNotNull(resTagStart)
        assertTrue("Expected to contain tag at start, but was: $resTagStart", resTagStart!!.contains("#apasaja a"))

        // Case: Cek 🆔 lama -> - [ ] Cek 🆔 lama 🆔 ...
        val resIdSymbol = service.formatNewTaskLine("Cek 🆔 lama")
        assertNotNull(resIdSymbol)
        assertTrue("Expected to preserve existing ID-like symbol, but was: $resIdSymbol", resIdSymbol!!.contains("Cek 🆔 lama 🆔 "))

        // Case: fail #apasaja #apasaja2
        val resFailTags = service.formatNewTaskLine("#apasaja #apasaja2")
        assertNull("Expected null for only tags input, but was: $resFailTags", resFailTags)
        
        // Case: fail //sample description
        val resFail = service.formatNewTaskLine("//sample description")
        assertNull("Expected null for comment-only input, but was: $resFail", resFail)
    }
}
