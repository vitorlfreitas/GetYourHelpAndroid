package com.vitorfreitas.getyourhelp.validation

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Checks a chat message before it is sent. The same limit is in firestore.rules. */
object ChatValidator {
    const val MESSAGE_MAX = 1000

    /** Why [text] cannot be sent, or null if it can. Spaces around the message are ignored. */
    fun message(text: String): String? = when {
        text.isBlank() -> "Write a message first."
        text.trim().length > MESSAGE_MAX -> "Messages can have at most $MESSAGE_MAX characters."
        else -> null
    }
}
