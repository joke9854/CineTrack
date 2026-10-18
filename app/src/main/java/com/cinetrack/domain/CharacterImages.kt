package com.cinetrack.domain

import java.text.Normalizer

/** A character picture from TVDB (through Floppy): [character] is played by [actor]. */
data class CharacterImage(val character: String, val actor: String, val imageUrl: String?)

private val GENERIC_ROLES = setOf("himself", "herself", "self", "themselves", "narrator", "voice", "host")

/**
 * Adds TVDB character pictures to the TMDB cast. TMDB stays the cast list; a
 * picture is matched by actor name (preferring the same character), otherwise by
 * a character name only one TVDB character has.
 */
fun List<PersonCard>.withCharacterImages(characters: List<CharacterImage>): List<PersonCard> {
    val pictured = characters.filter { !it.imageUrl.isNullOrBlank() }
    if (pictured.isEmpty()) return this
    val byActor = pictured.groupBy { nameKey(it.actor) }
    val byCharacter = pictured.groupBy { nameKey(it.character) }
        .filter { (key, matches) -> key.isNotEmpty() && key !in GENERIC_ROLES && matches.size == 1 }
        .mapValues { it.value.single() }
    return map { person ->
        if (!person.isCastMember) return@map person
        val roles = person.role.split(" · ").map { nameKey(it.replace(Regex("\\(.*?\\)"), "")) }.filter(String::isNotEmpty)
        val sameActor = byActor[nameKey(person.name)].orEmpty()
        val match = sameActor.firstOrNull { nameKey(it.character) in roles }
            ?: sameActor.firstOrNull()
            ?: roles.firstNotNullOfOrNull { byCharacter[it] }
        match?.let { person.copy(characterImageUrl = it.imageUrl) } ?: person
    }
}

private fun nameKey(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .filter(Char::isLetterOrDigit)
