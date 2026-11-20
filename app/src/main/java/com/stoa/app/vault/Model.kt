package com.stoa.app.vault

data class Task(
    val path: String, val line: Int, val raw: String, val section: String, val text: String,
    val status: String, val due: String?, val completion: String?, val prio: Int, val tags: List<String>,
    val file: String, val recurring: Boolean
) { val id get() = "$path:$line" }

data class WSet(val kg: String, val reps: String)
data class WEx(val name: String, val sets: List<WSet>)
data class Workout(val name: String, val meta: String, val ex: List<WEx>)

data class Day(
    val done: Set<String> = emptySet(), val present: Set<String> = emptySet(),
    val morning: Set<String> = emptySet(), val evening: Set<String> = emptySet(), val complete: Set<String> = emptySet(),
    val readMin: Int = 0, val medMin: Int = 0, val workouts: List<Workout> = emptyList()
)

data class Area(
    val id: String, val label: String, val icon: String, val habits: Map<String, Int>,
    val notesTarget: Int = 0, val notesUnless: String? = null, val tag: String? = null, val minTasks: Int = 0
)

data class RoutineItem(val emoji: String, val text: String) { val label get() = if (emoji.isNotEmpty()) "$emoji $text" else text }
