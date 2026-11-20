package com.stoa.app.vault

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver

/** YAML 1.1 resolver without timestamps, so "2026-11-19" stays a plain string (as Obsidian expects). */
private class NoDateResolver : Resolver() {
    override fun addImplicitResolvers() {
        addImplicitResolver(Tag.BOOL, Resolver.BOOL, "yYnNtTfFoO")
        addImplicitResolver(Tag.INT, Resolver.INT, "-+0123456789")
        addImplicitResolver(Tag.FLOAT, Resolver.FLOAT, "-+0123456789.")
        addImplicitResolver(Tag.MERGE, Resolver.MERGE, "<")
        addImplicitResolver(Tag.NULL, Resolver.NULL, "~nN\u0000")
        addImplicitResolver(Tag.NULL, Resolver.EMPTY, null)
    }
}

object Frontmatter {
    private fun yaml(): Yaml {
        val dump = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            indent = 2
            indicatorIndent = 2
            setIndentWithIndicator(true)
            width = 4096
            isAllowUnicode = true
            isPrettyFlow = false
        }
        val load = LoaderOptions()
        return Yaml(SafeConstructor(load), Representer(dump), dump, load, NoDateResolver())
    }

    private val FM = Regex("^---\\r?\\n([\\s\\S]*?)\\r?\\n---[ \\t]*(\\r?\\n|$)")

    data class Split(val fm: MutableMap<String, Any?>, val body: String, val hadFm: Boolean)

    fun split(text: String): Split {
        val m = FM.find(text)
        if (m == null || m.range.first != 0) return Split(LinkedHashMap(), text, false)
        val map = parse(m.groupValues[1])
        return Split(map, text.substring(m.range.last + 1), true)
    }

    fun parse(src: String): MutableMap<String, Any?> = try {
        @Suppress("UNCHECKED_CAST")
        val v = yaml().load<Any?>(src)
        if (v is Map<*, *>) LinkedHashMap(v.entries.associate { it.key.toString() to it.value }) else LinkedHashMap()
    } catch (e: Exception) { LinkedHashMap() }

    fun dump(map: Map<String, Any?>): String = yaml().dump(map)

    fun join(fm: Map<String, Any?>, body: String): String {
        if (fm.isEmpty()) return body
        val b = body.trimStart('\n', '\r')
        return "---\n" + dump(fm).trimEnd('\n') + "\n---\n" + b
    }

    /** Read-modify-write of a note's properties. */
    fun update(text: String, fn: (MutableMap<String, Any?>) -> Unit): String {
        val s = split(text)
        fn(s.fm)
        return join(s.fm, s.body)
    }
}

/* tiny value helpers for loosely-typed YAML */
fun Any?.asList(): List<String> = when (this) {
    null -> emptyList()
    is List<*> -> this.filterNotNull().map { it.toString() }
    else -> listOf(this.toString())
}
fun Any?.asMap(): Map<String, Any?> = if (this is Map<*, *>) this.entries.associate { it.key.toString() to it.value } else emptyMap()
fun Any?.asInt(): Int? = when (this) { is Number -> this.toInt(); is String -> this.trim().toDoubleOrNull()?.toInt(); else -> null }
fun Any?.asDouble(): Double? = when (this) { is Number -> this.toDouble(); is String -> this.trim().toDoubleOrNull(); else -> null }
