package com.cinetrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CharacterImagesTest {
    private fun actor(id: Int, name: String, role: String, castMember: Boolean = true) =
        PersonCard(id = id, name = name, role = role, profileUrl = "tmdb/$id.jpg", isCastMember = castMember)

    @Test
    fun matchesByActorNameIgnoringAccentsAndCase() {
        val cast = listOf(actor(1, "Penélope Cruz", "Raimunda"))
        val result = cast.withCharacterImages(listOf(CharacterImage("Raimunda", "PENELOPE CRUZ", "tvdb/raimunda.jpg")))

        assertEquals("tvdb/raimunda.jpg", result.single().characterImageUrl)
        assertEquals("tmdb/1.jpg", result.single().profileUrl)
    }

    @Test
    fun actorWithSeveralRolesGetsTheMatchingCharacter() {
        val cast = listOf(actor(1, "Tatiana Maslany", "Sarah Manning · Cosima Niehaus"))
        val result = cast.withCharacterImages(listOf(
            CharacterImage("Helena", "Tatiana Maslany", "tvdb/helena.jpg"),
            CharacterImage("Sarah Manning", "Tatiana Maslany", "tvdb/sarah.jpg"),
        ))

        assertEquals("tvdb/sarah.jpg", result.single().characterImageUrl)
    }

    @Test
    fun fallsBackToAUniqueCharacterNameAndStripsVoiceNotes() {
        val cast = listOf(actor(1, "Dan Castellaneta", "Homer Simpson (voice)"))
        val result = cast.withCharacterImages(listOf(CharacterImage("Homer Simpson", "Dan Castellanetta", "tvdb/homer.jpg")))

        assertEquals("tvdb/homer.jpg", result.single().characterImageUrl)
    }

    @Test
    fun genericAndSharedCharacterNamesNeverMatchAnotherActor() {
        val cast = listOf(actor(1, "Someone", "Himself"), actor(2, "Other", "Guard"))
        val result = cast.withCharacterImages(listOf(
            CharacterImage("Himself", "Somebody Else", "tvdb/self.jpg"),
            CharacterImage("Guard", "Guard One", "tvdb/g1.jpg"),
            CharacterImage("Guard", "Guard Two", "tvdb/g2.jpg"),
        ))

        assertNull(result[0].characterImageUrl)
        assertNull(result[1].characterImageUrl)
    }

    @Test
    fun crewAndMissingPicturesAreLeftAlone() {
        val cast = listOf(actor(1, "Vince Gilligan", "Creator", castMember = false), actor(2, "Aaron Paul", "Jesse Pinkman"))
        val result = cast.withCharacterImages(listOf(
            CharacterImage("Creator", "Vince Gilligan", "tvdb/vince.jpg"),
            CharacterImage("Jesse Pinkman", "Aaron Paul", null),
        ))

        assertNull(result[0].characterImageUrl)
        assertNull(result[1].characterImageUrl)
    }

    @Test
    fun noPicturesKeepsTheTmdbCastUnchanged() {
        val cast = listOf(actor(1, "Aaron Paul", "Jesse Pinkman"))

        assertSame(cast, cast.withCharacterImages(emptyList()))
    }
}
