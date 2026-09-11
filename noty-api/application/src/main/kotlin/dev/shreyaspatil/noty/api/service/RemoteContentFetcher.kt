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

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse

/**
 * Retrieves remote source documents that a client asks the server to import when
 * previewing a note built from an external link. Kept as a small object so it can
 * be reused across the notes and share controllers.
 */
object RemoteContentFetcher {

    /**
     * Fetches the document at [url] and returns a short summary describing the
     * response, or a failure note if the document could not be retrieved.
     */
    suspend fun fetch(url: String): String = runCatching {
        HttpClient(CIO).use { client ->
            //CWE-918
            //SINK
            val response: HttpResponse = client.get(url)
            "Fetched ${response.status.value} from source document"
        }
    }.getOrElse { "Unable to fetch source document" }
}
