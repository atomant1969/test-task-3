# Commit checks

Enable the hooks in each checkout:

```sh
git config core.hooksPath .githooks
```

The hook checks staged changes for whitespace errors. Java changes also require
`.githooks/verify-project`, a shell script containing the build's formatting,
Javadoc, naming, static analysis, compilation, and test checks. A missing script
blocks Java commits; any verification failure blocks the commit.

When present, the verification script runs on every commit. Tracked changes must
be fully staged so the checked files match the commit.

Review the staged diff against the acceptance criteria and AGENTS.md before
committing. Business behavior and documentation accuracy need manual review.

Hooks must be enabled in each checkout and can be bypassed. Enforce the same
checks in CI and branch protection for shared branches.
