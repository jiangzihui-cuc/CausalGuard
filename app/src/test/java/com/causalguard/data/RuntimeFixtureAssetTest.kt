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
        val docsFixture = repoPath("docs/fixtures/privacy-events-v0.1.json")
        val runtimeAsset = repoPath("app/src/main/assets/privacy-events-v0.1.json")
        check(docsFixture.isFile)
        check(runtimeAsset.isFile)
        val docsJson = docsFixture.readText()
        val assetJson = runtimeAsset.readText()

        assertEquals(docsJson.trim(), assetJson.trim())

        val events = ContractJson.instance.decodeFromString(
            ListSerializer(PrivacyEvent.serializer()),
            assetJson,
        )
        assertEquals(10, events.size)
        assertEquals(10, events.map { it.eventId }.toSet().size)
    }

    @Test
    fun runtimeRuleContextAndTemplatesMatchDocsCanonicalAssets() {
        assertDocsAssetMatchesRuntime("risk-rules-v0.1.json")
        assertDocsAssetMatchesRuntime("rule-input-context-v0.1.json")
        assertDocsAssetMatchesRuntime("explanation-templates-v0.1.json")
    }

    @Test
    fun expectedOracleIsNotCopiedToRuntimeAssets() {
        val assetsDir = repoPath("app/src/main/assets")
        check(assetsDir.isDirectory)
        val runtimeAssets = assetsDir.list()?.toSet().orEmpty()
        assertEquals(false, "privacy-events-v0.1.expected.json" in runtimeAssets)
    }

    private fun assertDocsAssetMatchesRuntime(name: String) {
        val docsAsset = repoPath("docs/fixtures/$name")
        val runtimeAsset = repoPath("app/src/main/assets/$name")
        check(docsAsset.isFile)
        check(runtimeAsset.isFile)
        assertEquals(
            docsAsset.readText().trim(),
            runtimeAsset.readText().trim(),
        )
    }

    private fun repoPath(relative: String): File {
        var directory = File(".").absoluteFile
        while (true) {
            val candidate = File(directory, relative)
            if (candidate.exists()) return candidate
            directory = directory.parentFile ?: break
        }
        error("repo path not found: $relative (cwd=${File(".").absolutePath})")
    }
}
