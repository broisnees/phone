package com.nees.launcher

import android.content.Context
import java.io.File
import java.time.Instant

/** Claude Code token usage for the current 5-hour rolling window, read from Termux JSONL via root. */
data class ClaudeUsage(
    val present: Boolean,
    val active: Boolean,
    val usedTokens: Long,
    val windowStart: Long,
    val resetAt: Long,
)

object Claude {
    private const val FIVE_H = 5L * 60 * 60 * 1000
    private const val PROJECTS = "/data/data/com.termux/files/home/.claude/projects"

    // Extracts "ISO8601 sumTokens" per assistant line from files modified in the last 6h.
    private val SCRIPT = """
        DIR=$PROJECTS
        files=${'$'}(find "${'$'}DIR" -name '*.jsonl' -mmin -360 2>/dev/null)
        [ -z "${'$'}files" ] && { echo NONE; exit 0; }
        awk '
        /"output_tokens"/{
          ts="";a=0;b=0;c=0;d=0;
          if(match(${'$'}0,/"timestamp":"[^"]+"/)){s=substr(${'$'}0,RSTART,RLENGTH);gsub(/"timestamp":"|"/,"",s);ts=s}
          if(match(${'$'}0,/"input_tokens":[0-9]+/)){s=substr(${'$'}0,RSTART,RLENGTH);gsub(/[^0-9]/,"",s);a=s+0}
          if(match(${'$'}0,/"cache_creation_input_tokens":[0-9]+/)){s=substr(${'$'}0,RSTART,RLENGTH);gsub(/[^0-9]/,"",s);b=s+0}
          if(match(${'$'}0,/"cache_read_input_tokens":[0-9]+/)){s=substr(${'$'}0,RSTART,RLENGTH);gsub(/[^0-9]/,"",s);c=s+0}
          if(match(${'$'}0,/"output_tokens":[0-9]+/)){s=substr(${'$'}0,RSTART,RLENGTH);gsub(/[^0-9]/,"",s);d=s+0}
          if(ts!="") print ts, (a+b+c+d)
        }' ${'$'}files 2>/dev/null
    """.trimIndent()

    private var scriptPath: String? = null

    private fun script(ctx: Context): String {
        scriptPath?.let { return it }
        val f = File(ctx.filesDir, "claude_usage.sh")
        runCatching { f.writeText(SCRIPT) }
        return f.absolutePath.also { scriptPath = it }
    }

    /** Read + compute. Call off the main thread. Returns present=false if unavailable or root denied. */
    fun read(ctx: Context): ClaudeUsage {
        val none = ClaudeUsage(false, false, 0, 0, 0)
        val out = Root.capture("sh ${script(ctx)}") ?: return none
        if (out.isBlank() || out.trim() == "NONE") return none

        // Parse "<iso> <tokens>" rows, sorted ascending by time.
        val rows = out.lineSequence().mapNotNull { line ->
            val sp = line.lastIndexOf(' ')
            if (sp <= 0) return@mapNotNull null
            val ts = runCatching { Instant.parse(line.substring(0, sp)).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
            val tok = line.substring(sp + 1).trim().toLongOrNull() ?: return@mapNotNull null
            ts to tok
        }.sortedBy { it.first }.toList()
        if (rows.isEmpty()) return none

        // Group into 5h blocks; the current window is the last block.
        var blockStart = rows.first().first
        var sum = 0L
        for ((ts, tok) in rows) {
            if (ts - blockStart >= FIVE_H) { blockStart = ts; sum = 0 }
            sum += tok
        }
        val reset = blockStart + FIVE_H
        val now = System.currentTimeMillis()
        val active = now < reset
        // Expired window → nothing counts until the next message; show a ready/zero state.
        return if (active) ClaudeUsage(true, true, sum, blockStart, reset)
        else ClaudeUsage(true, false, 0, blockStart, reset)
    }
}
