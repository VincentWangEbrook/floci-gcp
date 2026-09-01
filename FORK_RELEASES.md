# Fork release policy

This fork publishes only to GitHub Container Registry:

```text
ghcr.io/vincentwangebrook/floci-gcp:<upstream-version>-shophub.<patch>
```

For example, tag `fork-v0.8.0-shophub.0` publishes:

```text
ghcr.io/vincentwangebrook/floci-gcp:0.8.0-shophub.0
```

The tag communicates both the upstream base and the fork patch level. It is
immutable once published. This workflow deliberately does not publish
`latest`, `nightly`, or any other moving development tag.

Only a separately verified promotion workflow may assign a default development
tag. Promotion requires the consumer repository's complete conformance suite,
including its local Terraform lifecycle, GCP sandbox, and rollback checks.

## Publishing a candidate

1. Rebase or merge the intended upstream version and pass this repository's CI.
2. Run the consumer conformance suite against the candidate digest.
3. Create an annotated tag in the exact form `fork-v<upstream>-shophub.<patch>`.
4. Push the tag. `Fork Release` builds the multi-architecture native image,
   attaches build provenance, and publishes the immutable GHCR tag.

The first successful publication creates the repository's GHCR package. The
GitHub Actions workflow needs no long-lived registry credential: it uses the
repository-scoped `GITHUB_TOKEN` with `packages: write` permission.
