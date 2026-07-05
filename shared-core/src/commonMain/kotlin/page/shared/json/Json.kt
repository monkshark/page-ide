package page.shared.json

sealed interface JsonValue

data class JsonObject(val entries: Map<String, JsonValue>) : JsonValue {
    operator fun get(key: String): JsonValue? = entries[key]
}

data class JsonArray(val items: List<JsonValue>) : JsonValue

data class JsonString(val value: String) : JsonValue

data class JsonNumber(val value: Double) : JsonValue

data class JsonBool(val value: Boolean) : JsonValue

data object JsonNull : JsonValue

fun JsonValue?.asString(): String? = (this as? JsonString)?.value
fun JsonValue?.asArray(): List<JsonValue> = (this as? JsonArray)?.items ?: emptyList()
fun JsonValue?.asObject(): JsonObject? = this as? JsonObject

object Json {

    fun parse(text: String): JsonValue {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (!p.atEnd()) p.fail("trailing content")
        return v
    }

    private class Parser(val src: String) {
        var pos = 0

        fun atEnd(): Boolean = pos >= src.length

        fun fail(msg: String): Nothing = throw IllegalArgumentException("JSON parse error at $pos: $msg")

        fun skipWs() {
            while (pos < src.length && src[pos].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) pos++
        }

        fun value(): JsonValue {
            if (atEnd()) fail("unexpected end")
            return when (val c = src[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> JsonString(string())
                't', 'f' -> bool()
                'n' -> { literal("null"); JsonNull }
                else -> if (c == '-' || c in '0'..'9') number() else fail("unexpected '$c'")
            }
        }

        fun obj(): JsonObject {
            expect('{')
            val map = LinkedHashMap<String, JsonValue>()
            skipWs()
            if (peek() == '}') { pos++; return JsonObject(map) }
            while (true) {
                skipWs()
                val key = string()
                skipWs()
                expect(':')
                skipWs()
                map[key] = value()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; return JsonObject(map) }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        fun arr(): JsonArray {
            expect('[')
            val items = ArrayList<JsonValue>()
            skipWs()
            if (peek() == ']') { pos++; return JsonArray(items) }
            while (true) {
                skipWs()
                items += value()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; return JsonArray(items) }
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        fun string(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) fail("unterminated string")
                val c = src[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) fail("bad escape")
                        when (val e = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (pos + 4 > src.length) fail("bad unicode escape")
                                val code = src.substring(pos, pos + 4).toInt(16)
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> fail("bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun number(): JsonNumber {
            val start = pos
            if (peek() == '-') pos++
            while (pos < src.length && src[pos].let { it in '0'..'9' || it == '.' || it == 'e' || it == 'E' || it == '+' || it == '-' }) pos++
            val raw = src.substring(start, pos)
            return JsonNumber(raw.toDoubleOrNull() ?: fail("bad number '$raw'"))
        }

        fun bool(): JsonBool =
            if (peek() == 't') { literal("true"); JsonBool(true) } else { literal("false"); JsonBool(false) }

        fun literal(word: String) {
            if (pos + word.length > src.length || src.substring(pos, pos + word.length) != word) fail("expected '$word'")
            pos += word.length
        }

        fun peek(): Char = if (atEnd()) fail("unexpected end") else src[pos]

        fun expect(c: Char) {
            if (peek() != c) fail("expected '$c'")
            pos++
        }
    }
}
