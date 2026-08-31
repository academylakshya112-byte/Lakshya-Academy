package com.example.service

import android.content.Context
import android.util.Log

/**
 * Intelligent Real-Time Safe Study Web Filter.
 * Automatically detects and blocks adult (18+), explicit, gambling, malicious,
 * and high-distraction non-educational websites across ALL phone browsers without requiring manual URL entry.
 */
object AdultWebFilterGuard {

    private const val TAG = "AdultWebFilterGuard"

    // High-risk adult & explicit top-level and base domain indicators
    private val ADULT_DOMAINS = hashSetOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
        "youporn.com", "chaturbate.com", "stripchat.com", "onlyfans.com",
        "brazzers.com", "eporner.com", "hqporner.com", "spankbang.com",
        "rule34.xxx", "beeg.com", "tube8.com", "daftsex.com", "heavy-r.com",
        "livejasmin.com", "cam4.com", "bonga.com", "camsoda.com", "bongacams.com",
        "tnaflix.com", "porn.com", "drtuber.com", "porntrex.com", "tubegalore.com",
        "nuvid.com", "sunporno.com", "4tube.com", "empflix.com", "fapvid.com",
        "pornmd.com", "gotporn.com", "thumbzilla.com", "vporn.com", "txxx.com",
        "sex.com", "xxx.com", "motherless.com", "xcafe.com", "adultfriendfinder.com",
        "fetlife.com", "fapello.com", "coomer.party", "coomer.su", "kemono.party", "kemono.su",
        "erome.com", "erothots.com", "thothub.to", "thotslife.com", "noodlemagazine.com",
        "sexvid.xxx", "anysex.com", "fuskator.com", "cliphunter.com", "fuq.com",
        "upornia.com", "xozilla.com", "porngifs.com", "gifporn.com", "luscious.net",
        "nhentai.net", "hanime.tv", "hentaihaven.xxx", "hentai2read.com", "tsumino.com",
        "hitomi.la", "e-hentai.org", "gelbooru.com", "danbooru.donmai.us", "sankakucomplex.com",
        "desipapa.com", "desiporn.com", "kamababa.com", "masaladesi.com", "fsiblog.com",
        "antarvasna.com", "savita-bhabhi.com", "indianpornvideos.com", "bhabhiporn.com",
        "desixnxx.com", "ullu.app", "kooku.app", "primeplay.app", "bigshots.app",
        "adultgames.me", "itch.io/games/tag-nsfw", "nutaku.net", "lewdzone.com",
        "f95zone.to", "subscribestar.adult", "fansly.com", "manyvids.com", "loyalfans.com"
    )

    // Keywords and explicit slugs that trigger automatic instant protection
    private val EXPLICIT_KEYWORDS = listOf(
        "porn", "xxx", "xvideos", "xnxx", "xhamster", "sexvideo", "adultvideo",
        "erotic", "nude", "naked", "blowjob", "handjob", "boobs", "hentai",
        "camgirl", "sexchat", "escort", "stripper", "hardcore", "milf", "taboo",
        "gangbang", "incest", "deepthroat", "pussy", "dickpic", "playboy",
        "penthouse", "erome", "fapello", "nudevid", "fuckvid", "desi sex",
        "bhabhi sex", "hot sex", "sexy video", "x-rated", "redtube", "brazzers",
        "chaturbate", "onlyfans leaks", "nsfw", "ecchi", "yaoi", "yuri",
        "javhd", "jav uncensored", "av idol", "doujinshi", "chudai", "suhagraat",
        "sax sux", "hot mujra", "leaked mms", "nude leak", "stripchat", "bongacams",
        "livejasmin", "spankbang", "eporner", "rule34", "sex web series", "pornstar",
        "hentai video", "adult comics", "adult game", "18+ video", "sexy movie", "hot scene"
    )

    // Dangerous Gambling & Betting domains that distract students
    private val GAMBLING_DOMAINS = hashSetOf(
        "bet365.com", "1xbet.com", "parimatch.com", "stake.com", "betway.com",
        "dafabet.com", "melbet.com", "22bet.com", "mostbet.com", "fairplay.club",
        "lotus365.com", "mahadevbook.com", "kheloyar.com", "winbuzz.com",
        "reddyanna.com", "cricketbetting.net", "casinobet.com", "pokerstars.com",
        "teenpattilive.com", "rummycircle.com", "junglee rummy"
    )

    // Allowed Safe Educational / Study Whitelist (Never accidentally block these)
    private val SAFE_STUDY_DOMAINS = hashSetOf(
        "ncert.nic.in", "cbse.gov.in", "khanacademy.org", "wikipedia.org",
        "w3schools.com", "geeksforgeeks.org", "stackoverflow.com", "github.com",
        "physicswallah.live", "pw.live", "allen.ac.in", "unacademy.com",
        "vedantu.com", "byjus.com", "testbook.com", "doubtnut.com",
        "shiksha.com", "jagranjosh.com", "sarkariresult.com", "nta.ac.in",
        "swayam.gov.in", "coursera.org", "edx.org", "udemy.com", "medium.com",
        "google.com", "google.co.in", "bing.com", "developer.android.com", "kotlinlang.org",
        "youtube.com", "youtu.be"
    )

    @Volatile
    var isAutoAdultFilterEnabled: Boolean = true
        private set

    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences("shadow_focus_prefs", Context.MODE_PRIVATE)
            isAutoAdultFilterEnabled = prefs.getBoolean("auto_adult_site_filter", true)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AdultWebFilterGuard: ${e.message}")
        }
    }

    fun setAutoAdultFilterEnabled(context: Context, enabled: Boolean) {
        isAutoAdultFilterEnabled = enabled
        try {
            val prefs = context.getSharedPreferences("shadow_focus_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("auto_adult_site_filter", enabled).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving auto adult filter state: ${e.message}")
        }
    }

    /**
     * Checks if a package name belongs to ANY web browser on the user's phone.
     */
    fun isBrowserPackage(pkgName: String): Boolean {
        val cleanPkg = pkgName.lowercase()
        return cleanPkg == "com.android.chrome" ||
                cleanPkg.contains("chrome") ||
                cleanPkg == "org.mozilla.firefox" ||
                cleanPkg.contains("firefox") ||
                cleanPkg.contains("mozilla") ||
                cleanPkg == "com.sec.android.app.sbrowser" ||
                cleanPkg.contains("sbrowser") ||
                cleanPkg.contains("samsung") && cleanPkg.contains("browser") ||
                cleanPkg == "com.microsoft.emmx" ||
                cleanPkg.contains("edge") ||
                cleanPkg == "com.brave.browser" ||
                cleanPkg.contains("brave") ||
                cleanPkg == "com.opera.browser" ||
                cleanPkg.contains("opera") ||
                cleanPkg.contains("ucmobile") ||
                cleanPkg.contains("uc.browser") ||
                cleanPkg.contains("duckduckgo") ||
                cleanPkg.contains("mi.globalbrowser") ||
                cleanPkg.contains("mintbrowser") ||
                cleanPkg.contains("vivo.browser") ||
                cleanPkg.contains("coloros.browser") ||
                cleanPkg.contains("heytap.browser") ||
                cleanPkg.contains("transsion.phoenix") ||
                cleanPkg.contains("kiwibrowser") ||
                cleanPkg.contains("cloudmosa.puffin") ||
                cleanPkg.contains("jio.web.browser") ||
                cleanPkg.contains("vivaldi") ||
                cleanPkg.contains("torproject") ||
                cleanPkg.contains("browser") ||
                cleanPkg.contains("browser") ||
                cleanPkg.endsWith(".browser")
    }

    /**
     * Checks if a URL, domain, or address bar query is an explicit or adult website.
     * Returns a pair of (isBlocked, detectedReason).
     */
    fun evaluateUrlOrText(input: String): Pair<Boolean, String?> {
        if (!isAutoAdultFilterEnabled) return Pair(false, null)

        val clean = input.trim().lowercase()
        if (clean.isBlank() || clean.length < 3) return Pair(false, null)

        // 1. Never block verified educational study domains
        for (safe in SAFE_STUDY_DOMAINS) {
            if (clean.contains(safe)) {
                return Pair(false, null)
            }
        }

        // 2. Direct Domain match in known adult sites database
        for (adultDomain in ADULT_DOMAINS) {
            val root = adultDomain.replace(".com", "").replace(".net", "").replace(".org", "").replace(".xxx", "")
            if (clean.contains(adultDomain) || (root.length >= 4 && clean.contains(root) && (clean.contains("http") || clean.contains("www.") || clean.contains(".com") || clean.contains(".tv") || clean.contains(".to") || clean.contains(".me") || clean.contains(".la") || clean.contains(".org")))) {
                return Pair(true, "⚠️ 18+ Adult Content automatically blocked by SHADOW Web Guard ($adultDomain)")
            }
        }

        // 3. Direct Domain match in gambling/betting
        for (gamblingDomain in GAMBLING_DOMAINS) {
            val root = gamblingDomain.replace(".com", "").replace(".net", "").replace(".club", "")
            if (clean.contains(gamblingDomain) || (root.length >= 4 && clean.contains(root))) {
                return Pair(true, "⚠️ Betting & Gambling Site Blocked ($gamblingDomain)")
            }
        }

        // 4. Token & Keyword heuristics for adult search queries or unlisted explicit URLs
        val sanitized = clean
            .replace("http://", "")
            .replace("https://", "")
            .replace("www.", "")
            .replace("/", " ")
            .replace("?", " ")
            .replace("&", " ")
            .replace("=", " ")
            .replace("-", " ")
            .replace("_", " ")
            .replace(".", " ")
            .replace("+", " ")
            .replace("%20", " ")

        val words = sanitized.split(Regex("\\s+")).filter { it.isNotBlank() }

        for (kw in EXPLICIT_KEYWORDS) {
            val kwClean = kw.trim().lowercase()
            if (kwClean.contains(" ")) {
                if (sanitized.contains(kwClean)) {
                    return Pair(true, "⚠️ Inappropriate Search / Website detected & blocked ($kwClean)")
                }
            } else {
                if (words.contains(kwClean) || (clean.contains(kwClean) && (clean.contains(".com") || clean.contains(".xxx") || clean.contains(".tube") || clean.contains(".net") || clean.contains("search") || clean.contains("query")))) {
                    return Pair(true, "⚠️ Inappropriate Content filtered by SHADOW Auto Guard ($kwClean)")
                }
            }
        }

        return Pair(false, null)
    }
}

