package page.atlas

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.analyzer.ImportExtractor
import page.atlas.analyzer.RawRelation
import page.atlas.graph.EdgeKind

class SupertypeExtractionTest {

    private fun relations(fileName: String, text: String): List<RawRelation> =
        ImportExtractor.analyze(Path.of(fileName), text).relations

    @Test
    fun `java extends and implements`() {
        val text = """
            package demo;
            import demo.base.AbstractShape;
            public class Circle extends AbstractShape implements Shape, Comparable<Circle> {
            }
        """.trimIndent()
        val rels = relations("Circle.java", text)
        assertEquals(
            listOf(
                RawRelation("AbstractShape", EdgeKind.EXTENDS),
                RawRelation("Shape", EdgeKind.IMPLEMENTS),
                RawRelation("Comparable", EdgeKind.IMPLEMENTS),
            ),
            rels,
        )
    }

    @Test
    fun `dart extends with mixin and implements`() {
        val text = """
            import 'package:flutter/material.dart';
            class HomePage extends StatefulWidget with SomeMixin implements Tappable {
            }
        """.trimIndent()
        val rels = relations("home_page.dart", text)
        assertEquals(
            listOf(
                RawRelation("StatefulWidget", EdgeKind.EXTENDS),
                RawRelation("SomeMixin", EdgeKind.IMPLEMENTS),
                RawRelation("Tappable", EdgeKind.IMPLEMENTS),
            ),
            rels,
        )
    }

    @Test
    fun `java interface extends interfaces`() {
        val rels = relations("Repo.java", "interface Repo extends Readable, Writable {}")
        assertEquals(
            listOf(
                RawRelation("Readable", EdgeKind.EXTENDS),
                RawRelation("Writable", EdgeKind.EXTENDS),
            ),
            rels,
        )
    }

    @Test
    fun `kotlin constructor invocation is extends and plain type is implements`() {
        val text = """
            package sample
            class Circle(val r: Double) : Figure(r), Drawable
        """.trimIndent()
        val rels = relations("Circle.kt", text)
        assertEquals(
            listOf(
                RawRelation("Figure", EdgeKind.EXTENDS),
                RawRelation("Drawable", EdgeKind.IMPLEMENTS),
            ),
            rels,
        )
    }

    @Test
    fun `kotlin by delegation is implements`() {
        val rels = relations("Wrapper.kt", "class Wrapper(d: Drawable) : Drawable by d")
        assertEquals(listOf(RawRelation("Drawable", EdgeKind.IMPLEMENTS)), rels)
    }

    @Test
    fun `kotlin object expression supertype`() {
        val rels = relations("Holder.kt", "object Holder : Runnable { override fun run() {} }")
        assertEquals(listOf(RawRelation("Runnable", EdgeKind.IMPLEMENTS)), rels)
    }

    @Test
    fun `kotlin generic supertype keeps base name`() {
        val rels = relations("Sorter.kt", "class Sorter : Comparable<Sorter> { override fun compareTo(other: Sorter) = 0 }")
        assertEquals(listOf(RawRelation("Comparable", EdgeKind.IMPLEMENTS)), rels)
    }

    @Test
    fun `typescript class extends and implements`() {
        val text = """
            import { BaseComponent } from './base';
            export class Widget extends BaseComponent implements Renderable, Closable {
            }
        """.trimIndent()
        val rels = relations("widget.ts", text)
        assertEquals(
            listOf(
                RawRelation("BaseComponent", EdgeKind.EXTENDS),
                RawRelation("Renderable", EdgeKind.IMPLEMENTS),
                RawRelation("Closable", EdgeKind.IMPLEMENTS),
            ),
            rels,
        )
    }

    @Test
    fun `typescript interface extends`() {
        val rels = relations("api.ts", "interface Extended extends Base { x: number }")
        assertEquals(listOf(RawRelation("Base", EdgeKind.EXTENDS)), rels)
    }

    @Test
    fun `javascript class heritage is extends`() {
        val rels = relations("sub.js", "class Sub extends Base { }")
        assertEquals(listOf(RawRelation("Base", EdgeKind.EXTENDS)), rels)
    }

    @Test
    fun `python bases are extends and kwargs are skipped`() {
        val text = """
            class Circle(Shape, metaclass=ABCMeta):
                pass
        """.trimIndent()
        val rels = relations("circle.py", text)
        assertEquals(listOf(RawRelation("Shape", EdgeKind.EXTENDS)), rels)
    }

    @Test
    fun `python nested class bases are found`() {
        val text = """
            class Outer(Base):
                class Inner(Mixin):
                    pass
        """.trimIndent()
        val rels = relations("outer.py", text)
        assertEquals(
            listOf(
                RawRelation("Base", EdgeKind.EXTENDS),
                RawRelation("Mixin", EdgeKind.EXTENDS),
            ),
            rels,
        )
    }

    @Test
    fun `python class without bases has no relations`() {
        assertTrue(relations("plain.py", "class Plain:\n    pass").isEmpty())
    }

    @Test
    fun `analyze returns imports and relations together`() {
        val text = """
            package sample
            import sample.base.Figure
            class Circle : Figure()
        """.trimIndent()
        val analysis = ImportExtractor.analyze(Path.of("Circle.kt"), text)
        assertEquals(listOf("sample.base.Figure"), analysis.imports.map { it.target })
        assertEquals(listOf(RawRelation("Figure", EdgeKind.EXTENDS)), analysis.relations)
    }

    @Test
    fun `go and rust produce no relations`() {
        assertTrue(relations("main.go", "package main\n\ntype S struct{}\n").isEmpty())
        assertTrue(relations("main.rs", "struct S;\nimpl Clone for S { fn clone(&self) -> S { S } }\n").isEmpty())
    }
}
