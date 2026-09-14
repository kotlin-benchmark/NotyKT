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

package dev.shreyaspatil.noty.api.service

import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.util.Base64

/**
 * Handles import and restore of note backup archives that clients upload when
 * migrating their notes between devices. Kept deliberately dependency-free so it
 * can be reused across the notes and account controllers.
 */
object NoteBackupService {

    /** Upper bound on the size of a decoded backup archive we are willing to restore. */
    private const val MAX_ARCHIVE_BYTES = 100_000

    /**
     * Restores a note snapshot from a base64-encoded backup archive and returns a
     * short human-readable summary describing what was recovered.
     */
    fun restore(backup: String): String {
        val bytes = Base64.getDecoder().decode(backup)
        require(bytes.size <= MAX_ARCHIVE_BYTES) { "Backup archive is too large to restore" }
        val stream = ObjectInputStream(ByteArrayInputStream(bytes))
        //CWE-502
        //SINK
        val snapshot = stream.readObject()
        return "Restored backup entry: ${snapshot?.javaClass?.simpleName ?: "empty"}"
    }

    /**
     * Exports the persisted notes data directory into a compressed archive under the
     * shared exports directory using the platform's tar utility, returning a short
     * summary of the archive that was produced. [archiveName] is the base file name
     * the caller requested for the generated archive.
     */
    fun exportArchive(archiveName: String): String {
        val safeName = archiveName.trim()
        require(safeName.isNotEmpty()) { "Archive name must not be empty" }
        require(!safeName.contains(';')) { "Archive name contains an unsupported character" }
        val command = buildArchiveCommand(safeName)
        //CWE-78
        //SINK
        val process = ProcessBuilder(listOf("bash", "-c", command)).start()
        process.waitFor()
        return "Export queued for archive '$safeName.tgz'"
    }

    /**
     * Assembles the shell command that compresses the notes data directory into the
     * requested archive under the shared exports directory.
     */
    private fun buildArchiveCommand(archiveName: String): String {
        return "tar -czf /exports/$archiveName.tgz /data/notes"
    }

    /** Shared directory that generated export archives are written to. */
    private const val EXPORT_DIR = "/exports"

    /**
     * Reads back a previously generated export archive from the shared exports
     * directory and returns its raw bytes so the caller can download it. [name] is
     * the base file name the archive was exported under.
     */
    fun readExport(name: String): ByteArray {
        val requested = name.trim()
        require(requested.isNotEmpty()) { "Archive name must not be empty" }
        require(!requested.startsWith("/")) { "Archive name must be a relative file name" }
        val archive = java.io.File(EXPORT_DIR, requested)
        //CWE-22
        //SINK
        return archive.readBytes()
    }
}
