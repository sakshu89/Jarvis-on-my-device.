package com.jarvis.agent

/** Cheap local pre-processing before the LLM: wake word, STOP / yes / no, script detection. */
object IntentProcessor {
    data class Processed(
        val clean: String,
        val stop: Boolean,
        val yes: Boolean,
        val no: Boolean,
        val devanagari: Boolean,
        val shutdown: Boolean = false
    )

    private val wake = Regex("^\\s*(hey\\s+|ok\\s+|okay\\s+)?(jarvis|jarvish|जार्विस|जार्विश)[,.!:\\s]*", RegexOption.IGNORE_CASE)

    private val stopPhrases = setOf(
        "stop", "cancel", "stop it", "stop everything", "cancel karo", "ruko", "ruk", "ruk jao", "ruko jarvis",
        "band karo", "bas", "रुको", "रुक", "रुक जाओ", "बंद करो", "बस", "कैंसल"
    )
    private val yesPhrases = setOf(
        "yes", "yeah", "yep", "yup", "ok", "okay", "send", "send it", "sure", "haan", "han", "ha", "haa", "ji",
        "bhej do", "bhejo", "kar do", "karo", "theek hai", "ठीक है", "हाँ", "हां", "भेज दो", "भेजो", "कर दो"
    )
    private val noPhrases = setOf(
        "no", "nope", "nah", "don't send", "dont send", "nahi", "nahin", "mat bhejo", "mat", "नहीं", "मत भेजो", "मत"
    )

    private val shutdownRx = Regex(
        "^(jarvis\\s+)?(shut\\s?down|power\\s?off|turn\\s?off|switch\\s?off|go\\s+offline|go\\s+to\\s+sleep|" +
            "band\\s+ho\\s+ja(o|a)?|shutdown\\s+kar(o|\\s+do)?|power\\s+off\\s+kar(o|\\s+do)?|" +
            "शटडाउन(\\s+करो)?|बंद\\s+हो\\s+जा(ओ)?)(\\s+(jarvis|now|please))*$",
        RegexOption.IGNORE_CASE
    )

    fun process(raw: String): Processed {
        val noWake = raw.replace(wake, "").trim()
        val norm = noWake.lowercase().trim().trim('.', '!', '?', ',', '।')
        val words = norm.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val stop = norm in stopPhrases || raw.lowercase().trim().trim('.', '!', '?') == "jarvis stop" ||
            (words.size <= 2 && words.any { it in setOf("stop", "ruko", "रुको") })
        return Processed(
            clean = noWake,
            stop = stop,
            yes = norm in yesPhrases,
            no = norm in noPhrases,
            devanagari = raw.any { it in 'ऀ'..'ॿ' },
            shutdown = shutdownRx.containsMatchIn(norm)
        )
    }
}
