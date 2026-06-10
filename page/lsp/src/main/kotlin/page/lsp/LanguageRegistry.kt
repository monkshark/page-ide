package page.lsp

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object LanguageRegistry {
    private const val RESOURCE_PATH = "/page/lsp/languages.json"

    @Volatile
    private var cached: List<LanguageDefinition>? = null

    fun all(): List<LanguageDefinition> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val loaded = loadFromResource(RESOURCE_PATH)
            cached = loaded
            return loaded
        }
    }

    fun byId(id: String): LanguageDefinition? =
        all().firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun byExtension(extension: String?): LanguageDefinition? {
        if (extension == null) return null
        return all().firstOrNull { it.supports(extension) }
    }

    fun loadFromResource(path: String): List<LanguageDefinition> {
        val stream = LanguageRegistry::class.java.getResourceAsStream(path)
            ?: error("language resource not found: $path")
        return stream.use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                parse(Gson().fromJson(reader, JsonObject::class.java))
            }
        }
    }

    fun parse(json: JsonObject): List<LanguageDefinition> {
        val array = json.getAsJsonArray("languages")
            ?: error("languages array missing")
        val out = mutableListOf<LanguageDefinition>()
        for (element in array) {
            val obj = element.asJsonObject
            out += LanguageDefinition(
                id = obj.get("id").asString,
                displayName = obj.get("displayName").asString,
                extensions = obj.getAsJsonArray("extensions").map { it.asString },
                lspBinaries = obj.getAsJsonArray("lspBinaries").map { it.asString },
                lspWindowsBinaries = obj.getAsJsonArray("lspWindowsBinaries")?.map { it.asString } ?: emptyList(),
                installGuideUrl = obj.get("installGuideUrl").asString,
                install = obj.getAsJsonObject("install").entrySet().associate { (k, v) -> k to v.asString },
                runCommand = obj.get("runCommand").takeIf { !it.isJsonNull }?.asString,
                launchArgs = obj.getAsJsonArray("launchArgs")?.map { it.asString } ?: listOf("--stdio"),
                lspLanguageId = obj.get("lspLanguageId")?.takeIf { !it.isJsonNull }?.asString ?: obj.get("id").asString,
            )
        }
        return out
    }
}
