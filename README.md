# Java Test Task

Tests for the Spring Boot service supplied with the assignment.
Stack: Java 17, JUnit 5, Maven, WireMock and Allure.
The tests send requests to the application. WireMock stands in for the external
service at `/auth` and `/doAction`.

## Run the application

You need JDK 17. Place the supplied JAR in the project directory and run:

```powershell
java "-Dsecret=qazWSXedc" "-Dmock=http://localhost:8888/" -jar internal-0.0.1-SNAPSHOT.jar
```

Leave this terminal open. The application uses port 8080; the tests manage
WireMock on port 8888. That port must be free before a run, so don't run two
copies of the suite at once.

## Run the tests

In another terminal:

```powershell
.\mvnw.cmd clean test
```

The wrapper downloads Maven and dependencies on first use. On Linux or macOS,
use `sh ./mvnw` instead of `.\mvnw.cmd` for each command.

Settings are in `src/test/resources/config/test.properties`: application URL,
API key, request timeout and WireMock port. The loader reads this file only;
environment variables and command-line properties don't override it.

The key `qazWSXedc` comes from the assignment and is kept here for this task only.
For a real environment, load secrets from environment variables or the CI
credential store instead of committing them. Session tokens are generated test
data, not passwords.

## Allure report

After the tests, generate and open the report:

```powershell
.\mvnw.cmd allure:report
.\mvnw.cmd allure:serve
```

Run these separately from `test`, even if the tests fail. The HTML report is in
`target/site/allure-maven-plugin`; `allure:serve` opens a local preview.
Stop it with Ctrl+C.

Test names, steps and explanations are in Russian. Choose **Русский** in the
language menu for the Allure interface. API responses and library diagnostics
are left in their original language. Code comments are in Russian and English.

Attachments show the HTTP status and the response's `result` and `message`.
The API key and session token are masked if echoed. Raw response bodies and
request headers aren't attached. Generated reports are ignored by Git.

## Coverage and findings

The suite covers LOGIN, ACTION and LOGOUT, separate user sessions, login after
logout, invalid inputs, API keys and external service errors. It also checks
that external response bodies don't change how the status is handled.

The run on 3 November 2026 completed 112 cases: 108 passed and 4 failed.
Two failures show the same token-validation bug. The other two concern whitespace
in action names, which the task doesn't specify. Allure lists these in separate
categories. See [test findings](docs/test-findings.md) for reproduction steps
and coverage limits. The failing tests remain enabled.

## Source layout

```text
src/test/
├── java/apitests/
│   ├── api/          API client and request handling
│   ├── pages/        Reserved for UI page objects; unused in this API task
│   ├── tests/        Test scenarios and assertions
│   ├── config/       Configuration loading and validation
│   └── support/      Response attachments and masking
└── resources/
    ├── allure/       Report category rules
    ├── config/       Test settings
    └── fixtures/     External response data
```

The `pages` folder is kept with `.gitkeep`; this task has no UI tests.

## Reporting bugs

Use the bug report form on the [Issues page](https://github.com/atomant1969/test-task-3/issues/new/choose).
Include the application version, reproduction steps, expected and actual results,
and any useful logs. The form is defined in
[`.github/ISSUE_TEMPLATE`](.github/ISSUE_TEMPLATE); submitted reports become GitHub issues.

## Editor setup

The Red Hat Java extension formats code and organizes imports on save.
Workspace settings are in `.vscode`; `.editorconfig` sets indentation, encoding
and line endings. In Eclipse, use **Maven → Update Project** to load the POM.

## Commit checks

To use the local hook directory:

```sh
git config core.hooksPath .githooks
```

The hook is currently disabled, and `.githooks/verify-project` is missing.
It does not check commits yet. The intended checks are listed in
[.githooks/README.md](.githooks/README.md); project conventions are in
[AGENTS.md](AGENTS.md).

## CI

`.github/workflows/ci.yml` and `Jenkinsfile` reserve a place for GitHub Actions
and Jenkins. Both contain comments only: CI isn't configured to run tests yet.
