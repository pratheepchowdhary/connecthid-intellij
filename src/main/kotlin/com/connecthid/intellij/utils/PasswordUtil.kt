package com.connecthid.intellij.utils

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe

object PasswordUtil {
    private const val SERVICE_NAME = "com.connecthid.intellij"

    /**
     * Stores the password securely in Password Safe.
     * @param key A unique key for the password entry.
     * @param password The password to be stored.
     */
    fun storePassword(key: String, password: String) {
        val attributes = CredentialAttributes("$SERVICE_NAME:$key")
        PasswordSafe.instance.set(attributes, Credentials(key, password))
    }

    /**
     * Retrieves the password from Password Safe.
     * @param key The unique key for the password entry.
     * @return The password as a String, or null if not found.
     */
    fun getPassword(key: String): String? {
        val attributes = CredentialAttributes("$SERVICE_NAME:$key")
        val credentials = PasswordSafe.instance.get(attributes)
        return credentials?.getPasswordAsString()
    }

    /**
     * Deletes the password entry from Password Safe.
     * @param key The unique key for the password entry.
     */
    fun deletePassword(key: String) {
        val attributes = CredentialAttributes("$SERVICE_NAME:$key")
        PasswordSafe.instance.set(attributes, null)
    }
}