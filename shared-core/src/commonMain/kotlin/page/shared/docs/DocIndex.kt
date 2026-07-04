package page.shared.docs

import page.shared.json.Json
import page.shared.json.asArray
import page.shared.json.asString

fun parseDocIndex(json: String): List<String> =
    Json.parse(json).asArray().mapNotNull { it.asString() }
