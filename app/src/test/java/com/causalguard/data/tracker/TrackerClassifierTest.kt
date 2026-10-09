package com.causalguard.data.tracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackerClassifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun normalizerRejectsNullAndBlank() {
        assertNull(DomainNormalizer.normalize(null))
        assertNull(DomainNormalizer.normalize("  \t"))
    }

    @Test
    fun normalizerTrimsLowercasesAndRemovesOneTrailingDot() {
        assertEquals("example.com", DomainNormalizer.normalize("  Example.COM.  "))
        assertNull(DomainNormalizer.normalize("example.com.."))
    }

    @Test
    fun normalizerConvertsUnicodeHostToAscii() {
        assertEquals("xn--bcher-kva.example", DomainNormalizer.normalize("BÜCHER.Example"))
    }

    @Test
    fun normalizerRejectsEmptyLabelsAndMalformedHostnames() {
        assertNull(DomainNormalizer.normalize("example..com"))
        assertNull(DomainNormalizer.normalize("-example.com"))
        assertNull(DomainNormalizer.normalize("example-.com"))
        assertNull(DomainNormalizer.normalize("a_analytics.example.com"))
    }

    @Test
    fun normalizerRejectsWildcardRegexUrlPathUserinfoAndPort() {
        listOf(
            "*.example.com",
            "[a-z].example.com",
            "https://example.com/path",
            "example.com/path",
            "user@example.com",
            "example.com:443",
        ).forEach { assertNull(DomainNormalizer.normalize(it)) }
    }

    @Test
    fun normalizerRejectsIpv4AndIpv6() {
        assertNull(DomainNormalizer.normalize("192.0.2.1"))
        assertNull(DomainNormalizer.normalize("2001:db8::1"))
        assertNull(DomainNormalizer.normalize("[2001:db8::1]"))
    }

    @Test
    fun normalizerRejectsOverlongDomainAndLabel() {
        assertNull(DomainNormalizer.normalize("a".repeat(64) + ".example"))
        assertNull(DomainNormalizer.normalize(("a.".repeat(127)) + "a"))
    }

    @Test
    fun classifierMatchesExactDomainAndPreservesMetadata() {
        val classifier = TrackerClassifier(
            listOf(TrackerEntry("tracker.example", "Analytics", "disconnect", "Example Inc")),
        )

        val match = classifier.classify("TRACKER.EXAMPLE")

        assertEquals(
            TrackerMatch("tracker.example", "tracker.example", "Analytics", "disconnect", "Example Inc"),
            match,
        )
    }

    @Test
    fun classifierNormalizesUppercaseAndTrailingDotQuery() {
        val classifier = TrackerClassifier(
            listOf(TrackerEntry("tracker.example", "Analytics", "disconnect")),
        )

        assertEquals("tracker.example", classifier.classify(" TRACKER.EXAMPLE. ")?.queriedDomain)
    }

    @Test
    fun classifierMatchesOneAndMultiLevelSubdomains() {
        val classifier = TrackerClassifier(
            listOf(TrackerEntry("example.com", "Analytics", "disconnect")),
        )

        assertEquals("example.com", classifier.classify("api.example.com")?.matchedDomain)
        assertEquals("example.com", classifier.classify("a.b.example.com")?.matchedDomain)
    }

    @Test
    fun classifierChoosesLongestMostSpecificDomain() {
        val classifier = TrackerClassifier(
            listOf(
                TrackerEntry("example.com", "Analytics", "disconnect"),
                TrackerEntry("b.example.com", "Advertising", "disconnect"),
            ),
        )

        assertEquals("b.example.com", classifier.classify("a.b.example.com")?.matchedDomain)
        assertEquals("example.com", classifier.classify("a.example.com")?.matchedDomain)
    }

    @Test
    fun classifierRespectsLabelBoundaries() {
        val classifier = TrackerClassifier(
            listOf(TrackerEntry("example.com", "Analytics", "disconnect")),
        )

        assertNull(classifier.classify("notexample.com"))
        assertNull(classifier.classify("evil-example.com"))
    }

    @Test
    fun classifierReturnsNullForUnknownAndInvalidDomains() {
        val classifier = TrackerClassifier(
            listOf(TrackerEntry("example.com", "Analytics", "disconnect")),
        )

        assertNull(classifier.classify("unknown.test"))
        assertNull(classifier.classify("https://example.com/path"))
        assertNull(classifier.classify(null))
    }

    @Test
    fun loaderParsesTheRealHundredEntryAsset() {
        val dataset = TrackerDatasetLoader().load(context)

        assertEquals(TrackerDatasetLoader.SCHEMA_VERSION, dataset.schemaVersion)
        assertEquals(100, dataset.entries.size)
        assertEquals(100, dataset.entries.map { it.domain }.toSet().size)
        assertTrue(dataset.entries.all { it.category.isNotBlank() && it.source.isNotBlank() })
        assertEquals("CC BY-NC-SA 4.0", dataset.source.dataLicense)
    }

    @Test
    fun realAssetSupportsExactSubdomainAndUnknownLookups() {
        val dataset = TrackerDatasetLoader().load(context)
        val entry = dataset.entries.first()
        val classifier = TrackerClassifier(dataset.entries)

        assertEquals(entry.domain, classifier.classify(entry.domain)?.matchedDomain)
        assertEquals(entry.domain, classifier.classify("api.${entry.domain}")?.matchedDomain)
        assertNull(classifier.classify("not-a-known-tracker.example"))
    }
}
