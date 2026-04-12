package com.velo.app.interceptor

/**
 * Regex patterns for all supported video sites.
 * Used to validate URLs before attempting downloads and for clipboard detection.
 */
object SupportedSites {

    private val patterns = listOf(
        // YouTube
        Regex("""https?://(www\.|m\.)?youtube\.com/watch\?.*v=[\w-]+"""),
        Regex("""https?://youtu\.be/[\w-]+"""),
        Regex("""https?://(www\.)?youtube\.com/shorts/[\w-]+"""),
        Regex("""https?://(www\.)?youtube\.com/live/[\w-]+"""),
        Regex("""https?://(www\.)?youtube\.com/playlist\?.*list=[\w-]+"""),

        // Instagram
        Regex("""https?://(www\.)?instagram\.com/(p|reel|tv)/[\w-]+"""),
        Regex("""https?://(www\.)?instagram\.com/stories/[\w]+/\d+"""),

        // Facebook
        Regex("""https?://(www\.|m\.)?facebook\.com/.*/videos/\d+"""),
        Regex("""https?://(www\.|m\.)?facebook\.com/reel/\d+"""),
        Regex("""https?://fb\.watch/[\w-]+"""),

        // Twitter / X
        Regex("""https?://(www\.)?(twitter|x)\.com/[\w]+/status/\d+"""),

        // TikTok
        Regex("""https?://(www\.)?tiktok\.com/@[\w.]+/video/\d+"""),
        Regex("""https?://vm\.tiktok\.com/[\w]+"""),
        Regex("""https?://(www\.)?tiktok\.com/t/[\w]+"""),

        // Reddit
        Regex("""https?://(www\.)?reddit\.com/r/[\w]+/comments/[\w]"""),
        Regex("""https?://v\.redd\.it/[\w]+"""),
        Regex("""https?://redd\.it/[\w]+"""),

        // Vimeo
        Regex("""https?://(www\.)?vimeo\.com/\d+"""),

        // Dailymotion
        Regex("""https?://(www\.)?dailymotion\.com/video/[\w]+"""),
        Regex("""https?://dai\.ly/[\w]+"""),

        // Twitch
        Regex("""https?://(www\.)?twitch\.tv/videos/\d+"""),
        Regex("""https?://clips\.twitch\.tv/[\w-]+"""),

        // Pinterest
        Regex("""https?://(www\.)?pinterest\.com/pin/\d+"""),

        // LinkedIn (videos)
        Regex("""https?://(www\.)?linkedin\.com/posts/.*video"""),
        Regex("""https?://(www\.)?linkedin\.com/feed/update/urn:li:activity:\d+"""),

        // SoundCloud (audio)
        Regex("""https?://(www\.)?soundcloud\.com/[\w-]+/[\w-]+"""),

        // Bilibili
        Regex("""https?://(www\.)?bilibili\.com/(video|bangumi)/[\w]+"""),

        // Snapchat spotlight
        Regex("""https?://(www\.)?snapchat\.com/spotlight/[\w]+"""),

        // Tumblr
        Regex("""https?://[\w-]+\.tumblr\.com/post/\d+"""),

        // Generic (any direct .mp4/.webm/.mov link)
        Regex("""https?://.*\.(mp4|webm|mov|mkv|avi)(\?.*)?$"""),
    )

    /**
     * Returns true if the given text is a generic valid HTTP/HTTPS URL.
     */
    fun isAnyUrl(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }

    /**
     * Returns true if the given text is a URL matching any explicitly supported site.
     * Used exclusively to filter background Clipboard and OS Share Intents to avoid spam.
     */
    fun matches(text: String): Boolean {
        val trimmed = text.trim()
        if (!isAnyUrl(trimmed)) return false
        return patterns.any { it.containsMatchIn(trimmed) }
    }

    /**
     * Extracts a generically valid URL from a longer string.
     */
    fun extractAnyUrl(text: String): String? {
        val urlRegex = Regex("""https?://\S+""")
        return urlRegex.find(text.trim())?.value
    }

    /**
     * Extracts a URL from a longer string (e.g. share text with title + URL),
     * strictly confirming it against the whitelist.
     */
    fun extractUrl(text: String): String? {
        return extractAnyUrl(text)?.let { url ->
            if (matches(url)) url else null
        }
    }

    /**
     * Returns the site display name for a given URL.
     */
    fun siteName(url: String): String = when {
        url.contains("youtube.com") || url.contains("youtu.be") -> "youtube"
        url.contains("instagram.com") -> "instagram"
        url.contains("facebook.com") || url.contains("fb.watch") -> "facebook"
        url.contains("twitter.com") || url.contains("x.com") -> "twitter / x"
        url.contains("tiktok.com") -> "tiktok"
        url.contains("reddit.com") || url.contains("redd.it") -> "reddit"
        url.contains("vimeo.com") -> "vimeo"
        url.contains("dailymotion.com") || url.contains("dai.ly") -> "dailymotion"
        url.contains("twitch.tv") -> "twitch"
        url.contains("soundcloud.com") -> "soundcloud"
        url.contains("bilibili.com") -> "bilibili"
        url.contains("pinterest.com") -> "pinterest"
        url.contains("linkedin.com") -> "linkedin"
        else -> "video"
    }
}
