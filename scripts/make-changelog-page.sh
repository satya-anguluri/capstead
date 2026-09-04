#!/usr/bin/env bash
#
# Render CHANGELOG.md into website/changelog/index.html, styled like the rest of the site.
#
# The changelog existed only in the repository, so the site had no way to answer "what changed in 0.8.0"
# except by sending a reader to GitHub. This publishes it without introducing a second copy to keep in step:
# CHANGELOG.md stays the one source, the page is generated from it, and the release notes are extracted from
# the same file by scripts/changelog-section.sh.
#
# RENDERED BY GITHUB'S OWN MARKDOWN API, not by a converter written here. The changelog nests fenced code
# blocks inside list items and uses ```diff for the breaking-change example, which is precisely what a
# hand-rolled regex converter mangles — and a changelog that renders wrongly is worse than one that is only
# on GitHub. This uses the same renderer that displays the file on GitHub, so the page cannot disagree with it.
#
# Needs gh authenticated (GITHUB_TOKEN in Actions). Generated at deploy time and NOT committed, so nobody has
# to remember to regenerate it: website/README.md documents the site as hand-edited HTML, and this keeps that
# true for every file a person actually edits.
set -euo pipefail

cd "$(dirname "$0")/.."

OUT="website/changelog/index.html"
mkdir -p "$(dirname "$OUT")"

if [ ! -f CHANGELOG.md ]; then
  echo "CHANGELOG.md not found" >&2
  exit 1
fi

# NO LEADING SLASH: Git Bash on Windows rewrites "/markdown" into a filesystem path and gh rejects it.
echo "Rendering CHANGELOG.md via the GitHub markdown API..."
# --field reads the file as a string value; mode=gfm gives the same rendering as the repository view.
BODY=$(gh api --method POST markdown \
  -f mode=gfm \
  -f text="$(cat CHANGELOG.md)" \
  --header 'Accept: application/vnd.github+json')

if [ -z "$BODY" ]; then
  echo "The markdown API returned nothing. Refusing to write an empty page." >&2
  exit 1
fi

# The h1 comes from the page header below, so drop the one the markdown starts with rather than showing
# "Changelog" twice.
BODY=$(printf '%s' "$BODY" | sed -e 's|<h1[^>]*>.*</h1>||')

cat > "$OUT" <<HTML
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title>Changelog | Capstead</title>
    <meta name="description" content="What changed in each Capstead release — the capability governance library for Spring Boot. Breaking changes, additions and fixes, per version."/>
    <link rel="stylesheet" href="../styles.css"/>
</head>
<body>
<nav class="nav">
    <div class="nav-inner">
        <a class="brand" href="/">Capstead<span class="dot">.</span></a>
        <span class="spacer"></span>
        <a class="link" href="/research/">Research</a>
        <a class="link" href="/changelog/">Changelog</a>
        <a class="link" href="https://github.com/satya-anguluri/capstead">GitHub</a>
        <a class="link" href="https://central.sonatype.com/artifact/io.capstead/capstead-starter">Maven Central</a>
    </div>
</nav>

<header class="hero wrap">
    <div class="eyebrow">Releases</div>
    <h1>Changelog</h1>
    <p class="lede">
        What changed in each release, and what it breaks. Generated from
        <a href="https://github.com/satya-anguluri/capstead/blob/main/CHANGELOG.md">CHANGELOG.md</a>,
        which is also the source for the
        <a href="https://github.com/satya-anguluri/capstead/releases">GitHub release notes</a>.
    </p>
</header>

<section class="section wrap markdown-body">
$BODY
</section>

<footer>
    <div class="wrap">
        <span>&copy; 2026 Capstead &middot; Apache-2.0</span>
        <span class="spacer"></span>
        <a href="/">Home</a>
        <a href="https://github.com/satya-anguluri/capstead">GitHub</a>
        <a href="https://central.sonatype.com/artifact/io.capstead/capstead-starter">Maven Central</a>
    </div>
</footer>
</body>
</html>
HTML

echo "Wrote $OUT ($(wc -c < "$OUT") bytes)"
