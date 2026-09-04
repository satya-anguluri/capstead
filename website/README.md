# Capstead website

The public site for [capstead.io](https://capstead.io). Plain static HTML/CSS — **no build step**.

```
website/
  styles.css          # shared theme (mirrors the /capstead dashboard palette)
  index.html          # landing page              -> capstead.io/
  research/
    index.html        # governance research page  -> capstead.io/research/
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
This repo deploys `website/` via `.github/workflows/pages.yml`, and `website/CNAME` pins `capstead.io`.

**A person has to enable Pages once; the workflow cannot do it.** The workflow passes `enablement: true`,
which asks the action to create the site, and GitHub refuses: *"Create Pages site failed. Resource not
accessible by integration."* Creating a Pages site needs admin credentials, which `GITHUB_TOKEN` is not,
whatever `pages: write` suggests. Deploying to an existing site is a different call, and that one works —
which is why the workflow is otherwise correct and has simply never had a site to deploy to.

1. Repo **Settings → Pages → Source: GitHub Actions**. After this the workflow runs on its own, and
   `enablement: true` becomes a no-op because the site already exists. If the site is ever deleted, this
   step has to be repeated by hand for the same reason.
2. **DNS at GoDaddy**, which currently serves a Website Builder placeholder on the apex — that has to be
   detached first, or GoDaddy keeps reasserting its own records:
   - **Apex `capstead.io`** → four `A` records pointing at GitHub Pages (or an `ALIAS`/`ANAME` →
     `satya-anguluri.github.io`). Take the current addresses from GitHub's Pages documentation rather than
     from here — a stale IP list in a README is worse than no list, because it looks authoritative.
   - **`www.capstead.io`** → `CNAME` → `satya-anguluri.github.io`. Pages redirects the non-canonical host
     to the apex named in `CNAME`, so `www` → `capstead.io` works without a second rule.
3. Add the **domain-verification `TXT`** record offered in Settings → Pages. An unverified custom domain
   pointed at Pages can be claimed by another account.
4. Enable **Enforce HTTPS** once the certificate provisions — minutes to an hour after DNS resolves.

A green workflow run says nothing about whether the domain serves: the deploy and the DNS are independent,
and a passing deploy with a dead domain is what a DNS problem looks like.

### Option B — S3 + CloudFront (matches the existing EngineerPrep setup)
1. `aws s3 sync website/ s3://<bucket> --delete`
2. CloudFront distribution with the bucket as origin; default root object `index.html`.
3. Add a subdirectory-index behavior (Function/Lambda@Edge) so `/research/` resolves to
   `/research/index.html`, then point `capstead.io` at the distribution and invalidate `/*`.

## Editing

- Colors/spacing live in `styles.css` (`:root` variables mirror the dashboard).
- The comparison table and case-study numbers in `research/index.html` are first-party; keep them
  accurate to the current release when bumping versions (currently `0.5.3`).
