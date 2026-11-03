# Test findings

Run date: 3 November 2026. Application: `internal-0.0.1-SNAPSHOT.jar`.
Environment: Windows 10 Pro, Java 17.0.12, application on port 8080 and
WireMock on port 8888.

112 cases: 108 passed, 4 failed, none skipped, no execution errors.
Two failures reproduce one bug; the other two need a decision on whitespace handling.

## Valid tokens containing letters outside A–F are rejected

The task permits exactly 32 characters from A–Z and 0–9. The application
instead validates against `^[0-9A-F]{32}$`, rejecting valid tokens containing Z.

Steps to reproduce:

1. Start the supplied application with the assignment API key and mock URL.
2. Configure the external `/auth` endpoint to return HTTP 200.
3. Send `POST /endpoint` with the configured `X-Api-Key`,
   `Content-Type: application/x-www-form-urlencoded` and `Accept: application/json`.
4. Use `action=LOGIN` and a token of 32 uppercase Z characters.

Expected: `{"result":"OK"}`; the application sends the token to `/auth` and
stores the authenticated session.

Actual: HTTP 400 with the following response; no `/auth` request is made:

```json
{
  "result": "ERROR",
  "message": "token: должно соответствовать \"^[0-9A-F]{32}$\""
}
```

Impact: a user with a valid token containing Z cannot log in. The returned
validation rule also excludes the other letters G–Y.

Automated reproduction:

```powershell
.\mvnw.cmd test '-Dtest=RequestValidationTest#acceptsValidTokenCharacters'
```

The letter-only and mixed cases contain Z and fail. The digit-only case passes.
Tokens vary between runs to keep sessions separate.

## Clarification: surrounding whitespace in action names

The task lists LOGIN, ACTION and LOGOUT, but does not state whether surrounding
whitespace is allowed or should be removed.

With an authenticated token and both external services returning HTTP 200:

- `action=ACTION%20` returns HTTP 200 and `{"result":"OK"}`.
- `action=LOGOUT%0A` returns HTTP 200 and `{"result":"OK"}`; the token is removed.
- `action=%20LOGIN` is rejected.

These tests expect exact action names, so the two accepted requests fail the
checks. The task doesn't settle whether that expectation is correct; they aren't
confirmed bugs. In Allure, they appear under “Требует уточнения (дефект не подтверждён)”,
separate from the token bug. Their JUnit tag is `требует-уточнения`.

Automated reproduction:

```powershell
.\mvnw.cmd test '-Dtest=RequestValidationTest#checksExactActionNames'
```

## Coverage limits

Logout makes no external calls, and login with the same token restores access.
Both checks passed. For each external service, six error statuses were combined
with an empty body, non-JSON text and a body claiming success. All 36 cases passed.
The data is in `src/test/resources/fixtures/unsuccessful-external-responses.csv`.

All 39 invalid-token cases passed. The overlength tokens use A rather than Z
so that the character-validation bug doesn't interfere with the length check.

The task leaves several behaviors open: LOGIN during an active session, LOGOUT
without a session, the application's HTTP status codes, which external statuses
count as success, and timeouts. These need clarification before exact assertions
are added. This suite doesn't cover load, stress or concurrent requests.
