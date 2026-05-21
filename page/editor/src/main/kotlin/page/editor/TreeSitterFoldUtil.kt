package page.editor

internal object TreeSitterFoldUtil {

    fun buildLineStarts(text: String): IntArray {
        val list = ArrayList<Int>(text.length / 32 + 1)
        list.add(0)
        for (i in text.indices) if (text[i] == '\n') list.add(i + 1)
        return list.toIntArray()
    }

    fun lineOf(lineStarts: IntArray, charIdx: Int): Int {
        var lo = 0
        var hi = lineStarts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (lineStarts[mid] <= charIdx) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun buildByteToCharIndex(bytes: ByteArray, text: String): IntArray {
        val map = IntArray(bytes.size + 1)
        var charIdx = 0
        var byteIdx = 0
        while (charIdx < text.length) {
            val cp = text.codePointAt(charIdx)
            val charLen = Character.charCount(cp)
            val byteLen = utf8Length(cp)
            for (b in 0 until byteLen) {
                if (byteIdx + b <= map.lastIndex) map[byteIdx + b] = charIdx
            }
            byteIdx += byteLen
            charIdx += charLen
        }
        for (i in byteIdx..map.lastIndex) map[i] = text.length
        return map
    }

    fun mapByte(map: IntArray, byte: Int, charLen: Int): Int {
        if (byte < 0) return 0
        if (byte >= map.size) return charLen
        return map[byte]
    }

    private fun utf8Length(cp: Int): Int = when {
        cp < 0x80 -> 1
        cp < 0x800 -> 2
        cp < 0x10000 -> 3
        else -> 4
    }

    fun trimToContentEnd(text: String, lineStarts: IntArray, startChar: Int, endExclusive: Int): Int {
        var idx = endExclusive - 1
        while (idx > startChar) {
            while (idx > startChar && text[idx].isWhitespace()) idx--
            if (idx <= startChar) break
            val lineIdx = lineOf(lineStarts, idx)
            val lineStart = lineStarts[lineIdx]
            var content = lineStart
            while (content <= idx && text[content].isWhitespace()) content++
            val isLineComment = content + 1 <= idx &&
                text[content] == '/' && text[content + 1] == '/'
            val isBlockCommentEnd = idx > content && text[idx - 1] == '*' && text[idx] == '/'
            if (isLineComment) {
                idx = lineStart - 1
            } else if (isBlockCommentEnd) {
                var j = idx - 2
                var found = -1
                while (j > startChar) {
                    if (text[j] == '/' && j + 1 <= idx && text[j + 1] == '*') {
                        found = j
                        break
                    }
                    j--
                }
                if (found < 0) break
                idx = found - 1
            } else {
                break
            }
        }
        return idx
    }

    fun hasTrailingContentAfter(text: String, lineStarts: IntArray, lastIdx: Int, lastLine: Int): Boolean {
        val lineEndExclusive = if (lastLine + 1 < lineStarts.size) lineStarts[lastLine + 1] - 1 else text.length
        var i = lastIdx + 1
        while (i < lineEndExclusive) {
            if (!text[i].isWhitespace()) return true
            i++
        }
        return false
    }
}
