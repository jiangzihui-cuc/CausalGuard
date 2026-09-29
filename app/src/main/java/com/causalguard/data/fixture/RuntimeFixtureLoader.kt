package com.causalguard.data.fixture

import android.content.Context
import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.PrivacyEvent
import kotlinx.serialization.builtins.ListSerializer

object RuntimeFixtureLoader {
    const val ASSET_NAME: String = "privacy-events-v0.1.json"

    fun loadPrivacyEvents(context: Context): List<PrivacyEvent> {
        val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        return ContractJson.instance.decodeFromString(
            ListSerializer(PrivacyEvent.serializer()),
            json,
        )
    }
}
