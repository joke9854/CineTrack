package com.cinetrack.ui.personal

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cinetrack.data.personal.CustomList
import com.cinetrack.data.personal.PersonalRepository
import com.cinetrack.data.personal.UserRating
import com.cinetrack.domain.MediaCard
import com.cinetrack.ui.viewModelFactoryOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ratings, notes and your own lists, for the Detail page, the Lists pages and Profile. */
class PersonalViewModel(
    private val repository: PersonalRepository,
    private val importer: com.cinetrack.data.importer.ExternalImporter? = null,
) : ViewModel() {
    /** Null when idle; progress while running; the summary or error once done. */
    sealed interface ImportState {
        data class Running(val done: Int, val total: Int) : ImportState
        data class Done(val summary: com.cinetrack.data.importer.ImportSummary) : ImportState
        data class Failed(val message: String?) : ImportState
    }
    private val _importState = kotlinx.coroutines.flow.MutableStateFlow<ImportState?>(null)
    val importState: StateFlow<ImportState?> = _importState

    fun importFile(context: android.content.Context, uri: android.net.Uri) {
        val importer = importer ?: return
        if (_importState.value is ImportState.Running) return
        val appContext = context.applicationContext
        _importState.value = ImportState.Running(0, 0)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _importState.value = runCatching {
                val files = importer.read(appContext, uri)
                ImportState.Done(importer.import(files) { done, total -> _importState.value = ImportState.Running(done, total) })
            }.getOrElse { ImportState.Failed(it.message) }
        }
    }

    fun clearImport() {
        if (_importState.value !is ImportState.Running) _importState.value = null
    }

    val ratings: StateFlow<Map<String, UserRating>> =
        repository.ratings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val lists: StateFlow<List<CustomList>> =
        repository.lists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val memberships: StateFlow<Map<String, Set<Long>>> =
        repository.memberships.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun listMedia(listId: Long): Flow<List<MediaCard>> = repository.listMedia(listId)

    fun setRating(media: MediaCard, score: Int?, note: String) {
        viewModelScope.launch { repository.setRating(media, score, note) }
    }

    /** Creates a list, and puts [media] in it when the list was made from a title's page. */
    fun createList(name: String, media: MediaCard? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.createList(name)
            if (media != null) repository.setInList(id, media, true)
        }
    }

    fun renameList(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renameList(id, name) }
    }

    fun deleteList(id: Long) {
        viewModelScope.launch { repository.deleteList(id) }
    }

    fun setInList(listId: Long, media: MediaCard, inList: Boolean) {
        viewModelScope.launch { repository.setInList(listId, media, inList) }
    }

    class Factory(
        repository: PersonalRepository,
        importer: com.cinetrack.data.importer.ExternalImporter? = null,
    ) : ViewModelProvider.Factory by viewModelFactoryOf({
        PersonalViewModel(repository, importer)
    })
}

/** Provided once by the app, so the Detail sections don't thread it through every call. */
val LocalPersonal = staticCompositionLocalOf<PersonalViewModel?> { null }
