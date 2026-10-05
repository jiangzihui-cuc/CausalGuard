package com.causalguard.data.tracker

import android.content.Context
import com.causalguard.core.model.ContractJson
import java.util.Collections
import kotlinx.serialization.Serializable

@Serializable
data class TrackerDataset(
    val schemaVersion: String,
    val source: TrackerDatasetSource,
    val entries: List<TrackerEntry>,
)

@Serializable
data class TrackerDatasetSource(
    val upstreamName: String,
    val upstreamDataset: String,
    val snapshotSource: String,
    val trackerControlCommit: String,
    val bundledAsset: String,
    val bundledAssetSha: String,
    val dataLicense: String,
    val generatedBy: String,
)

@Serializable
data class TrackerEntry(
    val domain: String,
    val category: String,
    val source: String,
    val entity: String? = null,
)

data class TrackerMatch(
    val queriedDomain: String,
    val matchedDomain: String,
    val category: String,
    val source: String,
    val entity: String?,
)

/** Loads the fixed, bundled B4-1 dataset and rejects malformed development assets. */
class TrackerDatasetLoader {

    fun load(context: Context): TrackerDataset {
        val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val dataset = ContractJson.instance.decodeFromString<TrackerDataset>(json)
        require(dataset.schemaVersion == SCHEMA_VERSION) {
            "Unsupported tracker dataset schema: ${dataset.schemaVersion}"
        }
        require(dataset.entries.isNotEmpty()) { "Tracker dataset entries must not be empty" }

        val normalizedEntries = dataset.entries.map { entry ->
            val normalizedDomain = requireNotNull(DomainNormalizer.normalize(entry.domain)) {
                "Invalid tracker domain: ${entry.domain}"
            }
            require(entry.category.isNotBlank()) { "Tracker category must not be blank" }
            require(entry.source.isNotBlank()) { "Tracker source must not be blank" }
            entry.copy(domain = normalizedDomain)
        }
        require(normalizedEntries.map { it.domain }.toSet().size == normalizedEntries.size) {
            "Tracker dataset contains duplicate normalized domains"
        }
        return dataset.copy(entries = normalizedEntries)
    }

    companion object {
        const val ASSET_NAME = "tracker-domains-v0.1.json"
        const val SCHEMA_VERSION = "tracker-domains-v0.1"
    }
}

/** Exact and DNS-label-boundary parent matching over an immutable normalized lookup map. */
class TrackerClassifier(entries: List<TrackerEntry>) {

    private val entriesByDomain: Map<String, TrackerEntry> = buildLookup(entries)

    fun classify(domainHint: String?): TrackerMatch? {
        val queriedDomain = DomainNormalizer.normalize(domainHint) ?: return null
        val labels = queriedDomain.split('.')
        for (start in labels.indices) {
            val candidate = labels.subList(start, labels.size).joinToString(".")
            val entry = entriesByDomain[candidate] ?: continue
            return TrackerMatch(
                queriedDomain = queriedDomain,
                matchedDomain = candidate,
                category = entry.category,
                source = entry.source,
                entity = entry.entity,
            )
        }
        return null
    }

    private fun buildLookup(entries: List<TrackerEntry>): Map<String, TrackerEntry> {
        require(entries.isNotEmpty()) { "Tracker classifier entries must not be empty" }
        val normalized = entries.map { entry ->
            val domain = requireNotNull(DomainNormalizer.normalize(entry.domain)) {
                "Invalid tracker domain: ${entry.domain}"
            }
            require(entry.category.isNotBlank()) { "Tracker category must not be blank" }
            require(entry.source.isNotBlank()) { "Tracker source must not be blank" }
            domain to entry.copy(domain = domain)
        }
        require(normalized.map { it.first }.toSet().size == normalized.size) {
            "Tracker classifier entries contain duplicate normalized domains"
        }
        return Collections.unmodifiableMap(normalized.toMap())
    }
}
