#!/usr/bin/env python3
"""Generate CausalGuard's deterministic Disconnect domain snapshot."""

from __future__ import annotations

import argparse
import hashlib
import ipaddress
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INPUT = (
    ROOT
    / "third_party"
    / "tracker-control-android"
    / "app"
    / "src"
    / "main"
    / "assets"
    / "disconnect-blacklist.reversed.json"
)
DEFAULT_OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "tracker-domains-v0.1.json"
TRACKER_CONTROL_COMMIT = "9504d41b9f6fa1509d784e5503c084d4b428307d"
EXPECTED_ASSET_BLOB_SHA = "6fa1d74b3dd74a174fe1a90af5d2b59edd7865dc"
MIN_ENTRIES = 50
MAX_ENTRIES = 200
TARGET_ENTRIES = 100
DOMAIN_LABEL = re.compile(r"^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
UNSAFE_DOMAIN_CHARS = set("/\\:[](){}|+?^$")


def git_blob_sha(payload: bytes) -> str:
    header = f"blob {len(payload)}\0".encode("ascii")
    return hashlib.sha1(header + payload).hexdigest()


def normalise_domain(value: object) -> str | None:
    if not isinstance(value, str):
        return None
    domain = value.strip().lower().rstrip(".")
    if not domain or any(character.isspace() for character in domain):
        return None
    if "*" in domain or any(character in UNSAFE_DOMAIN_CHARS for character in domain):
        return None
    try:
        ipaddress.ip_address(domain)
    except ValueError:
        pass
    else:
        return None
    labels = domain.split(".")
    if len(labels) < 2 or not all(DOMAIN_LABEL.fullmatch(label) for label in labels):
        return None
    return domain


def load_snapshot(path: Path) -> tuple[dict, str]:
    payload = path.read_bytes()
    blob_sha = git_blob_sha(payload)
    if blob_sha != EXPECTED_ASSET_BLOB_SHA:
        raise ValueError(
            f"unexpected bundled asset blob SHA: {blob_sha} != {EXPECTED_ASSET_BLOB_SHA}"
        )
    try:
        root = json.loads(payload.decode("utf-8")[::-1])
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"bundled asset is not reversed services.json: {error}") from error
    if not isinstance(root, dict):
        raise ValueError("services.json root must be an object")
    license_text = root.get("license")
    if not isinstance(license_text, str):
        raise ValueError("services.json license field is missing or not a string")
    if "Disconnect, Inc." not in license_text:
        raise ValueError("services.json license does not identify Disconnect, Inc.")
    if "Creative Commons Attribution-NonCommercial-ShareAlike 4.0" not in license_text:
        raise ValueError("services.json license is not CC BY-NC-SA 4.0")
    categories = root.get("categories")
    if not isinstance(categories, dict) or not categories:
        raise ValueError("services.json categories must be a non-empty object")
    return root, blob_sha


def candidates_by_category(categories: dict) -> dict[str, list[tuple[str, str]]]:
    result: dict[str, list[tuple[str, str]]] = {}
    for category, services in categories.items():
        if not isinstance(category, str) or not category.strip():
            raise ValueError("services.json contains an empty category")
        if not isinstance(services, list):
            raise ValueError(f"category {category!r} is not an array")
        candidates: dict[str, str] = {}
        for service in services:
            if not isinstance(service, dict):
                raise ValueError(f"category {category!r} contains a non-object service")
            for entity, details in service.items():
                if not isinstance(entity, str) or not entity.strip() or not isinstance(details, dict):
                    raise ValueError(f"category {category!r} contains malformed service data")
                for values in details.values():
                    if not isinstance(values, list):
                        continue
                    for value in values:
                        domain = normalise_domain(value)
                        if domain is not None:
                            candidates[domain] = min(entity, candidates.get(domain, entity))
        result[category] = sorted((domain, entity) for domain, entity in candidates.items())
        if not result[category]:
            raise ValueError(f"category {category!r} has no exact domains")
    return result


def select_entries(candidates: dict[str, list[tuple[str, str]]]) -> list[dict[str, str]]:
    pools = {category: list(values) for category, values in sorted(candidates.items())}
    selected: list[dict[str, str]] = []
    used_domains: set[str] = set()
    while len(selected) < TARGET_ENTRIES:
        made_progress = False
        for category in pools:
            while pools[category]:
                domain, entity = pools[category].pop(0)
                if domain in used_domains:
                    continue
                used_domains.add(domain)
                selected.append(
                    {
                        "domain": domain,
                        "category": category,
                        "source": "disconnect",
                        "entity": entity,
                    }
                )
                made_progress = True
                break
            if len(selected) == TARGET_ENTRIES:
                break
        if not made_progress:
            break
    if not MIN_ENTRIES <= len(selected) <= MAX_ENTRIES:
        raise ValueError(f"selected {len(selected)} entries, expected {MIN_ENTRIES}..{MAX_ENTRIES}")
    return sorted(selected, key=lambda entry: (entry["domain"], entry["category"], entry["entity"]))


def build_document(entries: list[dict[str, str]], blob_sha: str) -> dict:
    return {
        "schemaVersion": "tracker-domains-v0.1",
        "source": {
            "upstreamName": "Disconnect Tracking Protection",
            "upstreamDataset": "services.json",
            "snapshotSource": "TrackerControl bundled asset",
            "trackerControlCommit": TRACKER_CONTROL_COMMIT,
            "bundledAsset": "third_party/tracker-control-android/app/src/main/assets/disconnect-blacklist.reversed.json",
            "bundledAssetSha": blob_sha,
            "dataLicense": "CC BY-NC-SA 4.0",
            "generatedBy": "scripts/generate-tracker-dataset.py",
        },
        "entries": entries,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    try:
        root, blob_sha = load_snapshot(args.input)
        entries = select_entries(candidates_by_category(root["categories"]))
        document = build_document(entries, blob_sha)
        output = json.dumps(document, ensure_ascii=False, indent=2) + "\n"
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output, encoding="utf-8")
        print(f"generated {len(entries)} entries at {args.output}")
        return 0
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
