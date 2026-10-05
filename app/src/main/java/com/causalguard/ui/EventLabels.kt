package com.causalguard.ui

import com.causalguard.analysis.EventProvenance
import com.causalguard.analysis.ObservationIssue
import com.causalguard.analysis.ObservationStatusResolver
import com.causalguard.core.model.PrivacyEvent

internal fun PrivacyEvent.provenanceLabel(): String =
    ObservationStatusResolver.forEvent(this).provenance.label

internal fun PrivacyEvent.provenance(): EventProvenance =
    ObservationStatusResolver.forEvent(this).provenance

internal fun PrivacyEvent.domainDisplayLabel(): String {
    val network = requireNotNull(this.network) { "Domain label requires a network event" }
    return if (ObservationIssue.DOMAIN_UNAVAILABLE in ObservationStatusResolver.forEvent(this).issues) {
        ObservationIssue.DOMAIN_UNAVAILABLE.label
    } else {
        network.domainHint.orEmpty()
    }
}

internal fun PrivacyEvent.attributionDisplayLabel(): String {
    val network = requireNotNull(this.network) { "Attribution label requires a network event" }
    return if (ObservationIssue.APP_ATTRIBUTION_UNAVAILABLE in ObservationStatusResolver.forEvent(this).issues) {
        ObservationIssue.APP_ATTRIBUTION_UNAVAILABLE.label
    } else {
        network.packageName
    }
}
