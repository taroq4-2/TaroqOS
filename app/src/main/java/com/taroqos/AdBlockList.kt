package com.taroqos

object AdBlockList {

    val blockedDomains: Set<String> = setOf(
        // Google Ads
        "googleadservices.com", "googlesyndication.com", "googletagmanager.com",
        "googletagservices.com", "doubleclick.net", "adservice.google.com",
        "adservice.google.ae", "adservice.google.co", "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com", "partner.googleadservices.com",
        // Facebook Ads
        "an.facebook.com", "graph.facebook.com", "connect.facebook.net",
        "staticxx.facebook.com", "www.facebook.com", "edge-mqtt.facebook.com",
        // Amazon Ads
        "aax.amazon-adsystem.com", "fls-na.amazon-adsystem.com",
        "s.amazon-adsystem.com", "mads.amazon-adsystem.com",
        // Twitter/X Ads
        "ads-twitter.com", "ads.twitter.com", "analytics.twitter.com",
        "t.co", "syndication.twitter.com",
        // TikTok Ads
        "ads.tiktok.com", "analytics.tiktok.com", "mon.musical.ly",
        "log-va.tiktokv.com", "log-sg.tiktokv.com",
        // Snapchat Ads
        "ads.snapchat.com", "tr.snapchat.com", "sc-static.net",
        // General Ad Networks
        "ads.yahoo.com", "adtech.de", "advertising.com",
        "aolcdn.com", "appnexus.com", "adsystem.amazon.com",
        "adnxs.com", "ib.adnxs.com", "secure.adnxs.com",
        "rubiconproject.com", "fastlane.rubiconproject.com",
        "openx.net", "servedby.openx.net",
        "pubmatic.com", "ads.pubmatic.com",
        "criteo.com", "dis.criteo.com", "rtax.criteo.com",
        "outbrain.com", "odb.outbrain.com",
        "taboola.com", "trc.taboola.com",
        "moatads.com", "z.moatads.com",
        "adsafeprotected.com", "pixel.adsafeprotected.com",
        // Analytics / Tracking
        "hotjar.com", "app.hotjar.com",
        "segment.com", "api.segment.io",
        "amplitude.com", "api.amplitude.com",
        "mixpanel.com", "api.mixpanel.com",
        "heap.io", "heapanalytics.com",
        "fullstory.com", "rs.fullstory.com",
        "mouseflow.com", "a.mouseflow.com",
        "appsflyer.com", "api2.appsflyer.com", "t.appsflyer.com",
        "adjust.com", "app.adjust.com", "s2s.adjust.com",
        "branch.io", "api2.branch.io",
        "firebase.google.com", "firebaselogging-pa.googleapis.com",
        "app-measurement.com", "app-measurement.net",
        // Telemetry / Crash Reporting (optional)
        "sentry.io", "o0.ingest.sentry.io",
        // Malware / Phishing
        "malware-traffic-analysis.net",
        "tracking.clicks4ads.com",
        "ad.doubleclick.net",
        "ad2.adservice.com",
        // Porn / Adult Ads
        "exoclick.com", "trafficjunky.net", "traffichaus.com",
        "plugrush.com", "juicyads.com", "adxpansion.com",
        // Misc
        "scorecardresearch.com", "pixel.quantserve.com",
        "counter.yadro.ru", "mc.yandex.ru",
        "cdn.ampproject.org",
        "stats.g.doubleclick.net"
    )

    fun isBlocked(host: String): Boolean {
        val lower = host.lowercase().trimEnd('.')
        if (blockedDomains.contains(lower)) return true
        var dot = lower.indexOf('.')
        while (dot != -1) {
            val sub = lower.substring(dot + 1)
            if (blockedDomains.contains(sub)) return true
            dot = lower.indexOf('.', dot + 1)
        }
        return false
    }
}
