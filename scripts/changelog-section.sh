#!/usr/bin/env bash
#
# Print one version's section of CHANGELOG.md, for use as GitHub release notes.
#
#   scripts/changelog-section.sh 0.8.0 > notes.md
#   gh release create v0.8.0 --notes-file notes.md
#
# WHY THIS EXISTS. The 0.8.0 release notes and the 0.8.0 changelog entry were written separately, said
# overlapping things, and would have drifted the moment either was edited. The changelog is the source now and
# the release page is a projection of it — so there is one place to write and one thing to keep true.
#
# The cost is that CHANGELOG.md has to read well as release notes, which means a one-line summary under the
# version heading before the categorised sections rather than diving straight into "### Breaking".
#
# Boundaries are "## <version>" to the next "## " at the same level. "### " subsections are part of the
# section, which is why the terminator is anchored to exactly two hashes followed by a space.
set -euo pipefail

cd "$(dirname "$0")/.."

VERSION="${1:-}"
if [ -z "$VERSION" ]; then
  echo "usage: $(basename "$0") <version>" >&2
  exit 2
fi

if [ ! -f CHANGELOG.md ]; then
  echo "CHANGELOG.md not found" >&2
  exit 1
fi

# -v so the version is a literal, not a regex: dots in 0.8.0 would otherwise match 0x8y0.
section=$(awk -v want="$VERSION" '
  # Exactly "## <version>", with nothing else on the line but optional trailing space.
  $0 == "## " want { inside = 1; next }
  # A following top-level heading ends it. "### " does not.
  inside && /^## / { exit }
  inside { print }
' CHANGELOG.md)

# Trim leading and trailing blank lines, so the notes do not open or close with whitespace.
section=$(printf '%s\n' "$section" | sed -e '/./,$!d' | sed -e :a -e '/^\n*$/{$d;N;};/\n$/ba')

if [ -z "$section" ]; then
  echo "No '## $VERSION' section in CHANGELOG.md." >&2
  echo "Sections present:" >&2
  grep -E '^## ' CHANGELOG.md >&2 || true
  exit 1
fi

printf '%s\n' "$section"
