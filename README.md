# Java Test Task

Repository layout for Java test automation. Tests and supporting classes belong
under `src/test`; configuration files and test data go in `src/test/resources`.

## Maven

Requires JDK 17. The Maven wrapper downloads Maven and dependencies on first use.

```powershell
.\mvnw.cmd clean test
```

On Linux or macOS, use `sh ./mvnw clean test`. Generate an Allure report after
running tests with `./mvnw.cmd allure:report` on Windows or
`sh ./mvnw allure:report` on Linux/macOS. Test classes use the `Test` suffix.
The build fails if no tests are found.

In Eclipse, use **Maven → Update Project** after adding the POM. If the project
has no Maven menu, use **Configure → Convert to Maven Project**.

## Source layout

```text
src/test/
├── java/apitests/
│   ├── api/          API client and request handling
│   ├── pages/        Page objects for UI tests
│   ├── tests/        Test scenarios and assertions
│   ├── config/       Configuration loading and validation
│   └── support/      Test setup, resource handling, and diagnostics
└── resources/
    ├── config/       Environment configuration files
    └── fixtures/     Synthetic test data and sample payloads
```

Empty folders contain `.gitkeep` files so they appear in Git. Remove these as
the folders gain real files.

## Reporting bugs

Report bugs through the repository's [Issues page](https://github.com/atomant1969/test-task-3/issues/new/choose)
and select the bug report form. Include the application version, steps to
reproduce, expected and actual results, and relevant logs.

The form and issue settings are maintained in [`.github/ISSUE_TEMPLATE`](.github/ISSUE_TEMPLATE).
Bug reports are stored as GitHub issues, rather than files in that folder.

## Test credentials

The API key `qazWSXedc` is supplied with the assignment and is used only for this
test task. Real API keys and passwords must not be hardcoded or committed; use
environment variables or the CI credential store instead. The request's `token`
field is test session data, separate from the API key.

## Editor setup

The workspace settings enable Java formatting and import organization on save
using the Red Hat Java extension. `.editorconfig` sets indentation, encoding,
and line endings. Extension recommendations are in `.vscode/extensions.json`.

## Commit checks

Enable the local hooks after cloning:

```sh
git config core.hooksPath .githooks
```

The pre-commit hook checks staged changes for whitespace errors. Java changes
also require a `.githooks/verify-project` script with the build and quality
checks. Without it, the hook blocks Java commits. See [.githooks/README.md](.githooks/README.md)
for details and [AGENTS.md](AGENTS.md) for project conventions.

## CI

The repository has entry-point files for both CI systems:

- `.github/workflows/ci.yml` for GitHub Actions.
- `Jenkinsfile` for Jenkins.

Both are comment-only placeholders. Neither currently runs a pipeline. Build
and test commands must be added before enabling CI; both pipelines should run
the same verification commands.
