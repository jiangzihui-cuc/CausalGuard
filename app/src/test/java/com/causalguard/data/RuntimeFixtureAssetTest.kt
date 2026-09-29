package com.causalguard.data

import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.PrivacyEvent
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeFixtureAssetTest {

    @Test
    fun runtimeAssetMatchesDocsFixtureAndParsesToTenUniqueEvents() {
        val docsJson = repoFile("docs/fixtures/privacy-events-v0.1.json").readText()
        val assetJson = repoFile("app/src/main/assets/privacy-events-v0.1.json").readText()

        assertEquals(docsJson.trim(), assetJson.trim())

        val events = ContractJson.instance.decodeFromString(
            ListSerializer(PrivacyEvent.serializer()),
            assetJson,
        )
        assertEquals(10, events.size)
        assertEquals(10, events.map { it.eventId }.toSet().size)
    }

    private fun repoFile(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("../$relative"),
            File("../../$relative"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("fixture not found: $relative (cwd=${File(".").absolutePath})")
    }
}
