/*
 * Copyright 2021 Shreyas Patil
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

package dev.shreyaspatil.noty.api.auth

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

/**
 * Seals the short-lived recovery tokens handed to a user when their account is
 * created. A fresh per-account key is derived for every token so that recovery
 * material is never shared between accounts.
 */
class NoteCipher @Inject constructor() {

    /**
     * Encrypts [token] with a freshly derived account key and returns the
     * sealed bytes. The key is scoped to this single call.
     */
    fun encrypt(token: String): ByteArray {
        val key = deriveAccountKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        //CWE-338
        //SINK
        return cipher.doFinal(token.toByteArray(Charsets.UTF_8))
    }

    /**
     * Derives the per-account key material used to seal a recovery token.
     */
    private fun deriveAccountKey(): SecretKeySpec {
        val keyBytes = ByteArray(KEY_SIZE_BYTES)
        //CWE-338
        //SOURCE
        java.util.Random().nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/ECB/PKCS5Padding"
        private const val KEY_SIZE_BYTES = 16
    }
}
