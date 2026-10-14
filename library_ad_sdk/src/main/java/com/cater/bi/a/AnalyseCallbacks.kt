package com.cater.bi.a

import com.cater.bi.analyzer.AdAnalyzer
import com.cater.bi.e.Eve

/**
 *
 * created on 2025/8/31
 * @author holmes
 */

class AnalyseLoadCallback(down: AdLoadCallback?) : AdLoadCallbackWrap(down) {

    override fun onSuccess(placementName: String, ad: AdBase) {
        // Analytics must run before application callbacks. A callback belongs
        // to page code and may throw; it must not make a real ad result vanish
        // from Firebase.
        runCatching {
            Eve.send(
                "ad_loaded", mapOf(
                    "scenario_code" to ad.metadata.placement.id,
                    "unit_code" to ad.getUnitCode(),
                    "ad_format" to ad.getAdType(),
                    "ad_platform" to ad.getPlatform(),
                    "ecpm" to ad.getEcpm(),
                    "load_duration" to ((ad as? AbstractAd)?.getLoadDuration() ?: 0L),
                )
            )
        }
        super.onSuccess(placementName, ad)
    }

    override fun onFailure(placementName: String, error: AdErrorC) {
        val ad = error.adObj as? AdBase
        runCatching {
            Eve.send(
                "ad_load_fail", mapOf(
                    "scenario_code" to placementName,
                    "err_code" to error.code,
                    "err_msg" to error.message,
                    "unit_code" to (ad?.getUnitCode() ?: ""),
                    "ad_format" to (ad?.getAdType() ?: ""),
                    "ad_platform" to (ad?.getPlatform() ?: ""),
                    "load_duration" to ((ad as? AbstractAd)?.getLoadDuration() ?: 0L),
                )
            )
        }
        super.onFailure(placementName, error)
    }

}

class AnalyseShowCallback(down: AdShowCallback?) : AdShowCallbackWrap(down) {
    override fun onSuccess(placementName: String, ad: AdBase) {
        runCatching {
            AdAnalyzer.recordImpression(ad)
        }
        runCatching {
            val revenue = ad.getRevenue()

            if (revenue == null) {
                Eve.send(
                    "ad_impression_sa", mapOf(
                        "scenario_code" to ad.metadata.placement.id,
                        "unit_code" to ad.getUnitCode(),
                        "ecpm" to ad.getEcpm(),
                        "value" to (ad.getEcpm() / 1000.0),
                        "currency" to "USD",
                        "ad_format" to ad.getAdType(),
                        "ad_platform" to ad.getPlatform(),
                        "load_duration" to ((ad as? AbstractAd)?.getLoadDuration() ?: 0L),
                        "show_duration" to ((ad as? AbstractAd)?.getShowDuration() ?: 0L),
                    )
                )
            } else {
                Eve.send(
                    "ad_impression_sa", mapOf(
                        "scenario_code" to ad.metadata.placement.id,
                        "unit_code" to ad.getUnitCode(),
                        "ecpm" to ad.getEcpm(),

                        "value" to revenue.value,
                        "currency" to revenue.currency,
                        "ad_format" to revenue.adType,
                        "ad_source" to revenue.network,
                        "ad_unit_name" to revenue.unitId,
                        "ad_platform" to revenue.plat,

                        "load_duration" to ((ad as? AbstractAd)?.getLoadDuration() ?: 0L),
                        "show_duration" to ((ad as? AbstractAd)?.getShowDuration() ?: 0L),
                    )
                )
            }
        }
        super.onSuccess(placementName, ad)
    }

    override fun onFailure(placementName: String, error: AdErrorC) {
        val ad = error.adObj as? AdBase
        runCatching {
            if (ad != null) {
                Eve.send(
                    "ad_show_fail", mapOf(
                        "scenario_code" to ad.metadata.placement.id,
                        "unit_code" to ad.getUnitCode(),
                        "ad_format" to ad.getAdType(),
                        "ad_platform" to ad.getPlatform(),
                        "ecpm" to ad.getEcpm(),
                        "load_duration" to ((ad as? AbstractAd)?.getLoadDuration() ?: 0L),
                        "show_duration" to ((ad as? AbstractAd)?.getShowDuration() ?: 0L),
                        "err_code" to error.code,
                        "err_msg" to error.message,
                    )
                )
            } else {
                Eve.send(
                    "ad_show_fail", mapOf(
                        "scenario_code" to placementName,
                        "err_code" to error.code,
                        "err_msg" to error.message,
                    )
                )
            }
        }
        super.onFailure(placementName, error)
    }

    override fun onClicked(placementName: String, ad: AdBase) {
        runCatching {
            val revenue = ad.getRevenue()
            Eve.send(
                "ad_clicked", mapOf(
                    "scenario_code" to ad.metadata.placement.id,
                    "unit_code" to ad.getUnitCode(),
                    "ad_format" to ad.getAdType(),
                    "ad_platform" to ad.getPlatform(),
                    "ecpm" to ad.getEcpm(),
                    "value" to (revenue?.value ?: (ad.getEcpm() / 1000.0)),
                    "currency" to (revenue?.currency ?: "USD"),
                    "load_duration" to ((ad as? AbstractAd)?.getLoadDuration() ?: 0L),
                    "show_duration" to ((ad as? AbstractAd)?.getShowDuration() ?: 0L),
                    "clicked_count" to ((ad as? AbstractAd)?.getClickedCount() ?: 0),
                    "clicked_duration" to ((ad as? AbstractAd)?.getClickedDuration() ?: 0L),
                )
            )
        }
        super.onClicked(placementName, ad)
    }

    override fun onClosed(placementName: String, ad: AdBase) {
        super.onClosed(placementName, ad)
    }

    override fun onRewarded(placementName: String, ad: AdBase) {
        super.onRewarded(placementName, ad)
    }
}
