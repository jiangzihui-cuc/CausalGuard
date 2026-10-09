#!/usr/bin/env bash
# A7-4：生成第三方依赖清单（Gradle dependency report），供人工核对许可证。
#
# 输出目录（默认 build/license-report/）：
#   raw/<module>-<configuration>.txt   Gradle 原始依赖树
#   resolved-artifacts.txt             去重后的 group:artifact:version 清单
#
# 注意：本脚本只负责“自动收集”，许可证判定仍需人工复核（见 docs/a7-license-report.md
# 与 docs/20-open-source-reuse-guide.md）。自动工具结果不等于法律结论。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if [[ -f "$HOME/tools/android-env.sh" ]]; then
  # shellcheck disable=SC1091
  source "$HOME/tools/android-env.sh"
fi
: "${JAVA_HOME:?JAVA_HOME 未设置}"
: "${ANDROID_HOME:?ANDROID_HOME 未设置}"

OUT_DIR="${1:-build/license-report}"
mkdir -p "$OUT_DIR/raw"

declare -a TARGETS=(
  "app:releaseRuntimeClasspath"
  "demo-app:mapReleaseRuntimeClasspath"
  "rule-engine:releaseRuntimeClasspath"
  "core-model:releaseRuntimeClasspath"
)

for target in "${TARGETS[@]}"; do
  mod="${target%%:*}"
  cfg="${target##*:}"
  echo "==> :$mod:$cfg"
  ./gradlew ":$mod:dependencies" --configuration "$cfg" --console=plain \
    > "$OUT_DIR/raw/${mod}-${cfg}.txt"
done

python3 - "$OUT_DIR" <<'PY'
import re, sys, glob, os
out_dir = sys.argv[1]
pat = re.compile(r'([\w.\-]+):([\w.\-]+):([\w.\-]+)(?:\s*->\s*([\w.\-]+))?')
coords = set()
for f in glob.glob(os.path.join(out_dir, 'raw', '*.txt')):
    for line in open(f, encoding='utf-8', errors='ignore'):
        if '(c)' in line or 'project ' in line:
            continue
        for m in pat.finditer(line):
            g, a, v, tgt = m.groups()
            if g.startswith('project'):
                continue
            coords.add(f"{g}:{a}:{tgt or v}")
with open(os.path.join(out_dir, 'resolved-artifacts.txt'), 'w', encoding='utf-8') as w:
    for c in sorted(coords):
        w.write(c + '\n')
print(f"resolved artifacts: {len(coords)} -> {os.path.join(out_dir, 'resolved-artifacts.txt')}")
PY
