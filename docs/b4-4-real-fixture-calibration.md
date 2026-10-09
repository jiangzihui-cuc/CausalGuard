# B4-4 真实 Fixture 校准基线

## 1. Scope

本报告是 B4-4 第一切片：审计 A4-3 真实 network observed input，建立独立事实 oracle，并记录当前 `ObservationStatusResolver`、`TrackerClassifier` 和 `RuleEvaluator` 的实际行为。它不是 RiskAssessment oracle；本轮只对 R-008 做版本化规则校准。

本轮复用现有 `kotlinx.serialization`、B4-2 `DomainNormalizer`/`TrackerClassifier`、B4-3 `ObservationStatusResolver` 和当前 rule-engine；没有新增第三方依赖、PSL/domain 库、tracker 数据源或 TrackerControl 修改。

## 2. Provenance and integrity

Inputs:

- `docs/fixtures/real-network-events-a4-v0.1.json`
- `docs/fixtures/real-network-events-a4-v0.1.meta.json`

SHA-256:

- fixture: `a0bc150d2f58fb21849fd94223c5034566a933f70ddc224258ec5935f7a8bb0c`
- meta: `b9cd7ccd6ea40e75c262694bead20bb6a32072f218c7e2b3bef054fe22b732e2`

The meta identifies this as a 20-row real-observed-input sample from a PJW110 / Android 16 / API 36 A4-3 rerun on 2026-10-05, selected from `causalguard.db` with `originalEventCount=3374`. It is not a subset of the 2026-09-29 486-row capture; that earlier capture was lost after the app reinstall/uninstall cycle. The fixture carries no request body, account, token, cookie, URL path/query or raw privacy content.

The selected timestamps are uniformly shifted: first `0`, maximum `251869`, non-decreasing order, with inter-event intervals preserved. The meta reports a selected real span of `251869 ms` and full database span of `279862 ms`. The fixture keeps UID values for attribution calibration, but `uid >= 0` is not treated as App attribution.

The meta counts are consistent with the declared rerun: `attributed_package_known=2550`, `package_unknown=824`, `uid_0_package_unknown=60`. The selected `a4r-0019` row demonstrates the B4-3 rule directly: `uid=0` and `packageName=unknown` remains unattributed.

Remote IPs are documentation-range redactions only: IPv4 values are in `203.0.113.0/24`, IPv6 values are in `2001:db8::/32`. Event IDs are stable redacted IDs `a4r-XXXX`; no real event IDs are reconstructed.

## 3. Independent oracle

The fact-layer oracle is `docs/fixtures/real-network-events-a4-v0.1.oracle.json`. It records only:

- live provenance;
- domain visible/unavailable;
- App attribution available/unavailable;
- blocked/unblocked;
- tracker classification matched/unmatched/unavailable.

Its tracker reference is the fixed `app/src/main/assets/tracker-domains-v0.1.json`. Expected tracker values were statically checked against that asset using normalized exact/parent-label suffix semantics, not copied from a production classifier run. The oracle contains no expected risk level, score, category verdict, matched rule, recommendation, scenario match, privacy-leak claim, malicious claim or exfiltration claim.

### Evidence boundary

#### Observed Fact

The fixture records only real observed network metadata from the 2026-10-05 device run: event timing/order, protocol, redacted IP family, observed domain hint when available, UID/package attribution fields, and `blocked=true` when the base VPN observed a blocked connection or DNS attempt.

#### Derived Inference

`ObservationStatusResolver` and `TrackerClassifier` outputs are derived interpretations of those fields. A tracker match means only that a domain was classified by the fixed public dataset; `blocked=true` means only that the base observed a blocked network/parse attempt. Neither supports a privacy-leak, sensitive-data-sent, or malicious-behavior conclusion. The R-008 change is a rule-design calibration for attribution-unavailable semantics, not a new device fact.

#### Synthetic Evaluation Input

For rule regression, each real event is wrapped as `RuleInput(event=event)` with no invented App profile, usage context, scenario match, related event, or prior event. The frozen synthetic v0.1 fixtures and their oracle remain separate and are not mixed into this real observed input.

#### Unavailable / Unknown

