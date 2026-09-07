package com.github.nndwn.todoso.toolWindow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Font


class TodosoInputPanelUiTest : BasePlatformTestCase() {

    private lateinit var inputPanel: TodosoInputPanel

    private var lastNewTask: String? = null
    private var lastUpdatedTask: String? = null
    private var lastConfirmedCancel: String? = null
    private var lastCreatedNote: String? = null
    private var cancelEditCalled = false

    override fun setUp() {
        super.setUp()

        lastNewTask = null
        lastUpdatedTask = null
        lastConfirmedCancel = null
        lastCreatedNote = null
        cancelEditCalled = false

        inputPanel = TodosoInputPanel(
            onNewTask = { lastNewTask = it },
            onUpdateTask = { lastUpdatedTask = it },
            onConfirmCancel = { lastConfirmedCancel = it },
            onCreateNote = { lastCreatedNote = it },
            onCancelEdit = { cancelEditCalled = true },
            fontInput = Font("Monospaced", Font.PLAIN, 12)
        )
    }

    /**
     * 1. Menguji Mode Normal:
     * - Tombol submit harus di-disable jika input kosong atau hanya berisi prefix Markdown status kosong (misal "- [ ]").
     * - Tombol submit aktif saat ada deskripsi valid.
     * - Mengeklik tombol submit memicu callback `onNewTask`.
     */
    fun testNormalModeValidationAndSubmit() {
        // Initially empty
        assertFalse("Tombol harus disabled saat teks kosong", inputPanel.newTaskButton.isEnabled)
        assertFalse("Tombol cancel harus tersembunyi pada mode normal", inputPanel.cancelButton.isVisible)

        // Hanya berisi prefix checkbox Markdown kosong
        inputPanel.inputTextArea.text = "- [ ] "
        assertFalse("Tombol harus disabled jika hanya berisi prefix status kosong", inputPanel.newTaskButton.isEnabled)

        inputPanel.inputTextArea.text = "- [/]   "
        assertFalse("Tombol harus disabled untuk prefix status DOING kosong", inputPanel.newTaskButton.isEnabled)

        // Teks valid
        inputPanel.inputTextArea.text = "Fix UI Navigation Bug #ui"
        assertTrue("Tombol harus enabled saat input memiliki deskripsi valid", inputPanel.newTaskButton.isEnabled)

        // Trigger Submit
        inputPanel.newTaskButton.doClick()
        assertEquals("Fix UI Navigation Bug #ui", lastNewTask)
    }

    /**
     * 2. Menguji Mode Edit:
     * - Tombol submit di-disable jika teks yang diedit TIDAK BERUBAH dari `originalText`.
     * - Tombol submit aktif jika ada perubahan teks.
     * - Mengeklik tombol submit memicu callback `onUpdateTask`.
     */
    fun testEditModeUnchangedTextValidation() {
        val original = "Beli kopi di minimarket"
        inputPanel.setEditMode(true, original)

        assertTrue("Mode aktif harus Edit", inputPanel.currentMode is InputMode.Edit)
        assertTrue("Tombol cancel harus terlihat pada mode Edit", inputPanel.cancelButton.isVisible)
        assertEquals("Beli kopi di minimarket", inputPanel.inputTextArea.text)
        assertFalse("Tombol update harus disabled jika teks tidak diubah", inputPanel.newTaskButton.isEnabled)

        // Ubah teks sedikit
        inputPanel.inputTextArea.text = "Beli kopi di minimarket #urgent"
        assertTrue("Tombol update harus enabled setelah teks diubah", inputPanel.newTaskButton.isEnabled)

        // Trigger Update
        inputPanel.newTaskButton.doClick()
        assertEquals("Beli kopi di minimarket #urgent", lastUpdatedTask)
    }

    /**
     * 3. Menguji Mode Note (Catatan):
     * - Mode Note secara otomatis menyuntikkan prefix "// " jika area input kosong.
     * - Validasi memastikan tombol disabled jika hanya ada "//" atau spasi.
     * - Menjamin prefix "// " disuntikkan secara otomatis jika pengguna secara tidak sengaja menghapus "//".
     */
    fun testNoteModeAutoPrefixAndValidation() {
        inputPanel.setNoteMode(true)

        assertTrue("Mode aktif harus Note", inputPanel.currentMode is InputMode.Note)
        assertEquals("// ", inputPanel.inputTextArea.text)
        assertFalse("Tombol update harus disabled jika hanya berisi '// ' tanpa isi", inputPanel.newTaskButton.isEnabled)

        inputPanel.inputTextArea.text = "// ini catatan penting"
        assertTrue("Tombol harus enabled setelah ada isi catatan", inputPanel.newTaskButton.isEnabled)

        inputPanel.newTaskButton.doClick()
        assertEquals("// ini catatan penting", lastCreatedNote)

        inputPanel.setNoteMode(true)
        inputPanel.inputTextArea.text = "catatan manual tanpa prefix"
        assertTrue(inputPanel.newTaskButton.isEnabled)

        inputPanel.newTaskButton.doClick()
        assertEquals(
            "Sistem harus otomatis menyuntikkan '// ' di depan jika pengguna lupa mengetiknya",
            "// catatan manual tanpa prefix",
            lastCreatedNote
        )
    }

    /**
     * 4. Menguji Mode Cancel (Pembatalan Task):
     * - Memastikan background dan mode beralih ke Cancel.
     * - Mengeklik submit memicu callback `onConfirmCancel`.
     */
    fun testCancelModeExecution() {
        inputPanel.setCancelMode(true, "Alasan: Diabaikan oleh Product Owner")

        assertTrue("Mode aktif harus Cancel", inputPanel.currentMode is InputMode.Cancel)
        assertTrue("Tombol cancel harus terlihat", inputPanel.cancelButton.isVisible)

        inputPanel.newTaskButton.doClick()
        assertEquals("Alasan: Diabaikan oleh Product Owner", lastConfirmedCancel)
    }

    /**
     * 5. Menguji Tombol Pembatalan Edit (Cancel Button Click) & Clear State:
     * - Mengeklik `cancelButton` memicu callback `onCancelEdit`.
     * - Memanggil `clearInputText()` mereset mode ke Normal dan mengosongkan teks.
     */
    fun testCancelEditAndClearState() {
        inputPanel.setEditMode(true, "Task yang sedang diedit")

        // Klik tombol Cancel
        inputPanel.cancelButton.doClick()
        assertTrue("Callback onCancelEdit harus dipanggil", cancelEditCalled)

        // Reset/Clear input panel
        inputPanel.clearInputText()
        assertTrue("Mode harus kembali ke Normal", inputPanel.currentMode is InputMode.Normal)
        assertEquals("", inputPanel.inputTextArea.text)
        assertFalse("Tombol cancel harus tersembunyi kembali", inputPanel.cancelButton.isVisible)
    }
}