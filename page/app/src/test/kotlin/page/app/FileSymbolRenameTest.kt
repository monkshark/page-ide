package page.app

import org.eclipse.lsp4j.SymbolKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import page.lsp.DocumentSymbolEntry
import page.lsp.SymbolRange

class FileSymbolRenameTest {

    private fun sym(
        name: String,
        kind: SymbolKind,
        container: String? = null,
        children: List<DocumentSymbolEntry> = emptyList(),
    ): DocumentSymbolEntry {
        val r = SymbolRange(0, 0, 0, name.length)
        return DocumentSymbolEntry(
            name = name,
            detail = null,
            kind = kind,
            range = r,
            selectionRange = r,
            containerName = container,
            children = children,
        )
    }

    @Test
    fun `picks top-level class matching stem`() {
        val syms = listOf(
            sym("Bar", SymbolKind.Class),
            sym("Other", SymbolKind.Class),
        )
        val pick = FileSymbolRename.findRenamableTopLevelSymbol("Bar", syms)
        assertNotNull(pick)
        assertEquals("Bar", pick!!.name)
        assertEquals(SymbolKind.Class, pick.kind)
    }

    @Test
    fun `prefers class over function when both match`() {
        val syms = listOf(
            sym("Bar", SymbolKind.Function),
            sym("Bar", SymbolKind.Class),
        )
        val pick = FileSymbolRename.findRenamableTopLevelSymbol("Bar", syms)
        assertEquals(SymbolKind.Class, pick?.kind)
    }

    @Test
    fun `falls back to function when no class matches`() {
        val syms = listOf(
            sym("useBar", SymbolKind.Function),
            sym("Other", SymbolKind.Class),
        )
        val pick = FileSymbolRename.findRenamableTopLevelSymbol("useBar", syms)
        assertEquals(SymbolKind.Function, pick?.kind)
    }

    @Test
    fun `skips nested members with matching name`() {
        val nested = sym("Bar", SymbolKind.Class, container = "Outer")
        val syms = listOf(sym("Outer", SymbolKind.Class, children = listOf(nested)))
        assertNull(FileSymbolRename.findRenamableTopLevelSymbol("Bar", syms))
    }

    @Test
    fun `returns null when no match`() {
        val syms = listOf(sym("Foo", SymbolKind.Class))
        assertNull(FileSymbolRename.findRenamableTopLevelSymbol("Bar", syms))
    }

    @Test
    fun `returns null on empty stem`() {
        val syms = listOf(sym("Bar", SymbolKind.Class))
        assertNull(FileSymbolRename.findRenamableTopLevelSymbol("", syms))
    }

    @Test
    fun `valid identifier accepts normal names`() {
        assertTrue(FileSymbolRename.isValidKotlinIdentifier("Bar"))
        assertTrue(FileSymbolRename.isValidKotlinIdentifier("useBar"))
        assertTrue(FileSymbolRename.isValidKotlinIdentifier("_underscore"))
        assertTrue(FileSymbolRename.isValidKotlinIdentifier("Camel123"))
    }

    @Test
    fun `valid identifier rejects keywords and invalid chars`() {
        assertFalse(FileSymbolRename.isValidKotlinIdentifier(""))
        assertFalse(FileSymbolRename.isValidKotlinIdentifier("123Bar"))
        assertFalse(FileSymbolRename.isValidKotlinIdentifier("Bar-Foo"))
        assertFalse(FileSymbolRename.isValidKotlinIdentifier("class"))
        assertFalse(FileSymbolRename.isValidKotlinIdentifier("fun"))
        assertFalse(FileSymbolRename.isValidKotlinIdentifier("object"))
    }

    @Test
    fun `stripKotlinExtension recognizes kt and kts`() {
        assertEquals("Bar", FileSymbolRename.stripKotlinExtension("Bar.kt"))
        assertEquals("Build", FileSymbolRename.stripKotlinExtension("Build.kts"))
        assertNull(FileSymbolRename.stripKotlinExtension("Bar.java"))
        assertNull(FileSymbolRename.stripKotlinExtension("Bar"))
    }

    @Test
    fun `readPackageDeclaration reads simple package`() {
        val text = "package refscan\n\nclass Bar\n"
        assertEquals("refscan", FileSymbolRename.readPackageDeclaration(text))
    }

    @Test
    fun `readPackageDeclaration reads dotted package`() {
        val text = "package com.example.refscan\nclass Bar\n"
        assertEquals("com.example.refscan", FileSymbolRename.readPackageDeclaration(text))
    }

    @Test
    fun `readPackageDeclaration skips leading comments and annotations`() {
        val text = "// header\n@file:JvmName(\"X\")\npackage refscan\nclass Bar\n"
        assertEquals("refscan", FileSymbolRename.readPackageDeclaration(text))
    }

    @Test
    fun `readPackageDeclaration returns null when no package`() {
        val text = "import something\nclass Bar\n"
        assertNull(FileSymbolRename.readPackageDeclaration(text))
    }

    @Test
    fun `readPackageDeclaration returns null on empty file`() {
        assertNull(FileSymbolRename.readPackageDeclaration(""))
    }
}
