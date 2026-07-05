package page.shared.path

class FilePath private constructor(val value: String) {

    val fileName: String
        get() {
            val i = value.lastIndexOf('/')
            return if (i < 0) value else value.substring(i + 1)
        }

    val parent: FilePath?
        get() {
            val i = value.lastIndexOf('/')
            if (i < 0) return null
            if (i == 0) return if (value == "/") null else FilePath("/")
            return FilePath(value.substring(0, i))
        }

    val segments: List<String>
        get() = value.split('/').filter { it.isNotEmpty() }

    fun startsWith(other: FilePath): Boolean {
        val a = value
        val b = other.value
        if (b.isEmpty()) return true
        if (!a.startsWith(b)) return false
        return a.length == b.length || b.endsWith("/") || a[b.length] == '/'
    }

    fun relativize(other: FilePath): FilePath {
        if (other.value == value) return FilePath("")
        val prefix = if (value.isEmpty() || value.endsWith("/")) value else "$value/"
        val rest = if (other.value.startsWith(prefix)) other.value.substring(prefix.length) else other.value
        return FilePath(rest)
    }

    fun resolve(segment: String): FilePath = of(if (value.isEmpty()) segment else "$value/$segment")

    override fun equals(other: Any?): Boolean = other is FilePath && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        fun of(raw: String): FilePath {
            var s = raw.replace('\\', '/')
            while (s.contains("//")) s = s.replace("//", "/")
            if (s.length > 1 && s.endsWith("/")) s = s.dropLast(1)
            return FilePath(s)
        }
    }
}
