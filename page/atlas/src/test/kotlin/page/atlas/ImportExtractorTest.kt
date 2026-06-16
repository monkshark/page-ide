package page.atlas

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.analyzer.ImportExtractor
import page.atlas.analyzer.RawImport

class ImportExtractorTest {

    @Test
    fun `java imports including static and wildcard`() {
        val text = """
            package com.example;

            import java.util.List;
            import static java.lang.Math.max;
            import com.example.util.*;

            public class Main {}
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("Main.java"), text)
        assertEquals(
            listOf(
                RawImport("java.util.List", false, listOf("List")),
                RawImport("java.lang.Math.max", false, listOf("max")),
                RawImport("com.example.util", false),
            ),
            imports,
        )
    }

    @Test
    fun `kotlin imports including alias and wildcard`() {
        val text = """
            package com.example

            import foo.bar.Baz
            import foo.qux.Quux as Q
            import foo.all.*

            fun main() {}
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("Main.kt"), text)
        assertEquals(
            listOf(
                RawImport("foo.bar.Baz", false, listOf("Baz")),
                RawImport("foo.qux.Quux", false, listOf("Q")),
                RawImport("foo.all", false),
            ),
            imports,
        )
    }

    @Test
    fun `python imports including relative from and multi`() {
        val text = """
            import os.path as osp, sys
            from ..pkg import thing
            from .local import x
            from importlib import metadata
            import json
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("main.py"), text)
        assertEquals(
            listOf(
                RawImport("os.path", false),
                RawImport("sys", false),
                RawImport("..pkg", true, listOf("thing")),
                RawImport(".local", true, listOf("x")),
                RawImport("importlib", false, listOf("metadata")),
                RawImport("json", false),
            ),
            imports,
        )
    }

    @Test
    fun `javascript imports distinguish relative and package`() {
        val text = """
            import { a } from './util';
            import * as fs from 'fs';
            import '../styles/side-effect.css';
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("app.js"), text)
        assertEquals(
            listOf(
                RawImport("./util", true, listOf("a")),
                RawImport("fs", false, listOf("fs")),
                RawImport("../styles/side-effect.css", true),
            ),
            imports,
        )
    }

    @Test
    fun `typescript imports`() {
        val text = """
            import { T } from "./types";
            import express from "express";
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("server.ts"), text)
        assertEquals(
            listOf(
                RawImport("./types", true, listOf("T")),
                RawImport("express", false, listOf("express")),
            ),
            imports,
        )
    }

    @Test
    fun `go grouped imports`() {
        val text = """
            package main

            import (
                "fmt"
                util "example.com/pkg/util"
            )
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("main.go"), text)
        assertEquals(
            listOf(
                RawImport("fmt", false),
                RawImport("example.com/pkg/util", false),
            ),
            imports,
        )
    }

    @Test
    fun `rust use declarations including group and alias`() {
        val text = """
            use crate::util::helper;
            use std::collections::HashMap;
            use crate::models::{User, Post};
            use std::fmt as f;

            fn main() {}
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("main.rs"), text)
        assertEquals(
            listOf(
                RawImport("crate::util::helper", true),
                RawImport("std::collections::HashMap", false),
                RawImport("crate::models", true),
                RawImport("std::fmt", false),
            ),
            imports,
        )
    }

    @Test
    fun `dart imports distinguish sdk package and relative`() {
        val text = """
            import 'dart:async';
            import 'package:flutter/material.dart';
            import 'package:my_app/widgets/button.dart' as btn;
            import 'utils/helper.dart' show formatDate;
            import '../models/user.dart';
            export 'src/api.dart';
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("main.dart"), text)
        assertEquals(
            listOf(
                RawImport("dart:async", false),
                RawImport("package:flutter/material.dart", false),
                RawImport("package:my_app/widgets/button.dart", false, listOf("btn")),
                RawImport("utils/helper.dart", true, listOf("formatDate")),
                RawImport("../models/user.dart", true),
                RawImport("src/api.dart", true),
            ),
            imports,
        )
    }

    @Test
    fun `unsupported extension returns empty`() {
        assertTrue(ImportExtractor.extract(Path.of("notes.txt"), "import x").isEmpty())
        assertTrue(ImportExtractor.supports(Path.of("a.kt")))
        assertTrue(!ImportExtractor.supports(Path.of("notes.txt")))
    }

    @Test
    fun `kotlin top-level declarations across many symbols in one file`() {
        val text = """
            package page.atlas.graph

            import foo.Bar

            data class GraphSlice(val nodes: List<GraphNode>)

            class GraphNode(val id: String)

            sealed interface NodeKind {
                object Active : NodeKind
            }

            enum class EdgeKind { IMPORT, CALLS }

            internal object Registry {
                val cache = mutableMapOf<String, Int>()
            }

            annotation class Marker(val name: String)

            typealias Ids = List<String>

            fun buildSlice(): GraphSlice? = null

            private fun GraphNode.weight(): Int = 1

            const val MAX = 100
        """.trimIndent()
        val decls = ImportExtractor.analyze(Path.of("GraphModel.kt"), text).declarations
        assertEquals("page.atlas.graph", decls.packageName)
        assertEquals(
            listOf(
                "GraphSlice", "GraphNode", "NodeKind", "EdgeKind", "Registry",
                "Marker", "Ids", "buildSlice", "weight", "MAX",
            ),
            decls.symbols,
        )
    }

    @Test
    fun `kotlin nested declarations are excluded`() {
        val text = """
            package p

            class Outer {
                class Inner
                fun member() {}
                val field = 1
            }
        """.trimIndent()
        val decls = ImportExtractor.analyze(Path.of("Outer.kt"), text).declarations
        assertEquals("p", decls.packageName)
        assertEquals(listOf("Outer"), decls.symbols)
    }

    @Test
    fun `java top-level types in one file`() {
        val text = """
            package com.example.model;

            import java.util.List;

            @Deprecated
            public final class Point {}

            interface Shape {}

            enum Color { RED, GREEN }

            public record Pair(int a, int b) {}

            @interface Json {}
        """.trimIndent()
        val decls = ImportExtractor.analyze(Path.of("Point.java"), text).declarations
        assertEquals("com.example.model", decls.packageName)
        assertEquals(listOf("Point", "Shape", "Color", "Pair", "Json"), decls.symbols)
    }

    @Test
    fun `package-less kotlin file yields empty package`() {
        val text = """
            class Lonely
        """.trimIndent()
        val decls = ImportExtractor.analyze(Path.of("Lonely.kt"), text).declarations
        assertEquals("", decls.packageName)
        assertEquals(listOf("Lonely"), decls.symbols)
    }

    @Test
    fun `unsupported language carries empty declarations`() {
        val decls = ImportExtractor.analyze(Path.of("app.js"), "class Foo {}").declarations
        assertEquals("", decls.packageName)
        assertEquals(emptyList(), decls.symbols)
    }
}
