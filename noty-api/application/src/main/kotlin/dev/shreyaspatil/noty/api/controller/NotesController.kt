/*
 * Copyright 2020 Shreyas Patil
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.shreyaspatil.noty.api.controller

import dev.shreyaspatil.noty.api.exception.BadRequestException
import dev.shreyaspatil.noty.api.exception.FailureMessages
import dev.shreyaspatil.noty.api.exception.ResourceNotFoundException
import dev.shreyaspatil.noty.api.exception.UnauthorizedAccessException
import dev.shreyaspatil.noty.api.model.request.NoteRequest
import dev.shreyaspatil.noty.api.model.response.Note
import dev.shreyaspatil.noty.api.model.response.NoteTaskResponse
import dev.shreyaspatil.noty.api.model.response.NotesResponse
import dev.shreyaspatil.noty.api.utils.toShareHtml
import dev.shreyaspatil.noty.data.dao.NoteDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controller for notes management
 */
@Singleton
class NotesController @Inject constructor(private val noteDao: NoteDao) {

    /**
     * Get all notes for a specific user
     */
    fun getNotesByUser(userId: String): NotesResponse {
        val notes = noteDao.getAllByUser(userId)

        return NotesResponse(
            notes.map {
                Note(
                    id = it.id,
                    title = it.title,
                    note = it.note,
                    created = it.created,
                    isPinned = it.isPinned,
                )
            },
        )
    }

    /**
     * Renders a note body as a standalone shareable HTML page for public preview links.
     */
    fun renderSharedNote(content: String): String {
        val body = content.replace("<script>", "")
        return body.toShareHtml()
    }

    /**
     * Add a new note
     */
    fun addNote(userId: String, note: NoteRequest): NoteTaskResponse {
        val noteTitle = note.title.trim()
        val noteText = note.note.trim()

        return withValidatedNote(noteTitle, noteText) {
            val noteId = noteDao.add(userId = userId, title = noteTitle, note = noteText)
            NoteTaskResponse(noteId)
        }
    }

    /**
     * Update an existing note
     */
    fun updateNote(userId: String, noteId: String, note: NoteRequest): NoteTaskResponse {
        val noteTitle = note.title.trim()
        val noteText = note.note.trim()

        return withValidatedNote(noteTitle, noteText) {
            withExistingNote(noteId) {
                withAuthorizedUser(userId, noteId) {
                    val id = noteDao.update(noteId, noteTitle, noteText)
                    NoteTaskResponse(id)
                }
            }
        }
    }

    /**
     * Delete a note
     */
    fun deleteNote(userId: String, noteId: String): NoteTaskResponse {
        return withExistingNote(noteId) {
            withAuthorizedUser(userId, noteId) {
                if (!noteDao.deleteById(noteId)) {
                    error("Error occurred while deleting a note")
                }
                NoteTaskResponse(noteId)
            }
        }
    }

    /**
     * Update pin status of a note
     */
    fun pinNote(userId: String, noteId: String): NoteTaskResponse {
        return updateNotePin(userId, noteId, true)
    }

    fun unpinNote(userId: String, noteId: String): NoteTaskResponse {
        return updateNotePin(userId, noteId, false)
    }

    private fun updateNotePin(userId: String, noteId: String, isPinned: Boolean): NoteTaskResponse {
        return withExistingNote(noteId) {
            return@withExistingNote withAuthorizedUser(userId, noteId) {
                val id = noteDao.updateNotePinById(id = noteId, isPinned = isPinned)
                return@withAuthorizedUser NoteTaskResponse(id)
            }
        }
    }

    /**
     * Higher-order function to validate note content
     */
    private fun <T> withValidatedNote(title: String, note: String, block: () -> T): T {
        validateNoteOrThrowException(title, note)
        return block()
    }

    /**
     * Higher-order function to check if note exists
     */
    private fun <T> withExistingNote(noteId: String, block: () -> T): T {
        val exists = runCatching { noteDao.exists(noteId) }.getOrDefault(false)
        if (!exists) {
            throw ResourceNotFoundException("Note not exist with ID '$noteId'")
        }
        return block()
    }

    /**
     * Higher-order function to check if user is authorized to access the note
     */
    private fun <T> withAuthorizedUser(userId: String, noteId: String, block: () -> T): T {
        if (!noteDao.isNoteOwnedByUser(noteId, userId)) {
            throw UnauthorizedAccessException(FailureMessages.MESSAGE_ACCESS_DENIED)
        }
        return block()
    }

    /**
     * Validate note content or throw exception
     */
    private fun validateNoteOrThrowException(title: String, note: String) {
        val message = when {
            (title.isBlank() or note.isBlank()) -> "Title and Note should not be blank"
            (title.length !in (4..30)) -> "Title should be of min 4 and max 30 character in length"
            else -> return
        }

        throw BadRequestException(message)
    }

    /**
     * Searches the user's notes, returning those whose title matches the supplied expression.
     */
    fun searchNotesByUser(userId: String, titleFilter: String): NotesResponse {
        val response = getNotesByUser(userId)
        val matched = matchNotesByTitle(response.notes, titleFilter)
        return NotesResponse(matched)
    }

    /**
     * Narrows a list of notes to those whose title matches the given expression.
     */
    private fun matchNotesByTitle(notes: List<Note>, titleFilter: String): List<Note> {
        if (titleFilter.isEmpty()) {
            return notes
        }
        require(titleFilter.length <= 200) { "Search expression is too long" }
        val matcher = Regex(titleFilter)
        //CWE-1333
        //SINK
        return notes.filter { matcher.containsMatchIn(it.title) }
    }

    /**
     * Restores a note from a client-supplied backup archive produced by an earlier export.
     */
    fun restoreBackup(backup: String): NoteTaskResponse {
        val summary = dev.shreyaspatil.noty.api.service.NoteBackupService.restore(backup)
        return NoteTaskResponse(message = summary)
    }

    /**
     * Builds a preview of a note whose body is imported from an external source
     * document. The document is fetched on demand so the client can review the
     * imported content before saving it as a note.
     */
    suspend fun previewNoteFromUrl(sourceUrl: String): NoteTaskResponse {
        val url = sourceUrl.trim()
        require(url.startsWith("http")) { "Source URL must be an HTTP address" }
        val summary = dev.shreyaspatil.noty.api.service.RemoteContentFetcher.fetch(url)
        return NoteTaskResponse(message = summary)
    }

    /**
     * Exports the caller's notes into a compressed archive under the server's shared
     * exports directory so the client can download a full backup of their notes. The
     * caller chooses the base file name used for the generated archive.
     */
    fun exportArchive(archiveName: String): NoteTaskResponse {
        val summary = dev.shreyaspatil.noty.api.service.NoteBackupService.exportArchive(archiveName)
        return NoteTaskResponse(message = summary)
    }

    /**
     * Reads back a previously generated export archive from the server's shared
     * exports directory so the client can download a copy of a backup it created
     * earlier. The archive is identified by the base file name used at export time.
     */
    fun readExport(name: String): NoteTaskResponse {
        val bytes = dev.shreyaspatil.noty.api.service.NoteBackupService.readExport(name)
        return NoteTaskResponse(message = "Prepared ${bytes.size} bytes for export '$name'")
    }
}
