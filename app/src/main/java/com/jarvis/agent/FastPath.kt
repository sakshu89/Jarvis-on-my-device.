package com.jarvis.agent

import org.json.JSONObject

/**
 * Instant, offline recognition of clear one-step commands (English / Hinglish / Hindi).
 * Skips the LLM entirely: faster, works with tiny on-device models, and cannot hallucinate a wrong tool.
 * Anything ambiguous or multi-step (contains "and", "then", "aur"...) returns null and goes to the LLM.
 */
object FastPath {
    data class Match(val tool: String, val args: JSONObject)

    private val multi = Regex("\\b(and|then|aur|phir|fir|after|और|फिर)\\b", RegexOption.IGNORE_CASE)
    private val I = RegexOption.IGNORE_CASE
    private val YTAPP = "(youtube music|yt music|youtube|yt|यूट्यूब)"
    private val ON = "(?:on|in|pe|par|me|mein|पर|में)"
    private val PLAYV = "(?:chalao|chala do|chala de|bajao|baja do|play karo|play kar do|चलाओ|चला दो|बजाओ|बजा दो)"

    private val timeQ = Regex("\\b(what('?s| is)? the time|what time|time (kya|batao|bata)|kitne baje|abhi (kya )?time|current time|samay|कितने बजे|टाइम क्या|समय)\\b", I)
    private val dateQ = Regex("\\b(what('?s| is)? (the )?date|today'?s date|aaj (ki )?(date|tarikh)|kaun sa din|तारीख|आज की तारीख)\\b", I)

    private val playA = Regex("^(?:play|put on)\\s+(.+?)(?:\\s+$ON\\s+$YTAPP)?$", I)
    private val playB = Regex("^$YTAPP\\s+$ON\\s+(.+?)\\s+$PLAYV$", I)
    private val playC = Regex("^(.+?)\\s+$YTAPP\\s+$ON\\s+$PLAYV$", I)
    private val playD = Regex("^(.+?)\\s+$PLAYV$", I)
    private val playE = Regex("^$PLAYV\\s+(.+)$", I)

    private val navA = Regex("^(?:navigate|navigation|take me|drive me|directions?|route)\\s+(?:to\\s+)?(.+)$", I)
    private val navB = Regex("^(.+?)\\s+(?:navigate karo|navigation shuru karo|navigate kar do|ka rasta batao|ka route batao|le chalo|tak ka rasta batao|ka rasta dikhao)$", I)

    private val openA = Regex("^(?:open|launch|start|kholo|khol do|खोलो)\\s+(.+)$", I)
    private val openB = Regex("^(.+?)\\s+(?:kholo|khol do|khol de|open karo|open kar do|खोलो|खोल दो)$", I)

    private val openPlayYt = Regex("^open (?:youtube|yt)(?: music)?\\s+and\\s+play\\s+(.+)$", I)
    private val alarmA = Regex("^(?:can you |please )*set (?:an? )?alarm (?:for|at|of)?\\s*(?:today |tomorrow )?(\\d{1,2})(?::(\\d{2}))?\\s*([ap])\\.?m\\.?(?:\\s+today)?$", I)

    private val APPS = "(whats ?app|telegram|instagram|insta|snapchat|snap)"
    private val SAY = "(?:saying|say|that|:)"
    private val msgC = Regex("^(?:on|in)\\s+$APPS\\s+(?:send (?:a |an )?(?:message|msg|text)\\s+to|message|msg|text)\\s+(.+?)\\s+$SAY\\s*(.+)$", I)
    private val msgA = Regex("^(?:send (?:a |an )?(?:message|msg|text)\\s+to|message|msg|text|whats ?app)\\s+(.+?)\\s+(?:on|in|via)\\s+$APPS\\s+(?:$SAY\\s*)?(.+)$", I)
    private val msgHi = Regex("^(.+?)\\s+ko\\s+(?:whats ?app\\s+)?(?:message|msg)\\s+(?:bhejo|karo|send karo|bhej do)\\s+(?:ki\\s+)?(.+)$", I)
    private val msgB = Regex("^(?:send (?:a |an )?(?:message|msg|text)\\s+to|message|msg|text|whats ?app)\\s+(.+?)\\s+$SAY\\s*(.+)$", I)

    private val lockQ = Regex("^(?:please )?(?:lock (?:the |my )?(?:phone|screen|device|mobile)|(?:phone|screen|mobile) lock(?: kar do| karo| kar de)?|lock kar do|lock it)$", I)
    private val unlockQ = Regex("^(?:please )?(?:unlock (?:the |my )?(?:phone|screen|device|mobile)|(?:wake|turn on) (?:up )?(?:the |my )?(?:phone|screen)|(?:phone|screen|mobile) unlock(?: kar do| karo| kar de)?|unlock kar do|screen on kar do)$", I)
    private val callA = Regex("^(?:please )?(?:call|phone|dial|ring)\\s+(.+)$", I)
    private val callB = Regex("^(.+?)\\s+ko\\s+(?:phone |call )(?:karo|kar do|lagao|laga do|milao)$", I)

