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

### Option A — GitHub Pages
1. Push this repo to GitHub.
2. Settings → Pages → deploy from a branch, folder `/website` (or move `website/*` to `/docs` and select `/docs`).
3. Custom domain: add `capstead.ai`, create a `CNAME` file containing `capstead.ai`, and set the DNS
   `CNAME`/`ALIAS` at your registrar to `<user>.github.io`.

### Option B — S3 + CloudFront (matches the existing EngineerPrep setup)
1. `aws s3 sync website/ s3://<bucket> --delete`
2. CloudFront distribution with the bucket as origin; default root object `index.html`.
3. Add a subdirectory-index behavior (Function/Lambda@Edge) so `/research/` resolves to
   `/research/index.html`, then point `capstead.ai` at the distribution and invalidate `/*`.

## Editing

- Colors/spacing live in `styles.css` (`:root` variables mirror the dashboard).
- The comparison table and case-study numbers in `research/index.html` are first-party; keep them
  accurate to the current release when bumping versions (currently `0.5.3`).
