package com.taroqos.filter

object AdBlockList {

    val blockedDomains: Set<String> = setOf(
        // ═══ Google Ads ═══
        "googleadservices.com","googlesyndication.com","googletagmanager.com",
        "googletagservices.com","doubleclick.net","ad.doubleclick.net",
        "pagead2.googlesyndication.com","tpc.googlesyndication.com",
        "adservice.google.com","partner.googleadservices.com",
        "adservice.google.ae","adservice.google.co","adservice.google.de",
        "adservice.google.fr","adservice.google.co.uk","adservice.google.com.sa",
        "stats.g.doubleclick.net","cm.g.doubleclick.net","securepubads.g.doubleclick.net",
        // ═══ Facebook / Meta Ads ═══
        "an.facebook.com","www.facebook.com","connect.facebook.net",
        "staticxx.facebook.com","edge-mqtt.facebook.com","graph.facebook.com",
        "pixel.facebook.com","analytics.facebook.com","ads.facebook.com",
        "scontent.facebook.com",
        // ═══ Instagram Ads ═══
        "ads.instagram.com","analytics.instagram.com",
        // ═══ Amazon Ads ═══
        "aax.amazon-adsystem.com","fls-na.amazon-adsystem.com",
        "s.amazon-adsystem.com","mads.amazon-adsystem.com",
        "adsystem.amazon.com","ads.amazon.com",
        // ═══ Twitter / X Ads ═══
        "ads.twitter.com","analytics.twitter.com","ads-twitter.com",
        "t.co","syndication.twitter.com","ads.x.com",
        // ═══ TikTok Ads ═══
        "ads.tiktok.com","analytics.tiktok.com","mon.musical.ly",
        "log-va.tiktokv.com","log-sg.tiktokv.com","ads-api.tiktok.com",
        "business.tiktok.com","ad.tiktok.com",
        // ═══ Snapchat Ads ═══
        "ads.snapchat.com","tr.snapchat.com","sc-static.net",
        "snap.com","businesshelp.snapchat.com",
        // ═══ AppNexus / Xandr ═══
        "appnexus.com","ib.adnxs.com","secure.adnxs.com","adnxs.com",
        "adnxs.net","prebid.adnxs.com",
        // ═══ Rubicon / Magnite ═══
        "rubiconproject.com","fastlane.rubiconproject.com","prebid.rubiconproject.com",
        // ═══ OpenX ═══
        "openx.net","servedby.openx.net","ox-d.openx.net","us-u.openx.net",
        // ═══ PubMatic ═══
        "pubmatic.com","ads.pubmatic.com","image6.pubmatic.com",
        // ═══ Criteo ═══
        "criteo.com","dis.criteo.com","rtax.criteo.com","cat.nl.eu.criteo.com",
        "static.criteo.net","gum.criteo.com",
        // ═══ Outbrain ═══
        "outbrain.com","odb.outbrain.com","traffic.outbrain.com",
        // ═══ Taboola ═══
        "taboola.com","trc.taboola.com","cdn.taboola.com","api.taboola.com",
        // ═══ Moat Analytics ═══
        "moatads.com","z.moatads.com","px.moatads.com",
        // ═══ IAS (Integral Ad Science) ═══
        "adsafeprotected.com","pixel.adsafeprotected.com","fw.adsafeprotected.com",
        // ═══ Yahoo Ads ═══
        "ads.yahoo.com","adtech.de","advertising.com","aolcdn.com",
        "yahooapis.com","gemini.yahoo.com",
        // ═══ Analytics & Tracking ═══
        "hotjar.com","app.hotjar.com","static.hotjar.com",
        "segment.com","api.segment.io","cdn.segment.com",
        "amplitude.com","api.amplitude.com","cdn.amplitude.com",
        "mixpanel.com","api.mixpanel.com","cdn4.mxpnl.com",
        "heap.io","heapanalytics.com","cdn.heapanalytics.com",
        "fullstory.com","rs.fullstory.com","edge.fullstory.com",
        "mouseflow.com","a.mouseflow.com",
        "appsflyer.com","api2.appsflyer.com","t.appsflyer.com","d.appsflyer.com",
        "adjust.com","app.adjust.com","s2s.adjust.com","view.adjust.com",
        "branch.io","api2.branch.io","app.link","bnc.lt",
        "app-measurement.com","app-measurement.net",
        "firebaselogging-pa.googleapis.com","firebase-settings.crashlytics.com",
        // ═══ Sentry / Crash Analytics ═══
        "sentry.io","o0.ingest.sentry.io","ingest.sentry.io",
        // ═══ Scorecard / ComScore ═══
        "scorecardresearch.com","pixel.quantserve.com","b.scorecardresearch.com",
        // ═══ Mopub (Twitter) ═══
        "mopub.com","ads.mopub.com",
        // ═══ Verizon Media ═══
        "nexage.com","beachfront.com","mmedia.com","mobad.com",
        // ═══ AdColony ═══
        "adcolony.com","adc3-launch.adcolony.com","events3.adcolony.com",
        // ═══ Chartboost ═══
        "chartboost.com","live.chartboost.com",
        // ═══ IronSource ═══
        "ironsrc.com","mobile.ironsrc.com","api.ironsrc.com",
        "is.mopub.com","ad.ironsrc.com",
        // ═══ Unity Ads ═══
        "unityads.unity3d.com","config.unityads.unity3d.com",
        "auction.unityads.unity3d.com","publisher.unityads.unity3d.com",
        // ═══ Vungle ═══
        "vungle.com","ads.vungle.com","api.vungle.com","cdn-lb.vungle.com",
        // ═══ AdMob ═══
        "admob.com","googleadmob.com","mediation.admob.com",
        // ═══ MoPub / Twitter ═══
        "ads.twitter.com","mopub.com",
        // ═══ Yandex Ads ═══
        "mc.yandex.ru","counter.yadro.ru","an.yandex.ru","yandex-metrica.com",
        // ═══ Snap Audience Network ═══
        "adsapi.snapchat.com","tr.snapchat.com",
        // ═══ Yahoo Flurry ═══
        "flurry.com","data.flurry.com","api.flurry.com",
        // ═══ Digital Turbine ═══
        "digitalturbine.com","loam.dtignite.com","ignite.digitalturbine.com",
        // ═══ Inmobi ═══
        "inmobi.com","d3b9j1n5stb5o7.cloudfront.net","w.inmobi.com",
        // ═══ Millennial Media ═══
        "millennialmedia.com","ads.millennialmedia.com",
        // ═══ Oath/AOL ═══
        "oath.com","advertising.com","beap.gemini.yahoo.com",
        // ═══ Pontiflex ═══
        "pontiflex.com","api.pontiflex.com",
        // ═══ SmartAdServer ═══
        "smartadserver.com","prg.smartadserver.com","diff.smartadserver.com",
        // ═══ TripleLift ═══
        "triplelift.com","tlx.3lift.com","eb2.3lift.com",
        // ═══ Sharethrough ═══
        "sharethrough.com","btlr.sharethrough.com","native.sharethrough.com",
        // ═══ Sovrn ═══
        "lijit.com","ex.lijit.com","sovrn.com",
        // ═══ Conversant / Epsilon ═══
        "conversantmedia.com","epsilon.com","dotomi.com",
        // ═══ Trade Desk ═══
        "thetradedesk.com","match.adsrvr.org","insight.adsrvr.org",
        // ═══ MediaMath ═══
        "mediamath.com","t.appsflyer.com","pixel.mathtag.com",
        // ═══ Pinterest Ads ═══
        "analytics.pinterest.com","ads.pinterest.com","ct.pinterest.com",
        // ═══ LinkedIn Ads ═══
        "ads.linkedin.com","analytics.pointdrive.linkedin.com","snap.licdn.com",
        // ═══ Spotify Ads ═══
        "adeventtracker.spotify.com","spclient.wg.spotify.com",
        // ═══ Generic Ad Networks ═══
        "adform.net","track.adform.net","adform.com",
        "advertising.com","adtech.de","adx.opera.com",
        "2mdn.net","g.doubleclick.net","stats.g.doubleclick.net",
        // ═══ Tracking Pixels ═══
        "pixel.wp.com","pixel.newrelic.com","pixel.advertising.com",
        "pixel.adsafeprotected.com","pixel.moatads.com","p.typekit.net",
        // ═══ Adult Ad Networks ═══
        "exoclick.com","trafficjunky.net","traffichaus.com",
        "plugrush.com","juicyads.com","adxpansion.com","ero-advertising.com",
        "revcontent.com","adnium.com","reporo.net","TrafficFactory.biz"
    )

    private val blockedPatterns = listOf(
        Regex("^ads?\\..*"),
        Regex("^ad\\..*"),
        Regex("^tracking\\..*"),
        Regex("^tracker\\..*"),
        Regex("^analytics\\..*"),
        Regex("^pixel\\..*"),
        Regex("^telemetry\\..*"),
        Regex(".*\\.ads\\..*"),
        Regex(".*-ads\\..*"),
        Regex(".*adserver.*"),
        Regex(".*adservice.*"),
        Regex(".*adnetwork.*"),
        Regex(".*\\.ad\\..*")
    )

    fun isBlocked(host: String): Boolean {
        val lower = host.lowercase().trimEnd('.')
        if (lower.isEmpty()) return false
        if (blockedDomains.contains(lower)) return true
        var dot = lower.indexOf('.')
        while (dot != -1) {
            if (blockedDomains.contains(lower.substring(dot + 1))) return true
            dot = lower.indexOf('.', dot + 1)
        }
        return blockedPatterns.any { it.matches(lower) }
    }
}
