package com.causalguard.ui

import com.causalguard.core.model.PrivacyEvent

internal fun PrivacyEvent.provenanceLabel(): String =
    if (isDemo) "Demo" else "Fixture"