    private val timerA = Regex("^(?:set\\s+)?(?:a\\s+)?timer\\s+(?:for\\s+)?(\\d+)\\s*(second|sec|minute|min|hour|hr)s?$", I)
    private val timerB = Regex("^(\\d+)\\s*(second|sec|minute|min|hour|hr)s?\\s+ka\\s+timer(?:\\s+(?:laga do|set karo|lagao))?$", I)

    fun match(textRaw: String): Match? {
        val t = textRaw.trim().trim('.', '!', '?', ',', '।').replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return null

        // Messages: one step, no LLM. send_message itself previews the text and asks you to confirm before sending.
        if (t.length <= 400) {
            msgC.find(t)?.let { return msg(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            msgA.find(t)?.let { return msg(it.groupValues[2], it.groupValues[1], it.groupValues[3]) }
            msgHi.find(t)?.let { return msg("whatsapp", it.groupValues[1], it.groupValues[2]) }
            msgB.find(t)?.let { return msg("whatsapp", it.groupValues[1], it.groupValues[2]) }
        }

        if (t.length > 120) return null
        openPlayYt.find(t)?.let { return play(it.groupValues[1], "") }
        if (multi.containsMatchIn(t)) return null

        alarmA.find(t)?.let {
            var h = it.groupValues[1].toInt() % 12
            if (it.groupValues[3].lowercase() == "p") h += 12
            val m = it.groupValues[2].ifBlank { "0" }.toInt()
            return Match("set_alarm", JSONObject().put("hour", h).put("minute", m))
        }

        if (lockQ.containsMatchIn(t)) return Match("lock_phone", JSONObject())
        if (unlockQ.containsMatchIn(t)) return Match("unlock_phone", JSONObject())
        callA.find(t)?.let { return call(it.groupValues[1]) }
        callB.find(t)?.let { return call(it.groupValues[1]) }

        if (timeQ.containsMatchIn(t) || dateQ.containsMatchIn(t)) return Match("time_date", JSONObject())

        timerA.find(t)?.let { return timer(it) }
        timerB.find(t)?.let { return timer(it) }

        navA.find(t)?.let { return nav(it.groupValues[1]) }
        navB.find(t)?.let { return nav(it.groupValues[1]) }

        // Order matters: most specific play pattern first.
        playB.find(t)?.let { return play(it.groupValues[2], it.groupValues[1]) }
        playC.find(t)?.let { return play(it.groupValues[1], it.groupValues[2]) }
        playA.find(t)?.let { return play(it.groupValues[1], it.groupValues.getOrNull(2).orEmpty()) }
        playE.find(t)?.let { return play(it.groupValues[1], "") }
        playD.find(t)?.let { return play(it.groupValues[1], "") }

        (openA.find(t) ?: openB.find(t))?.let {
            val app = it.groupValues[1].trim()
            if (app.isNotEmpty() && app.split(" ").size <= 3) return Match("open_app", JSONObject().put("app", app))
        }
        return null
    }

    private fun msg(app: String, contact: String, message: String): Match? {
        val c = contact.trim().trim('"', '\'')
        val m = message.trim().trim('"', '\'')
        if (c.isEmpty() || m.isEmpty()) return null
        val a = when (app.lowercase().replace(" ", "")) {
            "insta", "instagram" -> "Instagram"
            "snap", "snapchat" -> "Snapchat"
            "telegram" -> "Telegram"
            else -> "WhatsApp"
        }
        return Match("send_message", JSONObject().put("app", a).put("contact", c).put("message", m))
    }

    private fun call(target: String): Match? {
        val x = target.trim().trim('"', '\'')
        if (x.isEmpty() || Regex("\\b(on|in|via)\\b", I).containsMatchIn(x)) return null // "call X on whatsapp" -> LLM
        return if (Regex("^[+\\d\\s-]{3,}$").matches(x))
            Match("call_contact", JSONObject().put("number", x.filter { it.isDigit() || it == '+' }))
        else Match("call_contact", JSONObject().put("name", x))
    }

    private fun play(query: String, app: String): Match? {
        val q = query.trim().trim('"', '\'')
        if (q.isEmpty() || q.lowercase().startsWith("store")) return null // "play store" is an app, not a song
        val music = app.lowercase().contains("music")
        return Match("play_media", JSONObject().put("query", q).put("app", if (music) "youtube_music" else "youtube"))
    }

    private fun nav(dest: String): Match? =
        dest.trim().takeIf { it.isNotEmpty() }?.let { Match("navigate_to", JSONObject().put("destination", it)) }

    private fun timer(m: MatchResult): Match {
        val n = m.groupValues[1].toInt()
        val unit = m.groupValues[2].lowercase()
        val secs = when { unit.startsWith("h") -> n * 3600; unit.startsWith("m") -> n * 60; else -> n }
        return Match("set_timer", JSONObject().put("seconds", secs))
    }
}
