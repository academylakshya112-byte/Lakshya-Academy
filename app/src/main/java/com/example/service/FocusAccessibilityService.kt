package com.example.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FocusAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FocusA11yService"
        private const val YOUTUBE_PKG = "com.google.android.youtube"
        private var lastBlockedTime = 0L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            FocusManager.initFromPrefs(this)
            AdultWebFilterGuard.init(this)
        } catch (e: Exception) {
            Log.e(TAG, "onServiceConnected error: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (FocusManager.blockedPackageSet.isEmpty()) {
            FocusManager.initFromPrefs(this)
        }

        val isAutoAdultFilterActive = AdultWebFilterGuard.isAutoAdultFilterEnabled
        val isFocusBlockingActive = FocusManager.isBlockingActive

        // If neither focus mode nor auto adult web guard is active, skip
        if (!isFocusBlockingActive && !isAutoAdultFilterActive) return

        val pkgName = event.packageName?.toString() ?: return

        // Never block our own app
        if (pkgName == packageName) return

        // 1. Direct App Blocking Check (During Focus Mode)
        if (isFocusBlockingActive && FocusManager.blockedPackageSet.contains(pkgName)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            triggerBlockOverlay(pkgName, "App is blocked during Focus Session")
            return
        }

        // 2. Real YouTube App Study Mode & Shorts Handling (During Focus Mode)
        if (isFocusBlockingActive && pkgName == YOUTUBE_PKG) {
            handleYouTubeAccessibilityEvent(event)
            return
        }

        // 3. General Shorts & Reels Detection for Other Social Apps (During Focus Mode)
        if (isFocusBlockingActive && FocusManager.blockShortsAndReels) {
            val className = event.className?.toString()?.lowercase() ?: ""
            val contentDesc = event.contentDescription?.toString()?.lowercase() ?: ""
            if (contentDesc.contains("shorts") || contentDesc.contains("reels") || className.contains("reels")) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                triggerBlockOverlay(pkgName, "Shorts & Reels are blocked during Focus Study Mode", isShorts = true)
                return
            }
        }

        // 4. Universal Browser Adult & Inappropriate Content & Custom Domain Checking
        // Runs on ANY web browser installed on the phone (Chrome, Samsung Internet, Firefox, Edge, Brave, Opera, UC, etc.)
        val isBrowserApp = AdultWebFilterGuard.isBrowserPackage(pkgName)
        if (isBrowserApp) {
            // Also check event text and content description directly for fast detection
            val eventTexts = event.text?.joinToString(" ") ?: ""
            val eventDesc = event.contentDescription?.toString() ?: ""
            val fastCheckText = "$eventTexts $eventDesc"

            if (isAutoAdultFilterActive && fastCheckText.isNotBlank()) {
                val (isAdultBlocked, adultReason) = AdultWebFilterGuard.evaluateUrlOrText(fastCheckText)
                if (isAdultBlocked) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    triggerBlockOverlay(
                        blockedTarget = pkgName,
                        reason = adultReason ?: "⚠️ Adult / Inappropriate website automatically blocked by SHADOW Web Guard."
                    )
                    return
                }
            }

            try {
                val rootNode = rootInActiveWindow ?: return
                checkNodeHierarchyForBlockedContent(rootNode, pkgName, isAutoAdultFilterActive, isFocusBlockingActive)
            } catch (e: Exception) {
                Log.d(TAG, "Node traversal note: ${e.message}")
            }
        }
    }

    private fun handleYouTubeAccessibilityEvent(event: AccessibilityEvent) {
        val className = event.className?.toString()?.lowercase() ?: ""
        val contentDesc = event.contentDescription?.toString()?.lowercase() ?: ""
        val textList = event.text?.map { it.toString().lowercase() } ?: emptyList()

        // 1. Check for YouTube Shorts (Always block short-form doomscrolling during focus)
        if (FocusManager.blockYouTubeShorts || FocusManager.blockShortsAndReels) {
            val isShortsEvent = className.contains("reel") ||
                    className.contains("shorts") ||
                    contentDesc.contains("shorts") ||
                    contentDesc.contains("sound used in this short") ||
                    contentDesc.contains("remix this short") ||
                    contentDesc.contains("subscribe to shorts") ||
                    textList.any { it.contains("shorts") }

            if (isShortsEvent) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                triggerBlockOverlay(
                    blockedTarget = YOUTUBE_PKG,
                    reason = "📵 YouTube Shorts are blocked during Focus Study Mode.",
                    isShorts = true
                )
                return
            }
        }

        // 2. If YouTube Study Mode is NOT active, allow normal YouTube usage (unless shorts was triggered)
        if (!FocusManager.isYouTubeStudyModeEnabled) {
            return
        }

        // 3. Inspect Real YouTube App Node Hierarchy
        val rootNode = try {
            rootInActiveWindow
        } catch (e: Exception) {
            null
        } ?: return

        try {
            val nodeTextList = mutableListOf<String>()
            val viewIds = mutableListOf<String>()
            var isShortsNodeDetected = false
            var isSearchOrLibrary = false
            var isHomeFeedOnly = false

            collectYouTubeNodeInfo(
                node = rootNode,
                textCollector = nodeTextList,
                viewIdCollector = viewIds,
                onShortsFound = { isShortsNodeDetected = true },
                onSearchFound = { isSearchOrLibrary = true }
            )

            // If Shorts view hierarchy is detected
            if (isShortsNodeDetected && (FocusManager.blockYouTubeShorts || FocusManager.blockShortsAndReels)) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                triggerBlockOverlay(
                    blockedTarget = YOUTUBE_PKG,
                    reason = "📵 YouTube Shorts are blocked during Focus Study Mode.",
                    isShorts = true
                )
                return
            }

            // If user is actively searching study keywords or in their offline downloads/library, allow navigation
            if (isSearchOrLibrary) {
                return
            }

            val allowedChannels = FocusManager.allowedChannelSet
            val fullScreenText = nodeTextList.joinToString(" ").lowercase()

            // General study keywords that indicate an educational lecture / study video
            val educationalKeywords = listOf(
                "lecture", "chapter", "ncert", "class 10", "class 12", "class 9", "class 11",
                "physics", "chemistry", "biology", "mathematics", "maths", "science", "revision",
                "one shot", "pyq", "solutions", "study", "neet", "jee", "upsc", "ssc", "board exam",
                "sample paper", "tutorial", "full course", "explanation", "academy", "formula",
                "derivation", "trigonometry", "algebra", "geometry", "history", "geography", "polity",
                "economics", "english grammar", "hindi vyakaran", "notes", "marathon"
            )

            // Check if any allowed channel matches
            var isApprovedChannelMatched = false
            for (channel in allowedChannels) {
                val normalizedChannel = channel.trim().lowercase()
                if (normalizedChannel.isBlank()) continue

                val cleanAlphanumeric = normalizedChannel.replace(Regex("[^a-z0-9]"), "")

                if (fullScreenText.contains(normalizedChannel) ||
                    (cleanAlphanumeric.length >= 3 && fullScreenText.replace(Regex("[^a-z0-9]"), "").contains(cleanAlphanumeric))) {
                    isApprovedChannelMatched = true
                    break
                }
            }

            // If channel matched, allow video playback!
            if (isApprovedChannelMatched) {
                return
            }

            // If not matched by channel, check if the video is genuinely educational / study content
            var isEducationalStudyVideo = false
            for (eduWord in educationalKeywords) {
                if (fullScreenText.contains(eduWord)) {
                    isEducationalStudyVideo = true
                    break
                }
            }

            if (isEducationalStudyVideo) {
                // Legitimate educational video allowed
                return
            }

            // If video is entertainment / non-study video and non-whitelisted channel, block it!
            if (fullScreenText.length > 20) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                triggerBlockOverlay(
                    blockedTarget = YOUTUBE_PKG,
                    reason = "⚠️ Non-study content blocked in YouTube.\nOnly selected Study Channels & Educational Videos are allowed during Focus Mode.",
                    isYouTubeStudy = true
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "YouTube hierarchy analysis error: ${e.message}")
        }
    }

    private fun collectYouTubeNodeInfo(
        node: AccessibilityNodeInfo,
        textCollector: MutableList<String>,
        viewIdCollector: MutableList<String>,
        onShortsFound: () -> Unit,
        onSearchFound: () -> Unit,
        depth: Int = 0
    ) {
        if (depth > 18) return

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (viewId.isNotBlank()) viewIdCollector.add(viewId)
        if (text.isNotBlank()) textCollector.add(text)
        if (desc.isNotBlank()) textCollector.add(desc)

        // Check for shorts indicators
        if (viewId.contains("reel") ||
            viewId.contains("shorts") ||
            viewId.contains("pivot_shorts") ||
            desc.contains("shorts") ||
            text.contains("shorts") ||
            desc.contains("subscribe to shorts")
        ) {
            onShortsFound()
        }

        // Check for search query / search box / library indicators
        if (viewId.contains("search_edit_text") ||
            viewId.contains("search_query") ||
            viewId.contains("searchbox") ||
            desc.contains("search youtube") ||
            desc.contains("clear query") ||
            text.contains("search youtube") ||
            viewId.contains("library") ||
            desc.contains("library") ||
            desc.contains("downloads")
        ) {
            onSearchFound()
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectYouTubeNodeInfo(child, textCollector, viewIdCollector, onShortsFound, onSearchFound, depth + 1)
            child.recycle()
        }
    }

    private fun checkNodeHierarchyForBlockedContent(
        node: AccessibilityNodeInfo,
        pkgName: String,
        isAutoAdultFilterActive: Boolean,
        isFocusBlockingActive: Boolean,
        depth: Int = 0
    ) {
        if (depth > 20) return

        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val candidateText = "$text $contentDesc"

        // 1. Automatic Adult & Inappropriate Website Auto Detection across ALL phone browsers
        if (isAutoAdultFilterActive && candidateText.isNotBlank()) {
            val (isAdultBlocked, adultReason) = AdultWebFilterGuard.evaluateUrlOrText(candidateText)
            if (isAdultBlocked) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                triggerBlockOverlay(
                    blockedTarget = pkgName,
                    reason = adultReason ?: "⚠️ Adult / Inappropriate website automatically blocked by SHADOW Web Guard."
                )
                return
            }
        }

        // 2. Custom User-Blocked Domains (Active during Focus Mode)
        if (isFocusBlockingActive && candidateText.isNotBlank()) {
            for (domain in FocusManager.blockedDomainSet) {
                val cleanDomain = domain.lowercase().replace("http://", "").replace("https://", "").replace("www.", "").trim()
                if (cleanDomain.isNotBlank() && (text.contains(cleanDomain) || contentDesc.contains(cleanDomain))) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    triggerBlockOverlay(pkgName, "Website $cleanDomain is blocked during your Focus Study Session.")
                    return
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            checkNodeHierarchyForBlockedContent(child, pkgName, isAutoAdultFilterActive, isFocusBlockingActive, depth + 1)
            child.recycle()
        }
    }

    private fun triggerBlockOverlay(
        blockedTarget: String,
        reason: String,
        isYouTubeStudy: Boolean = false,
        isShorts: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        if (now - lastBlockedTime < 1200L) {
            return // Throttle multiple rapid events
        }
        lastBlockedTime = now
        FocusManager.triggerBlockOverlay(this, blockedTarget, reason, isYouTubeStudy, isShorts)
    }

    override fun onInterrupt() {
        Log.d(TAG, "FocusAccessibilityService interrupted")
    }
}

