package page.atlas

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.analyzer.CallSite
import page.atlas.analyzer.ImportExtractor
import page.atlas.analyzer.RawImport
import page.atlas.analyzer.RawRelation
import page.atlas.analyzer.SymbolDecl
import page.atlas.graph.EdgeKind

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
                RawImport("com.example.util", false, wildcard = true),
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
                RawImport("foo.all", false, wildcard = true),
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
    fun `javascript commonjs require calls`() {
        val text = """
            const a = require('./a');
            const b = require('pkg');
            require('./side-effect');
            const ok = helper('not-a-require');
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("app.js"), text)
        assertEquals(
            listOf(
                RawImport("./a", true),
                RawImport("pkg", false),
                RawImport("./side-effect", true),
            ),
            imports,
        )
    }

    @Test
    fun `javascript dynamic import calls including chained`() {
        val text = """
            const m = import('./m');
            import('pkg').then((x) => x);
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("app.mjs"), text)
        assertEquals(
            listOf(
                RawImport("./m", true),
                RawImport("pkg", false),
            ),
            imports,
        )
    }

    @Test
    fun `typescript re-export from forwards target and symbols`() {
        val text = """
            export { a, b } from './ab';
            export * from './all';
            export * as ns from './ns';
            export type { T } from './t';
            export const local = 1;
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("index.ts"), text)
        assertEquals(
            listOf(
                RawImport("./ab", true, listOf("a", "b")),
                RawImport("./all", true),
                RawImport("./ns", true, listOf("ns")),
                RawImport("./t", true, listOf("T")),
            ),
            imports,
        )
    }

    @Test
    fun `vue single-file component extracts imports from script setup and ignores template`() {
        val text = """
            <template>
              <Child :count="n" />
              <p>import fake from 'not-real'</p>
            </template>

            <script setup lang="ts">
            import Child from './Child.vue'
            import { store } from '@/store'
            import { ref } from 'vue'
            const n = ref(0)
            </script>

            <style scoped>
            p { color: red; }
            </style>
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("Parent.vue"), text)
        assertEquals(
            listOf(
                RawImport("./Child.vue", true, listOf("Child")),
                RawImport("@/store", false, listOf("store")),
                RawImport("vue", false, listOf("ref")),
            ),
            imports,
        )
    }

    @Test
    fun `svelte component extracts imports from module and instance script blocks`() {
        val text = """
            <script context="module">
            import { load } from './loader.js'
            </script>

            <script>
            import Widget from './Widget.svelte'
            import { onMount } from 'svelte'
            </script>

            <h1>Hello</h1>
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("Page.svelte"), text)
        assertEquals(
            listOf(
                RawImport("./loader.js", true, listOf("load")),
                RawImport("./Widget.svelte", true, listOf("Widget")),
                RawImport("svelte", false, listOf("onMount")),
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
    fun `c includes distinguish quoted and system`() {
        val text = """
            #include "util/helper.h"
            #include <stdio.h>
            #include "../common.h"
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("main.c"), text)
        assertEquals(
            listOf(
                RawImport("util/helper.h", true),
                RawImport("stdio.h", false),
                RawImport("../common.h", true),
            ),
            imports,
        )
    }

    @Test
    fun `cpp includes and base classes`() {
        val text = """
            #include "widget.h"
            #include <vector>

            class Button : public Widget, private Clickable {};
        """.trimIndent()
        val analysis = ImportExtractor.analyze(Path.of("button.cpp"), text)
        assertEquals(
            listOf(
                RawImport("widget.h", true),
                RawImport("vector", false),
            ),
            analysis.imports,
        )
        assertEquals(
            listOf(
                RawRelation("Widget", EdgeKind.EXTENDS),
                RawRelation("Clickable", EdgeKind.EXTENDS),
            ),
            analysis.relations,
        )
    }

    @Test
    fun `scala imports including selectors wildcard and rename`() {
        val text = """
            package com.example.app

            import scala.collection.mutable.ListBuffer
            import com.example.util.{Helper, Logger}
            import com.example.misc._
            import com.example.alias.{Foo => Bar}
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("Service.scala"), text)
        assertEquals(
            listOf(
                RawImport("scala.collection.mutable.ListBuffer", false, listOf("ListBuffer")),
                RawImport("com.example.util.Helper", false, listOf("Helper")),
                RawImport("com.example.util.Logger", false, listOf("Logger")),
                RawImport("com.example.misc", false, wildcard = true),
                RawImport("com.example.alias.Foo", false, listOf("Bar")),
            ),
            imports,
        )
    }

    @Test
    fun `scala top-level declarations and base types`() {
        val text = """
            package com.example.app

            import foo.Bar

            class Service(val name: String) extends Base with Mixin

            object Service

            trait Repo

            case class User(id: Int)

            val topLevelVal = 1

            def topLevelFun(): Int = 2

            type Ids = List[Int]
        """.trimIndent()
        val analysis = ImportExtractor.analyze(Path.of("Service.scala"), text)
        assertEquals("com.example.app", analysis.declarations.packageName)
        assertEquals(
            listOf("Service", "Service", "Repo", "User", "topLevelVal", "topLevelFun", "Ids"),
            analysis.declarations.symbols,
        )
        assertEquals(
            listOf(
                RawRelation("Base", EdgeKind.EXTENDS),
                RawRelation("Mixin", EdgeKind.IMPLEMENTS),
            ),
            analysis.relations,
        )
    }

    @Test
    fun `ruby require and require_relative distinguish relative`() {
        val text = """
            require 'json'
            require_relative '../lib/helper'
            require_relative 'models/user'
            load 'config.rb'
            puts 'not a require'
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("service.rb"), text)
        assertEquals(
            listOf(
                RawImport("json", false),
                RawImport("../lib/helper", true),
                RawImport("models/user", true),
                RawImport("config.rb", false),
            ),
            imports,
        )
    }

    @Test
    fun `ruby superclass relation`() {
        val text = """
            class Service < Base
              include Comparable
            end
        """.trimIndent()
        val relations = ImportExtractor.analyze(Path.of("service.rb"), text).relations
        assertEquals(listOf(RawRelation("Base", EdgeKind.EXTENDS)), relations)
    }

    @Test
    fun `php use imports including group rename and function`() {
        val text = """
            <?php
            namespace App;

            use App\Support\Helper;
            use App\Support\{Logger, Cache};
            use App\Contracts\Repo as Repository;
            use function App\Support\helper_fn;
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("User.php"), text)
        assertEquals(
            listOf(
                RawImport("App.Support.Helper", false, listOf("Helper")),
                RawImport("App.Support.Logger", false, listOf("Logger")),
                RawImport("App.Support.Cache", false, listOf("Cache")),
                RawImport("App.Contracts.Repo", false, listOf("Repository")),
                RawImport("App.Support.helper_fn", false, listOf("helper_fn")),
            ),
            imports,
        )
    }

    @Test
    fun `php require and include carry file paths`() {
        val text = """
            <?php
            require 'config.php';
            require_once __DIR__ . '/bootstrap.php';
            include 'partial.php';
            include_once "lib/util.php";
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("index.php"), text)
        assertEquals(
            listOf(
                RawImport("config.php", true),
                RawImport("/bootstrap.php", true),
                RawImport("partial.php", true),
                RawImport("lib/util.php", true),
            ),
            imports,
        )
    }

    @Test
    fun `php top-level declarations and base types`() {
        val text = """
            <?php
            namespace App\Models;

            use App\Support\Helper;

            class User extends Model implements Arrayable, JsonSerializable {}

            interface Arrayable extends Countable {}

            trait HasTimestamps {}

            function topLevelFn() {}
        """.trimIndent()
        val analysis = ImportExtractor.analyze(Path.of("User.php"), text)
        assertEquals("App.Models", analysis.declarations.packageName)
        assertEquals(
            listOf("User", "Arrayable", "HasTimestamps", "topLevelFn"),
            analysis.declarations.symbols,
        )
        assertEquals(
            listOf(
                RawRelation("Model", EdgeKind.EXTENDS),
                RawRelation("Arrayable", EdgeKind.IMPLEMENTS),
                RawRelation("JsonSerializable", EdgeKind.IMPLEMENTS),
                RawRelation("Countable", EdgeKind.EXTENDS),
            ),
            analysis.relations,
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
    fun `kotlin declarations carry top-level line numbers`() {
        val text = "package p\n\nclass A\n\nfun b() {}\n\nobject C\n"
        val decls = ImportExtractor.analyze(Path.of("A.kt"), text).declarations
        assertEquals(listOf("A", "b", "C"), decls.symbols)
        assertEquals(
            listOf(SymbolDecl("A", 2), SymbolDecl("b", 4), SymbolDecl("C", 6)),
            decls.locations,
        )
    }

    @Test
    fun `java declarations carry top-level line numbers`() {
        val text = "package m;\n\nclass Point {}\n\ninterface Shape {}\n"
        val decls = ImportExtractor.analyze(Path.of("Point.java"), text).declarations
        assertEquals(
            listOf(SymbolDecl("Point", 2), SymbolDecl("Shape", 4)),
            decls.locations,
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

    @Test
    fun `kotlin call sites attribute callee names to the enclosing top-level declaration`() {
        val text = "package p\n\nfun a() {\n    b()\n    C.d()\n    x.y.z()\n}\n"
        val calls = ImportExtractor.analyze(Path.of("A.kt"), text).calls
        assertEquals(
            listOf(
                CallSite("a", "b", 3),
                CallSite("a", "d", 4),
                CallSite("a", "z", 5),
            ),
            calls,
        )
    }

    @Test
    fun `kotlin calls inside a member are attributed to the top-level type`() {
        val text = "package p\n\nclass K {\n    fun m() {\n        b()\n    }\n}\n"
        val calls = ImportExtractor.analyze(Path.of("K.kt"), text).calls
        assertEquals(listOf(CallSite("K", "b", 4)), calls)
    }

    @Test
    fun `java call sites use the method name and the top-level type as caller`() {
        val text = "package m;\n\nclass T {\n    void m() {\n        helper();\n        a.b.c();\n    }\n}\n"
        val calls = ImportExtractor.analyze(Path.of("T.java"), text).calls
        assertEquals(
            listOf(CallSite("T", "helper", 4), CallSite("T", "c", 5)),
            calls,
        )
    }

    @Test
    fun `languages without call extraction carry no call sites`() {
        val calls = ImportExtractor.analyze(Path.of("app.js"), "function a(){ b() }").calls
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `csharp using directives distinguish namespace static and alias`() {
        val text = """
            global using System.Text;
            using System;
            using System.Collections.Generic;
            using static System.Math;
            using Json = System.Text.Json;
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("Program.cs"), text)
        assertEquals(
            listOf(
                RawImport("System.Text", false, wildcard = true),
                RawImport("System", false, wildcard = true),
                RawImport("System.Collections.Generic", false, wildcard = true),
                RawImport("System.Math", false, listOf("Math")),
                RawImport("System.Text.Json", false, listOf("Json")),
            ),
            imports,
        )
    }

    @Test
    fun `csharp file-scoped namespace declarations and base types`() {
        val text = """
            using System;

            namespace App.Services;

            public partial class OrderService : BaseService, IOrderService { }

            public interface IOrderService { }

            public readonly struct Money { }

            public enum Status { Open, Closed }

            public record Person(string Name);

            public record class Employee(string Id);
        """.trimIndent()
        val analysis = ImportExtractor.analyze(Path.of("OrderService.cs"), text)
        assertEquals("App.Services", analysis.declarations.packageName)
        assertEquals(
            listOf("OrderService", "IOrderService", "Money", "Status", "Person", "Employee"),
            analysis.declarations.symbols,
        )
        assertEquals(
            listOf(
                RawRelation("BaseService", EdgeKind.EXTENDS),
                RawRelation("IOrderService", EdgeKind.EXTENDS),
            ),
            analysis.relations,
        )
    }

    @Test
    fun `swift import directives including submodule and testable`() {
        val text = """
            import Foundation
            import SwiftUI
            @testable import MyApp
            import struct Foundation.Date
            import class UIKit.UIView
        """.trimIndent()
        val imports = ImportExtractor.extract(Path.of("App.swift"), text)
        assertEquals(
            listOf(
                RawImport("Foundation", false),
                RawImport("SwiftUI", false),
                RawImport("MyApp", false),
                RawImport("Foundation.Date", false),
                RawImport("UIKit.UIView", false),
            ),
            imports,
        )
    }

    @Test
    fun `swift declarations and inheritance skip extensions as symbols`() {
        val text = """
            import Foundation

            public final class OrderService: BaseService, OrderProtocol { }

            struct Money: Codable { }

            enum Status { case open, closed }

            protocol OrderProtocol { }

            extension OrderService: Equatable { }
        """.trimIndent()
        val analysis = ImportExtractor.analyze(Path.of("OrderService.swift"), text)
        assertEquals("", analysis.declarations.packageName)
        assertEquals(
            listOf("OrderService", "Money", "Status", "OrderProtocol"),
            analysis.declarations.symbols,
        )
        assertEquals(
            listOf(
                RawRelation("BaseService", EdgeKind.EXTENDS),
                RawRelation("OrderProtocol", EdgeKind.EXTENDS),
                RawRelation("Codable", EdgeKind.EXTENDS),
                RawRelation("Equatable", EdgeKind.EXTENDS),
            ),
            analysis.relations,
        )
    }
}
