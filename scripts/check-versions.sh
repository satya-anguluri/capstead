#!/usr/bin/env bash
#
# Does every version a reader is told to install match the version this repo builds?
#
# The website served 0.5.3 for three releases while the pom said 0.8.0, and website/README.md had carried the
# instruction to keep them in step the whole time. An instruction that relies on someone remembering to grep
# is not a control, which is what this replaces.
#
# WHAT IT DELIBERATELY IGNORES, and this is the part that decides whether the check survives contact:
# historical version references are CORRECT and must never be rewritten. README.md says "In 0.7.0 the recorder
# became durable", docs/LAUNCH.md uses 0.3.0 eleven times as a worked example of announcing a release, and
# DECLARATIVE-CAPABILITIES.md says "provider-neutral since 0.5.0". A check that flagged those would be
# switched off within a week and the repo would be worse off than with no check at all.
#
# So it looks at exactly one thing: a <version> that sits inside a capstead-starter dependency block — the
# snippet someone copies. Plain XML in Markdown, HTML-escaped in the site's <pre> blocks.
#
# Usage: scripts/check-versions.sh [expected-version]
#        With no argument it asks Maven for the project version.
set -euo pipefail

cd "$(dirname "$0")/.."

EXPECTED="${1:-}"
if [ -z "$EXPECTED" ]; then
  EXPECTED=$(mvn -B -ntp -q help:evaluate -Dexpression=project.version -DforceStdout)
fi
echo "expected version: $EXPECTED"

FILES="README.md website/index.html website/research/index.html"
fail=0

for f in $FILES; do
  [ -f "$f" ] || { echo "::warning::$f not found, skipping"; continue; }

  # Every line mentioning the starter artifact, then the version within the following three lines. Printed as
  # "file:line:version" so a failure names the exact place to edit.
  found=$(awk -v file="$f" '
    # ANY capstead-* artifact, not just the starter: the README also installs capstead-spring-ai,
    # capstead-jdbc, capstead-mcp and capstead-mcp-server, and all four go stale the same way. The
    # capstead- prefix is what excludes spring-ai-starter-mcp-server, which carries the Spring AI
    # version and must never be rewritten to ours.
    /capstead-[a-z-]*<\/artifactId>|capstead-[a-z-]*&lt;\/artifactId&gt;/ { hot = 3; next }
    hot > 0 {
      line = $0
      gsub(/&lt;/, "<", line); gsub(/&gt;/, ">", line)
      if (match(line, /<version>[^<]+<\/version>/)) {
        v = substr(line, RSTART, RLENGTH)
        gsub(/<\/?version>/, "", v)
        gsub(/^[ \t]+|[ \t]+$/, "", v)
        print file ":" NR ":" v
        hot = 0
        next
      }
      hot--
    }' "$f")

  if [ -z "$found" ]; then
    echo "::warning::$f mentions no capstead-starter dependency block"
    continue
  fi

  while IFS= read -r hit; do
    v="${hit##*:}"
    if [ "$v" = "$EXPECTED" ]; then
      echo "  ok   $hit"
    else
      echo "::error::$hit declares $v but this repo builds $EXPECTED"
      fail=1
    fi
  done <<< "$found"
done

# A release with no changelog entry is a release nobody can read. Cheap to assert, and it is the one thing
# about the CHANGELOG that can be checked mechanically — its contents cannot be.
if [ ! -f CHANGELOG.md ]; then
  echo "::error::CHANGELOG.md is missing"
  fail=1
elif grep -qE "^## +${EXPECTED}([[:space:]]|$)" CHANGELOG.md; then
  echo "  ok   CHANGELOG.md has a section for $EXPECTED"
else
  echo "::error::CHANGELOG.md has no '## $EXPECTED' section"
  echo "Release notes are extracted from that section, so without it a release has no notes either."
  fail=1
fi

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "Version drift. Update the install snippets and the changelog, NOT the historical mentions:"
  echo "  README.md          'In 0.7.0 the recorder became ...'      <- correct, leave it"
  echo "  docs/LAUNCH.md     0.3.0 as a worked example              <- correct, leave it"
  echo "  DECLARATIVE-*.md   'provider-neutral since 0.5.0'         <- correct, leave it"
  exit 1
fi

echo "All install snippets and the changelog agree on $EXPECTED."