Missing `domainHint`, `packageName=unknown`, and unmatched tracker classification remain explicitly unavailable/unknown. No IP-based tracker inference is performed, and absent request bodies, payloads, URL paths, cookies, tokens, and raw privacy content remain unobservable.

## 4. Coverage and observation status

| Dimension | Count |
|---|---:|
| events | 20 |
| network / VPN / E2 / non-demo | 20 |
| domain visible | 15 |
| domain unavailable | 5 |
| attributed to an App (`packageName` meaningful and not `unknown`) | 14 |
| attribution unavailable | 6 |
| blocked | 5 |
| unblocked | 15 |
| TCP | 17 |
| UDP | 2 |
| ICMP | 1 |

All 20 events resolve to `LIVE_OBSERVED`. Five events have `DOMAIN_UNAVAILABLE`; six have `APP_ATTRIBUTION_UNAVAILABLE`. Missing domain or attribution does not mechanically downgrade the event's `EvidenceLevel=E2` to E5: these remain VPN-observed connection metadata.

## 5. TrackerClassifier calibration

The fixed 100-entry Disconnect-derived asset does not match any of the 15 visible domains in this selected sample. Results:

| Tracker result | Count |
|---|---:|
| matched | 0 |
| unmatched | 15 |
| unavailable (no domain) | 5 |

Matched domain/category summary: none. Therefore no Disconnect category or entity is reported for this sample. The absence of a match is not evidence that the observed service is benign; it only means this fixed 100-entry offline list had no exact or parent-label match. Disconnect categories are public dataset classification facts, not `RiskCategory`, privacy-leak findings or malicious verdicts.

No IP-based tracker inference is performed.

## 6. Tracker dataset coverage diagnosis

The complete fixed upstream input is the local reversed snapshot `third_party/tracker-control-android/app/src/main/assets/disconnect-blacklist.reversed.json`. Reversing its bytes yields the pinned `services.json` structure with 11 categories, 4,800 raw list values and 4,447 unique values that pass the generator's safe exact-domain normalization. No floating upstream data was downloaded.

The runtime asset check uses the production classifier's exact/parent-label suffix semantics:

| Scope | Result |
|---|---:|
| visible real domain events | 15 |
| runtime asset exact/parent matches | 0/15 |
| complete upstream exact/parent matches | 1/15 |
| distinct visible domain names | 14 |
| distinct upstream exact/parent matches | 1/14 |

The one upstream match is `o4510860521897984.ingest.us.sentry.io` via parent `sentry.io`, classified by the upstream as `Analytics` / entity `FunctionalSoftware`. `sentry.io` is not present in the 100-entry runtime asset, so this event is a sampling/selection coverage gap. The other 14 visible event occurrences (13 distinct domain names; `edith.xiaohongshu.com` occurs twice) have no exact or parent-label match in the complete upstream source, so they are source coverage gaps. The overall `0/15` result is therefore `mixed`, not a pure sampling failure.

The four blocked domain rows are all unmatched in both layers:

| blocked domain | runtime asset | complete upstream |
|---|---|---|
| `api-access.pangolin-sdk-toutiao.com` | unmatched | unmatched |
| `cpro.baidustatic.com` | unmatched | unmatched |
| `api.m.jd.com` | unmatched | unmatched |
| `interface3.music.163.com` | unmatched | unmatched |

The blocked UDP:53 row `a4r-0019` has no `domainHint`, so tracker classification is `unavailable`. These are three separate facts: TrackerControl's base block list observed `blocked=true`; the CausalGuard 100-entry asset may or may not contain a domain; and `TrackerClassifier` may or may not match it. The first fact does not imply either of the latter two.

The generator is deterministic but not coverage-oriented. It reads only the pinned reversed snapshot, reverses and parses JSON, validates the Disconnect license/categories, trims/lowercases/removes one trailing dot, rejects wildcard/regex/unsafe/IP/non-domain values, and deduplicates domains within each category using the lexicographically smallest entity. It sorts each category pool by normalized `(domain, entity)`, then takes one entry per category in sorted category order round-robin until the target of 100 entries, globally skipping duplicate domains. The final output is sorted by `(domain, category, entity)`. This category-balanced deterministic small sample can exclude common real domains; it is not a coverage-oriented catalog.

