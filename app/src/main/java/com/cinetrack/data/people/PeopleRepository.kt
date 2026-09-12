package com.cinetrack.data.people

import com.cinetrack.data.media.MediaRepository
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.TimelineCard

interface PeopleRepository {
    suspend fun search(query: String): List<PersonCard>
    suspend fun getPerson(person: PersonCard): PersonCard
    suspend fun viewingPeople(history: List<TimelineCard>): Pair<List<PersonCard>, List<PersonCard>>
}

class DefaultPeopleRepository(private val media: MediaRepository) : PeopleRepository {
    override suspend fun search(query: String) = media.searchPeople(query)
    override suspend fun getPerson(person: PersonCard) = media.loadPerson(person)
    override suspend fun viewingPeople(history: List<TimelineCard>) = media.loadViewingPeople(history)
}

