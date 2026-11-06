package com.jarvis.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jarvis.ai.Http
import com.jarvis.ai.HttpException
import com.jarvis.agent.Tool
import com.jarvis.agent.ToolResult
import com.jarvis.agent.fail
import com.jarvis.agent.ok
import com.jarvis.app.Graph
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

/** Lightweight lookup (DuckDuckGo instant answers + Wikipedia). It is NOT a full web search engine. */
class WebSearchTool : Tool {
    override val name = "web_search"
    override val description =
        "Quick factual lookup (instant answers + Wikipedia snippets; not full web search). args: {\"query\":\"...\"}"

    override suspend fun execute(args: JSONObject): ToolResult {
        val q = args.optString("query").trim()
        if (q.isEmpty()) return fail("web_search needs a query.")
        val out = StringBuilder()
        runCatching {
            val r = JSONObject(
                Http.request("GET", "https://api.duckduckgo.com/?q=${enc(q)}&format=json&no_html=1&skip_disambig=1", mapOf("User-Agent" to "JARVIS-Android"))
            )
            r.optString("Answer").takeIf { it.isNotBlank() }?.let { out.append("Answer: $it\n") }
            r.optString("AbstractText").takeIf { it.isNotBlank() }?.let { out.append("Summary: $it (${r.optString("AbstractURL")})\n") }
            val rel = r.optJSONArray("RelatedTopics") ?: JSONArray()
            for (i in 0 until minOf(3, rel.length())) rel.optJSONObject(i)?.optString("Text")?.takeIf { it.isNotBlank() }?.let { out.append("Related: $it\n") }
        }
        runCatching {
            val r = JSONObject(
                Http.request(
                    "GET",
                    "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${enc(q)}&format=json&srlimit=3",
                    mapOf("User-Agent" to "JARVIS-Android/1.0")
                )
            )
            val arr = r.optJSONObject("query")?.optJSONArray("search") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.append("Wikipedia: ${o.optString("title")} - ${o.optString("snippet").replace(Regex("<[^>]*>"), "")}\n")
            }
        }
        return if (out.isBlank()) fail("No quick results found. I can open a browser search instead.") else ok("Search results for '$q':", out.toString())
    }
}

class BrowserTool(private val ctx: Context) : Tool {
    override val name = "browser"
    override val description = "Open a web page or a Google search in the browser. args: {\"url\":\"https://...\"} or {\"query\":\"search terms\"}"

    override suspend fun execute(args: JSONObject): ToolResult {
        val url = args.optString("url").trim()
        val query = args.optString("query").trim()
        val target = when {
            url.startsWith("https://") || url.startsWith("http://") -> url
            query.isNotEmpty() -> "https://www.google.com/search?q=${enc(query)}"
            else -> return fail("browser needs an http(s) url or a query.")
        }
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ok("Opened $target in the browser.")
    }
}

/** Read-only GitHub status via the REST API. Token (optional for public repos) is stored encrypted. */
class GitHubTool(private val g: Graph) : Tool {
    override val name = "github"
    override val description =
        "Read-only GitHub info. args: {\"action\":\"repo_status|latest_run|issues|list_repos\",\"repo\":\"owner/name (optional, default from settings)\"}"

    override suspend fun execute(args: JSONObject): ToolResult {
        val action = args.optString("action", "repo_status")
        val repo = args.optString("repo").ifBlank { g.settings.githubRepo }
        val needsRepo = action != "list_repos"
        if (needsRepo && !Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(repo)) {
            return fail("I need a repo in owner/name form. Set a default in Settings or tell me the repo.")
        }
        val headers = mutableMapOf(
            "Accept" to "application/vnd.github+json",
            "User-Agent" to "JARVIS-Android",
            "X-GitHub-Api-Version" to "2022-11-28"
        )
        g.settings.githubToken.takeIf { it.isNotBlank() }?.let { headers["Authorization"] = "Bearer $it" }
        fun get(path: String) = "https://api.github.com$path"
        return try {
            when (action) {
                "repo_status" -> {
                    val r = JSONObject(Http.request("GET", get("/repos/$repo"), headers))
                    val runs = JSONObject(Http.request("GET", get("/repos/$repo/actions/runs?per_page=1"), headers)).optJSONArray("workflow_runs")
                    val run = runs?.optJSONObject(0)
                    ok(
                        "$repo: ${r.optString("description").ifBlank { "no description" }}. Last push ${r.optString("pushed_at")}, " +
                            "${r.optInt("open_issues_count")} open issues/PRs, default branch ${r.optString("default_branch")}." +
                            (run?.let { " Latest build: ${it.optString("status")}/${it.optString("conclusion")} on ${it.optString("head_branch")}." } ?: " No workflow runs.")
                    )
                }
                "latest_run" -> {
                    val runs = JSONObject(Http.request("GET", get("/repos/$repo/actions/runs?per_page=3"), headers)).optJSONArray("workflow_runs")
                    if (runs == null || runs.length() == 0) return ok("No workflow runs for $repo.")
                    ok("Recent runs for $repo:", (0 until runs.length()).joinToString("\n") {
                        val o = runs.getJSONObject(it)
                        "${o.optString("name")}: ${o.optString("status")}/${o.optString("conclusion")} on ${o.optString("head_branch")} at ${o.optString("created_at")}"
                    })
                }
                "issues" -> {
                    val arr = JSONArray(Http.request("GET", get("/repos/$repo/issues?state=open&per_page=5"), headers))
                    if (arr.length() == 0) ok("No open issues in $repo.")
                    else ok("Open issues/PRs in $repo:", (0 until arr.length()).joinToString("\n") { "#${arr.getJSONObject(it).optInt("number")} ${arr.getJSONObject(it).optString("title")}" })
                }
                "list_repos" -> {
                    if (g.settings.githubToken.isBlank()) return fail("Listing your repos needs a GitHub token in Settings.")
                    val arr = JSONArray(Http.request("GET", get("/user/repos?sort=pushed&per_page=8"), headers))
                    ok("Your recent repos:", (0 until arr.length()).joinToString("\n") { arr.getJSONObject(it).optString("full_name") })
                }
                else -> fail("Unknown github action '$action'.")
            }
        } catch (e: HttpException) {
            fail(
                when (e.code) {
                    401 -> "GitHub rejected the token. Check it in Settings."
                    403 -> "GitHub refused (rate limit or missing token permission)."
                    404 -> "GitHub says $repo was not found (private repos need a token)."
                    else -> "GitHub error ${e.code}."
                }
            )
        }
    }
}