Because the selected real sample has zero runtime classifier matches, there is no evidence that a tracker-fact to `RuleInput` bridge is the current blocker. The bridge remains a candidate only after dataset coverage is deliberately addressed; no bridge is implemented here.

## 7. Current rule-engine behavior

Each event was evaluated with the independently constructed minimal input `RuleInput(event=event)`; no app profile, usage context, scenario match, related event or prior event was invented.

The frozen v0.1 evaluator remains a historical regression: it matches R-008 on 5 rows and does not match the `uid=0` / `packageName=unknown` row. The current runtime uses v0.2:

| Current v0.2 output group | Count | Actual behavior |
|---|---:|---|
| `R-008` | 6 | `riskLevel=low`, `category=unknown`, `scenarioMatch=unknown`, `confidence=low`, action `none`, unknown degradation shown |
| no rule | 14 | `riskLevel=low`, `category=unknown`, `scenarioMatch=unknown`, `confidence=low`, action `none`, no unknown degradation |
| `R-005` | 0 | No selected real domain equals the two synthetic R-005 domain hints |

### R-008 calibration decision

Observed fact: `a4r-0019` has `packageName=unknown` and `uid=0`. A known UID does not imply attribution to a concrete App. Therefore v0.2 R-008 retains `eventTypes=["network"]` and `packageName="unknown"`, but removes the `uid=-1` requirement. Its result remains `LOW / UNKNOWN / UNKNOWN / LOW`, recommendation action `none`, with unknown degradation shown. This improves honest degradation for the real fixture and does not increase risk.

The five historical `uid=-1` / `packageName=unknown` rows still match R-008, and known package rows do not match R-008 solely because of UID. No blocked event receives a high-risk result or a non-`none` action under this event-only input. Tracker coverage is not fixed in this slice: 14/15 visible event occurrences are source coverage gaps and 1/15 is a sampling/selection gap. No tracker fact to `RuleInput` bridge is implemented.

## 8. Safety invariants

- Unknown package attribution is never rendered as a specific App fact; `packageName=unknown` remains unavailable even when `uid=0`.
- Missing `domainHint` never produces a tracker match from `remoteIp`.
- `blocked=true` is retained as a real blocked connection/parse attempt, not converted into leakage, high risk or maliciousness.
- A future tracker match would remain a public dataset classification fact; this slice performs no category-to-`RiskCategory` coercion.
- The real sample does not cause `R-005` to fire. There is no observed classifier-to-rule-input bridge gap in this sample because there are no classifier matches to pass through. The broader bridge remains a candidate design concern if future real matches are introduced: tracker facts should enter `RuleInput` explicitly rather than being coerced into risk categories.

## 9. Calibration gaps

| Class | Finding |
|---|---|
| fact-layer gap | Five rows have no domain and six have no App attribution; the observed metadata remains valid but incomplete. |
| classifier gap | The fixed 100-entry sample has no match for the 15 visible domains; coverage should be measured before treating unmatched as absence. |
| rule-input gap | No current sample match exists, so no runtime bridge failure is demonstrated; a future tracker-fact input contract is still needed before using tracker facts in rules. |
| rule-semantic gap | Current rules intentionally know only synthetic R-005 domains and do not map Disconnect categories to risk categories. This slice does not change that semantic boundary. |

## 10. Candidate next slice

Define and review a rule-input bridge for an explicit, provenance-preserving tracker fact (matched domain, dataset category, source and optional entity), then add a separate real-domain rule calibration case. Do not coerce Disconnect categories directly into `RiskCategory`, and do not add the bridge until its unknown and blocked semantics are agreed.

## 11. Verification note

Static JSON/provenance validation passed. The calibration test uses the existing Robolectric local-unit-test pattern and the single requested `:app:testDebugUnitTest` run passed: 98 tests completed, including all 5 calibration tests. Runtime now loads v0.2 rule/template assets; v0.1 remains byte-for-byte available for historical regression. The frozen synthetic event/context/oracle and tracker assets were not modified.
