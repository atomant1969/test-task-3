# Project guidance

## Scope

- Read the task description and acceptance criteria before implementing tests.
- Confirm requirements before adding application code, tests, or framework setup.
- Do not invent application behavior, API contracts, credentials, selectors, or expected results.
- Keep changes within the requested scope and preserve unrelated work.
- Use Java and the tools required by the specification.

## Code and structure

- Follow standard Java naming and source-directory conventions.
- Keep methods focused and names descriptive. Prefer composition and shallow inheritance.
- Search for existing functionality before adding reusable methods.
- Add abstractions only when they make the code easier to maintain.
- Keep scenarios and business assertions in tests. Put reusable UI interactions in
  page or component objects, and API transport in clients, when applicable.
- Keep test data separate from selectors and environment configuration.
- Use explicit imports and the project's formatter. Keep editor and build formatting consistent.
- Document each explicitly declared method and constructor with meaningful Javadoc,
  including relevant parameters, return values, and exceptions. Document each type's responsibility.
- Пишите комментарии и Javadoc проекта на русском и английском языках, русский первым.
  Write project comments and Javadoc in both Russian and English, with Russian first.
  Не изменяйте комментарии сторонних инструментов и лицензионные уведомления.
  Leave third-party tool comments and license notices unchanged.

## Test design

- Write report-facing test names, steps, descriptions, attachment labels, and project-defined
  assertion messages in Russian. Preserve protocol values and original application responses.
- Follow the supplied scenarios and assert actual observable outcomes.
- Use clear assertions with useful failure messages; never add placeholder assertions.
- Keep tests independent, repeatable, and safe to run in any order.
- Use isolated synthetic data and unique identifiers where collisions are possible.
- Close resources and clean up created data reliably without hiding the original failure.
- Fail immediately when a failed prerequisite makes later actions invalid. Use grouped
  or soft assertions only for checks that can safely continue independently.
- Do not introduce retries to conceal instability. Investigate the cause first.

## UI automation, if required

- Prefer accessible roles or dedicated test attributes and keep locators with their
  owning page or component. Extract shared selector constants only where useful.
- Resolve ambiguous locators rather than using the first match to silence an error.
- Use framework synchronization and condition-based waits instead of fixed sleeps.
- Wait for the specific state or response needed; do not use network idle as a general readiness check.
- Configure meaningful timeout defaults centrally and use explicit exceptions when justified.
- Keep visual highlighting optional and disabled during normal execution.
- Capture useful failure evidence through lifecycle hooks without exposing sensitive data.

## Configuration and safety

- Keep environment settings configurable and validate required values before use.
- Document configuration precedence.
- Never commit secrets or real personal data. Do not expose them in logs or reports.
- Exclude build output, logs, reports, and other generated artifacts from version control.

## Before finishing

- Before every commit, review the staged changes against this file and the task's
  acceptance criteria. Run the pre-commit hook; do not bypass failed checks.
- Keep `.githooks/verify-project` aligned with the build and quality rules.
  Run the same automated checks in CI.
- Review the changes against the task and check affected callers when shared code changes.
- Run formatting, compilation, static analysis, and relevant tests.
- Fix failures caused by the changes. Do not claim checks passed unless they were run.
- State any unverified behavior or missing prerequisites clearly.
- Keep documentation short, factual, and consistent with what is implemented.
- Do not commit, push, or rewrite history unless requested.
