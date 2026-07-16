# Capstead website

The public site for [capstead.ai](https://capstead.ai). Plain static HTML/CSS — **no build step**.

```
website/
  styles.css          # shared theme (mirrors the /capstead dashboard palette)
  index.html          # landing page              -> capstead.ai/
  research/
    index.html        # governance research page  -> capstead.ai/research
```

## Preview locally

Any static file server works, e.g.:

```bash
cd website
python -m http.server 8000
# open http://localhost:8000/  and  http://localhost:8000/research/
```

Links use absolute root paths (`/`, `/research/`), so serve from the site root (not a subpath).

## Hosting

Pick either; both serve the folder as-is.

### Option A — GitHub Pages (current setup)
This repo already deploys `website/` via `.github/workflows/pages.yml`, and `website/CNAME` pins
`capstead.ai`. To finish:
1. Repo **Settings → Pages → Source: GitHub Actions** (unlocks the workflow).
2. DNS at your registrar:
   - **Apex `capstead.ai`** → four `A` records: `185.199.108.153`, `185.199.109.153`,
     `185.199.110.153`, `185.199.111.153` (or an `ALIAS`/`ANAME` → `satya-anguluri.github.io`).
   - **`www.capstead.ai`** → `CNAME` → `satya-anguluri.github.io`. GitHub Pages auto-redirects the
     non-canonical host to the apex named in `CNAME`, so `www` → `capstead.ai` works out of the box.
3. Enable **Enforce HTTPS** once the certificate provisions (a few minutes after DNS resolves).

### Option B — S3 + CloudFront (matches the existing EngineerPrep setup)
1. `aws s3 sync website/ s3://<bucket> --delete`
2. CloudFront distribution with the bucket as origin; default root object `index.html`.
3. Add a subdirectory-index behavior (Function/Lambda@Edge) so `/research/` resolves to
   `/research/index.html`, then point `capstead.ai` at the distribution and invalidate `/*`.

## Editing

- Colors/spacing live in `styles.css` (`:root` variables mirror the dashboard).
- The comparison table and case-study numbers in `research/index.html` are first-party; keep them
  accurate to the current release when bumping versions (currently `0.5.3`).
