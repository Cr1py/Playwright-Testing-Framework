# Playwright Test Automation Architecture

A test automation architecture for web applications with a **React + TypeScript frontend** and a backend written in **Java**, **Python**, or **TypeScript**. It tests the UI through the browser and the backend through its HTTP API, using [Playwright](https://playwright.dev/). All three stacks share one configuration contract, one set of test data, and one combined Allure report, and they follow the same internal layering so a tester who knows one stack can navigate the others. It serves multiple projects, selected with `PROJECT=<name>`, and the test code can be written in Java, Python, or TypeScript (backend language doesn't dictate the test language). 

---

## Table of Contents

1. [Features](#features)
2. [Getting Started](#getting-started)
   * [Prerequisites](#prerequisites)
   * [Setup](#setup)
   * [Running Tests](#running-tests)
3. [System Design](#system-design)
   * [Frontend: React + TypeScript](#frontend-react--typescript)
   * [Backends: Java, Python, TypeScript](#backends-java-python-typescript)
   * [What the Backend Language Does and Does Not Affect](#what-the-backend-language-does-and-does-not-affect)
4. [Languages and Frameworks](#languages-and-frameworks)
5. [Design Principles](#design-principles)
6. [Project Structure](#project-structure)
7. [Multi-Project Support](#multi-project-support)
   * [Per-Project Configuration](#per-project-configuration)
   * [Layout Inside Each Stack](#layout-inside-each-stack)
   * [Adding a New Project](#adding-a-new-project)
8. [Architecture Layers](#architecture-layers)
9. [Stack Details](#stack-details)
   * [Java (JUnit 5 + Gradle)](#java-junit-5--gradle)
   * [Python (pytest)](#python-pytest)
   * [TypeScript (Playwright Test)](#typescript-playwright-test)
10. [Shared Configuration](#shared-configuration)
11. [Data-Driven Testing](#data-driven-testing)
12. [API Testing](#api-testing)
    * [Scope](#scope)
    * [The API Layer](#the-api-layer)
    * [Configuration](#configuration)
    * [Contracts](#contracts)
    * [Authentication and Test Data](#authentication-and-test-data)
    * [Test Layers and Selection](#test-layers-and-selection)
    * [Examples](#examples)
    * [Backend Validation](#backend-validation)
    * [API Reporting](#api-reporting)
13. [Failure Capture (Screenshots, Video, Traces)](#failure-capture-screenshots-video-traces)
14. [Parallel Execution](#parallel-execution)
15. [Reporting](#reporting)
16. [AI-Assisted Workflows](#ai-assisted-workflows)
    * [Failure Summarization](#failure-summarization)
    * [AI Coding Assistance](#ai-coding-assistance)
17. [Coding Conventions](#coding-conventions)

---
 
## Features
 
- **Configuration-based:** one JSON config per environment, deep-merged over a base file and consumed identically by all three stacks.
- **Page Objects and Page Component Objects:** pages compose reusable components (header, nav menu, table) instead of duplicating locators.
- **Data-driven:** CSV and JSON test data in `shared/test-data/` feeds tests in every language.
- **API testing:** typed API clients, black-box endpoint tests, and response validation against shared OpenAPI contracts, independent of the backend's language.
- **Cross-layer E2E:** perform an action in the UI, then verify the result through the API.
- **Backend-agnostic:** the same API tests and contract checks work against Java, Python, and TypeScript backends.
- **Multi-project:** per-project config, data, contracts, pages, and tests, with shared components in `common/`.
- **Failure capture:** screenshot, video, and trace on test failure.
- **Detailed reporting:** per-stack results are merged into a single Allure report.
- **Parallel execution:** native parallelism in each stack (JUnit 5 parallel, pytest-xdist, Playwright workers).
- **AI-assisted:** `llm-cli` summarizes failures after each CI run, and shared context files guide AI coding tools (Claude Code, Copilot, etc.).
---

## Getting Started
 
### Prerequisites
 
- Java 17+, Gradle (wrapper included)
- Python 3.11+ and `uv` or `pip`
- Node.js 20+
- `jq`, `make`, [Allure CLI](https://allurereport.org/docs/install/)
- Optional: [`llm`](https://llm.datasette.io/) CLI for AI summaries
### Setup
 
```bash
git clone <repo-url> && cd playwright-automation
cp .env.example .env
 
# TypeScript
cd typescript && npm ci && npx playwright install --with-deps && cd ..
 
# Python
cd python && uv sync && uv run playwright install --with-deps && cd ..
 
# Java
cd java && ./gradlew build -x test && cd ..
```
 
### Running tests
 
```bash
make test-ts        # TypeScript
make test-python    # Python
make test-java      # Java
make test-all       # all three stacks, all layers
make test-api       # API tests only, all stacks (fast, no browser)
make test-ui        # UI tests only
make test-e2e       # cross-layer tests only
 
PROJECT=project-a make test-api            # choose the application under test
PROJECT=project-b ENV=staging make test-all  # choose project and environment
make report                   # merge results, build and open Allure report
make summarize                # AI failure summary -> reports/summary.md
```
 
Per-stack commands, if you prefer them directly:
 
```bash
cd typescript && PROJECT=project-a npx playwright test
cd python && PROJECT=project-a uv run pytest
cd java && PROJECT=project-a ./gradlew test
 
# API tests only (prefix each with PROJECT=<name>)
cd typescript && npx playwright test --project=api
cd python && uv run pytest -m api
cd java && ./gradlew test -PincludeTags=api
```
---
 
## System Design
 
The framework tests **web applications with a React + TypeScript frontend and an HTTP backend written in Java, Python, or TypeScript**.
 
```
          ┌──────────────────────────────┐
          │       Test framework         │
          │     (Java | Python | TS)     │
          └──────┬────────────────┬──────┘
        browser  │                │  HTTP
                 v                v
   ┌────────────────────┐   ┌────────────────────────────┐
   │ Frontend           │-->│ Backend API                │
   │ React + TypeScript │   │ Java | Python | TypeScript │
   └────────────────────┘   └────────────────────────────┘
```
 
### Frontend: React + TypeScript
 
The frontend is tested as a black box through the browser. The framework only needs its URL, and no frontend code changes are required beyond optional `data-testid` attributes.
 
- **Locators:** prefer `getByRole`, `getByLabel`, and `getByText`. Do not rely on CSS class names, which CSS Modules and styled-components often hash or regenerate.
- **Rendering and loading states:** rely on Playwright's auto-waiting and web-first assertions. Assert on the final visible state instead of sleeping or waiting for re-renders.
- **Network isolation (optional):** `ui` tests may stub specific API calls with `page.route` to cover edge cases (errors, empty states). `e2e` tests always use the real backend.
- **Out of scope:** component-level tests (Vitest/Jest with React Testing Library) belong in the frontend repo.
### Backends: Java, Python, TypeScript
 
Backends are tested as black boxes over HTTP. The framework needs an OpenAPI spec for each, which most popular frameworks can generate:
 
| Backend language | Typical frameworks | OpenAPI spec source |
|---|---|---|
| Java | Spring Boot | `springdoc-openapi` (`/v3/api-docs`) |
| Python | FastAPI, Django REST Framework, Flask | FastAPI built-in (`/openapi.json`), `drf-spectacular`, `flask-smorest` / `apispec` |
| TypeScript | NestJS, Express, Fastify | `@nestjs/swagger`, `tsoa`, `@fastify/swagger`, `zod-to-openapi` |
 
**What every backend must provide:**
 
1. An HTTP API reachable from the test environment (dev or staging, never production for mutating tests).
2. A way to authenticate programmatically (token endpoint, API key, or test-only login).
3. An OpenAPI spec, generated or hand-written.
4. A way to create and clean up test data through the API, or through test-only endpoints.
### What the backend language does and does not affect
 
| Does **not** affect | Does affect |
|---|---|
| UI tests | Where the OpenAPI spec comes from |
| Black-box API tests | Database driver, if you opt into [direct DB validation](#backend-validation) |
| Contract validation | Test-data seeding mechanism (public API vs test-only endpoints) |
| Cross-layer E2E tests | The `backend` label in reports |
 
---
 
## Languages and Frameworks
 
These are the languages of the **test code**. The backend under test can be written in any of the three, independent of the language the tests use.
 
| Concern | Java | Python | TypeScript |
|---|---|---|---|
| Language | Java 17+ | Python 3.11+ | TypeScript / Node 20+ |
| Browser automation | Playwright for Java | Playwright for Python (`pytest-playwright`) | Playwright (`@playwright/test`) |
| Test runner | JUnit 5 | pytest | Playwright Test |
| Build / deps | Gradle (Kotlin DSL) | pip / uv (`pyproject.toml`) | npm |
| CSV parsing | Univocity Parsers | `csv` / pandas | `csv-parse` |
| Parallelism | JUnit parallel execution | `pytest-xdist` | Playwright workers |
| Reporting | `allure-junit5` | `allure-pytest` | `allure-playwright` |
| API client | Playwright `APIRequestContext` (REST Assured optional) | Playwright `APIRequestContext` (`httpx` optional) | Playwright `request` fixture |
| Contract validation | `swagger-request-validator` | `openapi-core` (+ `schemathesis` optional) | `ajv` / `openapi-response-validator` |
 
Shared tooling: [Allure Report](https://allurereport.org/), [`llm`](https://llm.datasette.io/) CLI (or equivalent) for AI summaries, `jq` for result processing, GNU Make for unified commands.

---
 
## Design Principles
 
1. **Monorepo with one folder per language.** Each stack keeps its native tooling and does not fight the others.
2. **Shared assets at the root.** Test data, environment config, and report tooling are language-neutral. All stacks read the same CSV/JSON files and write the same `allure-results` format.
3. **Identical layering in every language.** Config -> core -> components -> pages -> tests.
4. **Use what the framework already gives you.** `pytest-playwright` and Playwright Test already provide browser fixtures, video, and screenshots, so we configure them rather than rebuild them.
5. **No secrets in the repo.** Credentials come from `.env` locally and CI secrets in pipelines.
6. **API contracts are the shared source of truth.** One OpenAPI spec per project is validated by all three stacks, so a backend in any language is checked against the same definition of correct.
7. **Project is a first-class dimension.** Config, data, contracts, pages, API clients, and tests are organized per project, with only genuinely shared code in `common/`.
8. **Test the system, not the implementation.** Tests interact through the browser and HTTP only, so backend rewrites in another language do not break them.
---
 
## Project Structure
 
```
playwright-automation/
├── README.md
├── .env.example
├── .gitignore
├── Makefile                          # make test-java | test-python | test-ts | test-all | report
│
├── shared/
│   ├── config/
│   │   ├── base.json                 # global defaults: browser, viewport, retries
│   │   └── projects/
│   │       ├── project-a/
│   │       │   ├── project.json      # frontend/API URLs, backend language, auth type
│   │       │   ├── dev.json          # per-environment overrides
│   │       │   ├── staging.json
│   │       │   └── prod.json
│   │       ├── project-b/
│   │       └── project-c/
│   ├── test-data/                    # read by all 3 stacks
│   │   ├── common/                   # data shared by every project
│   │   ├── project-a/
│   │   │   ├── users.csv
│   │   │   └── products.json
│   │   ├── project-b/
│   │   ├── project-c/
│   │   └── schema/                   # JSON schemas to validate data files
│   ├── contracts/
│   │   ├── project-a/openapi.yaml    # one OpenAPI spec per backend
│   │   ├── project-b/openapi.yaml
│   │   └── project-c/openapi.yaml
│   └── scripts/
│       ├── merge-allure-results.sh
│       └── summarize-report.sh       # llm-cli failure summarizer
│
├── java/                             # JUnit 5 + Gradle
├── python/                           # pytest
├── typescript/                       # Playwright Test
│
├── ai/
│   ├── prompts/
│   │   ├── summarize-failures.md
│   │   ├── generate-page-object.md
│   │   ├── generate-api-client.md
│   │   └── triage-flaky-tests.md
│   └── context/
│       ├── ARCHITECTURE.md           # conventions the LLM must follow
│       └── AGENTS.md                 # AI coding tool instructions (CLAUDE.md points here)
│
├── reports/                          # gitignored output
│   ├── allure-results/               # merged raw results from all stacks
│   └── allure-report/
│
└── .github/workflows/
    ├── java.yml
    ├── python.yml
    ├── typescript.yml
    └── report.yml                    # merge results, publish Allure, run AI summary
```
 
---
 
## Multi-Project Support
 
`PROJECT` selects the application under test, and `ENV` selects the environment: `PROJECT=project-a ENV=staging make test-api`.
 
### Per-project configuration
 
```json
// shared/config/projects/project-a/project.json
{
  "name": "project-a",
  "backend": { "language": "java", "framework": "spring-boot" },
  "frontend": { "framework": "react", "baseURL": "http://localhost:3000" },
  "api": {
    "baseURL": "http://localhost:8080/api",
    "timeouts": { "request": 10000 },
    "auth": { "type": "bearer", "tokenEndpoint": "/auth/token" }
  },
  "contract": "shared/contracts/project-a/openapi.yaml"
}
```
 
`staging.json` and the other environment files override only what differs (usually the two base URLs). `backend.language` is informational: it labels reports and selects the driver if direct DB validation is enabled.
 
### Layout inside each stack
 
Pages, API clients, and tests get one folder per project. Components shared by several projects live in `common/`, for example a shared design system or table.
 
```
typescript/
├── playwright.config.ts             # reads PROJECT and ENV; sets testMatch and baseURLs
└── src/
    ├── components/
    │   ├── common/                  # reused by two or more projects
    │   └── project-a/
    ├── pages/
    │   ├── project-a/
    │   ├── project-b/
    │   └── project-c/
    ├── api/
    │   ├── common/BaseApiClient.ts
    │   ├── project-a/               # UsersClient.ts, OrdersClient.ts ...
    │   ├── project-b/
    │   └── project-c/
    └── fixtures/
        ├── base.ts                  # config, contract validator, auth
        ├── project-a.ts             # project-specific pages and clients
        ├── project-b.ts
        └── project-c.ts
tests/
├── project-a/{ui,api,e2e}/
├── project-b/{ui,api,e2e}/
└── project-c/{ui,api,e2e}/
```
 
Each project's tests import fixtures from their own project file (for example `src/fixtures/project-a`), so project-specific pages and clients stay type-safe with no runtime lookup.
 
```ts
// playwright.config.ts (sketch)
const project = process.env.PROJECT ?? 'project-a';
const cfg = loadConfig(project, process.env.ENV ?? 'dev');
 
export default defineConfig({
  testDir: `./tests/${project}`,
  use: { baseURL: cfg.frontend.baseURL },
  projects: [
    { name: 'ui-chromium', testMatch: 'ui/**/*.spec.ts' },
    { name: 'api', testMatch: 'api/**/*.spec.ts', use: { baseURL: cfg.api.baseURL } },
    { name: 'e2e', testMatch: 'e2e/**/*.spec.ts' },
  ],
});
```
 
> **Naming note:** a Playwright "project" is a run configuration (`ui-chromium`, `api`, `e2e`). In this README, a "project" or `PROJECT` is an application under test. The two are independent.
 
Java and Python follow the same pattern with language-appropriate names and selectors:
 
| | Folder / package naming | Selecting the project |
|---|---|---|
| TypeScript | `project-a/` | `PROJECT=project-a` |
| Python | `project_a/` (hyphens are not valid in module names) | `PROJECT=project-a` read in `conftest.py` |
| Java | `projecta` package, for example `pages.projecta`, `tests.projecta.api` | `PROJECT=project-a` read by `Config`, or `-Pproject=project-a` |
 
### Adding a new project
 
1. Create `shared/config/projects/<project>/` with `project.json` and one file per environment.
2. Add the OpenAPI spec to `shared/contracts/<project>/` (or configure CI to pull it from the backend).
3. Add `shared/test-data/<project>/`.
4. Add `pages/`, `api/`, `fixtures/`, and `tests/` folders for that project in each stack you use, reusing `common/` where possible.
5. Add the project to the CI matrix.
---
 
## Architecture Layers
 
Every stack implements the same five layers. Dependencies only point downward.
 
```
tests (ui | api | e2e)       <- assertions and scenarios: data-driven
  │
  ├── pages -> components    <- UI: one class per screen, built from reusable fragments
  │
  └── api clients            <- API: one class per resource, typed requests/responses
  │
core                         <- browser lifecycle, BasePage, BaseApiClient (auth, headers, logging)
  │
config + data                <- environment settings, CSV/JSON readers, typed models, OpenAPI contracts
```
 
| Layer | Responsibility |
|---|---|
| **config** | Load `shared/config/base.json`, deep-merge the file for `ENV`, expose a typed settings object. |
| **core** | Create and tear down Playwright objects, provide `BasePage` and `BaseApiClient` helpers. |
| **components** | Encapsulate a fragment of UI scoped to a root locator. Reused by many pages. |
| **pages** | Model a screen. Expose actions (`login(user)`) and state (`errorMessage()`), never assertions. |
| **api clients** | Wrap one backend resource. Expose calls (`createUser(payload)`) that return typed responses, never assertions. |
| **tests** | Drive pages and API clients and assert outcomes. Split into `ui`, `api`, and `e2e`. |
 
---
 
## Stack Details
 
### Java (JUnit 5 + Gradle)
 
```
java/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── src/
    ├── main/java/automation/
    │   ├── config/
    │   │   ├── Config.java              # loads shared/config/*.json by ENV
    │   │   └── ConfigLoader.java
    │   ├── core/
    │   │   ├── BrowserFactory.java      # chromium/firefox/webkit, headless, video
    │   │   ├── PlaywrightManager.java   # ThreadLocal<Playwright/Browser/Context/Page>
    │   │   └── BasePage.java
    │   ├── components/
    │   │   ├── HeaderComponent.java
    │   │   ├── NavMenuComponent.java
    │   │   └── TableComponent.java
    │   ├── pages/
    │   │   ├── LoginPage.java
    │   │   └── DashboardPage.java
    │   ├── api/
    │   │   ├── BaseApiClient.java       # base URL, auth, headers, Allure logging
    │   │   ├── UsersClient.java
    │   │   ├── OrdersClient.java
    │   │   ├── ContractValidator.java   # validates responses against OpenAPI
    │   │   └── models/                  # request/response POJOs
    │   ├── data/
    │   │   ├── CsvReader.java           # Univocity wrapper
    │   │   └── models/User.java
    │   └── utils/
    │       ├── ScreenshotUtil.java
    │       └── WaitUtil.java
    └── test/
        ├── java/com/yourorg/automation/
        │   ├── base/BaseTest.java       # @BeforeEach/@AfterEach, browser context setup
        │   ├── base/BaseApiTest.java    # API request context only, no browser
        │   ├── extensions/
        │   │   └── FailureExtension.java  # TestWatcher: screenshot + trace on failure
        │   └── tests/
        │       ├── ui/
        │       │   ├── LoginTest.java   # @Tag("ui"), @ParameterizedTest + @CsvFileSource
        │       │   └── DashboardTest.java
        │       ├── api/
        │       │   └── UsersApiTest.java    # @Tag("api"), no browser
        │       └── e2e/
        │           └── CheckoutE2ETest.java # @Tag("e2e"), UI action + API verification
        └── resources/
            ├── junit-platform.properties   # parallel execution config
            └── allure.properties
```
 
Key points:
 
- **Thread safety:** Playwright objects are not thread-safe. `PlaywrightManager` holds one `Playwright`/`Browser`/`BrowserContext`/`Page` per thread via `ThreadLocal`.
- **Video:** set via `Browser.NewContextOptions().setRecordVideoDir(...)` and attached to Allure in the `TestWatcher`.
- **Allure:** `io.qameta.allure` Gradle plugin plus `allure-junit5`.
- **Test selection:** tag classes with `@Tag("ui" | "api" | "e2e")` and filter in Gradle with `-PincludeTags=api` (wired to `useJUnitPlatform { includeTags(...) }`).
### Python (pytest)
 
```
python/
├── pyproject.toml                # pytest-playwright, pytest-xdist, allure-pytest
├── pytest.ini                    # markers, -n auto, --alluredir, --video, --screenshot
├── conftest.py                   # root fixtures
├── src/automation/
│   ├── config/
│   │   ├── settings.py           # loads shared/config/*.json (pydantic model)
│   │   └── __init__.py
│   ├── core/
│   │   ├── base_page.py
│   │   └── browser_factory.py    # only if overriding pytest-playwright defaults
│   ├── components/
│   │   ├── header_component.py
│   │   └── table_component.py
│   ├── pages/
│   │   ├── login_page.py
│   │   └── dashboard_page.py
│   ├── api/
│   │   ├── base_api_client.py     # base URL, auth, headers, Allure logging
│   │   ├── users_client.py
│   │   ├── orders_client.py
│   │   ├── contract_validator.py  # validates responses against OpenAPI
│   │   └── models.py              # pydantic request/response models
│   ├── data/
│   │   ├── csv_reader.py
│   │   └── models.py
│   └── utils/
│       └── allure_helpers.py
└── tests/
    ├── conftest.py               # page + api fixtures, pytest_runtest_makereport failure hook
    ├── ui/
    │   ├── test_login.py         # @pytest.mark.ui, parametrize from CSV
    │   └── test_dashboard.py
    ├── api/
    │   └── test_users_api.py     # @pytest.mark.api, no browser
    └── e2e/
        └── test_checkout.py      # @pytest.mark.e2e, UI action + API verification
```
 
Key points:
 
- `pytest-playwright` already provides `page`, `context`, and `browser` fixtures plus `--video=retain-on-failure` and `--screenshot=only-on-failure`.
- Parallelism comes from `pytest-xdist` (`-n auto`).
- **Test selection:** register `ui`, `api`, and `e2e` markers in `pytest.ini` and filter with `-m api`. API tests use the `playwright` fixture's `request.new_context()` and never launch a browser.
### TypeScript (Playwright Test)
 
```
typescript/
├── package.json
├── tsconfig.json
├── playwright.config.ts          # projects, workers, allure-playwright reporter, video, trace
├── .eslintrc.cjs
├── src/
│   ├── config/
│   │   └── env.ts                # loads shared/config/*.json by ENV
│   ├── fixtures/
│   │   └── index.ts              # test.extend<{ loginPage, dashboardPage, usersClient }>()
│   ├── components/
│   │   ├── HeaderComponent.ts
│   │   └── TableComponent.ts
│   ├── pages/
│   │   ├── BasePage.ts
│   │   ├── LoginPage.ts
│   │   └── DashboardPage.ts
│   ├── api/
│   │   ├── BaseApiClient.ts       # wraps APIRequestContext: auth, headers, logging
│   │   ├── UsersClient.ts
│   │   ├── OrdersClient.ts
│   │   ├── contractValidator.ts   # validates responses against OpenAPI
│   │   └── models.ts              # request/response types
│   ├── data/
│   │   ├── csvReader.ts          # csv-parse
│   │   └── types.ts
│   └── utils/
│       └── allureHelpers.ts
└── tests/
    ├── ui/
    │   ├── login.spec.ts         # for (const user of users) { test(...) }
    │   └── dashboard.spec.ts
    ├── api/
    │   └── users.api.spec.ts     # no browser
    └── e2e/
        └── checkout.e2e.spec.ts  # UI action + API verification
```
 
Key points:
 
- Fixtures replace the `BaseTest` pattern. Page objects are injected into tests, so there is no manual setup or teardown.
- `use: { video: 'retain-on-failure', screenshot: 'only-on-failure', trace: 'retain-on-failure' }` covers failure capture.
- `fullyParallel: true` and `workers: process.env.CI ? 4 : undefined` handle parallelism.
- **Test selection:** define Playwright `projects` named `ui-chromium`, `api`, and `e2e`, each matching its folder under `tests/<project>/`, then run `PROJECT=project-a npx playwright test --project=api`. The `api` project needs no browser.
---
 
## Shared Configuration
 
All three stacks read the same files so behavior stays consistent.
 
```json
// shared/config/base.json
{
  "browser": "chromium",
  "headless": true,
  "viewport": { "width": 1920, "height": 1080 },
  "timeouts": { "action": 10000, "navigation": 30000 },
  "retries": 1,
  "video": "retain-on-failure",
  "screenshot": "only-on-failure"
}
```
 
**Resolution order:**
 
1. Load `shared/config/base.json` (global defaults).
2. Deep-merge `shared/config/projects/<PROJECT>/project.json` over it.
3. Deep-merge `shared/config/projects/<PROJECT>/<ENV>.json` (`ENV=dev|staging|prod`, default `dev`).
4. Overlay environment variables from `.env` or CI secrets (URL overrides, credentials).
Secrets (passwords, tokens) never live in the JSON files.
 
`.env.example`:
 
```
PROJECT=project-a
ENV=staging
TEST_USER_PASSWORD=changeme
# Optional overrides
# FRONTEND_BASE_URL=https://staging.example.com
# API_BASE_URL=https://api.staging.example.com
```
 
---
 
## Data-Driven Testing
 
- Data lives in `shared/test-data/<project>/` as CSV (tabular inputs such as `users.csv`) or JSON (nested fixtures such as `products.json`). Data used by every project goes in `shared/test-data/common/`.
- Each stack has a small reader and typed model: Univocity + `User.java`, `csv_reader.py` + pydantic, `csvReader.ts` + `types.ts`.
- Data files are validated against JSON schemas in `shared/test-data/schema/` so a bad row fails fast instead of failing mid-test.
- Per-language patterns:
  - **Java:** `@ParameterizedTest` with `@CsvFileSource` or a custom `ArgumentsProvider`.
  - **Python:** `@pytest.mark.parametrize` populated from the CSV reader.
  - **TypeScript:** a loop generating `test()` calls, one per row.
---
 
## API Testing
 
### Scope
 
| In scope | Out of scope |
|---|---|
| **Black-box API tests:** status codes, response bodies, headers, auth, validation and error cases | Backend unit and integration tests (keep these in each backend repo with its native tooling: JUnit, pytest, Jest) |
| **Contract validation:** responses checked against the OpenAPI spec | Load and performance testing (use k6 or Gatling) |
| **Cross-layer E2E:** UI action, then API verification of backend state | Direct database assertions as a default (see [Backend validation](#backend-validation)) |
 
Black-box API tests do not care whether the backend is written in Java, TypeScript, or Python, so the same tests work against all of them.
 
### The `api` layer
 
Each stack gets API clients that mirror page objects:
 
- `BaseApiClient` handles base URL, auth headers, timeouts, and request/response logging to Allure.
- One client per resource (`UsersClient`, `OrdersClient`) exposes typed methods such as `create(payload)` and `getById(id)`.
- Clients return typed responses and **never assert**. Tests assert.
- Request/response models live next to the clients and mirror the OpenAPI schemas.
The default HTTP client in every stack is Playwright's `APIRequestContext`. It keeps tooling consistent, shares cookies and `storageState` with browser contexts, and exists in all three languages. Use `httpx` or REST Assured only if a team has a strong reason.
 
### Configuration
 
Each project's `project.json` carries an `api` block (see [Multi-Project Support](#multi-project-support)), and the environment files override the URLs:
 
```json
// shared/config/projects/project-a/project.json (excerpt)
{
  "api": {
    "baseURL": "https://api.dev.example.com",
    "timeouts": { "request": 10000 },
    "auth": { "type": "bearer", "tokenEndpoint": "/auth/token" }
  }
}
```
 
Credentials and client secrets come from `.env` or CI secrets, never from these files.
 
### Contracts
 
```
shared/contracts/<project>/openapi.yaml
```
 
- `ContractValidator` loads the spec and validates every response a test chooses to check: status, content type, and body schema.
- Validators: `ajv` / `openapi-response-validator` (TypeScript), `openapi-core` (Python), `swagger-request-validator` (Java).
- Optionally run [Schemathesis](https://schemathesis.readthedocs.io/) from the Python stack as a property-based fuzzer against any backend, whatever language it is written in.
- If the spec is generated by the backend (FastAPI, Springdoc, tsoa), pull it into `shared/contracts/` in CI so tests always validate against the deployed version.
### Authentication and test data
 
- **Tokens:** obtain them via the auth endpoint in a worker-scoped fixture or setup step, then cache per worker.
- **UI shortcut:** log in through the API once, save `storageState`, and reuse it in UI tests so they skip the login screen.
- **Data setup and teardown:** create and delete test data through API clients, not the UI.
- **Parallel safety:** generate unique data per test (for example, a suffix on emails or names) so parallel workers never collide.
### Test layers and selection
 
| Layer | Browser? | Purpose | Typical speed |
|---|---|---|---|
| `ui` | Yes | Screen behavior and user flows | Slowest |
| `api` | No | Endpoint behavior and contracts | Fast |
| `e2e` | Yes | UI action produces correct backend state | Slow, few tests |
 
Run `api` first in CI as a fast gate, then `ui` and `e2e`.
 
### Examples
 
**TypeScript**
 
```ts
// tests/api/users.api.spec.ts
import { test, expect } from '../../src/fixtures';
 
test('create user returns 201 and matches contract', async ({ usersClient, contract }) => {
  const res = await usersClient.create({ name: 'Ada', email: `ada+${Date.now()}@example.com` });
 
  expect(res.status()).toBe(201);
  contract.assertResponse('POST', '/users', res.status(), await res.json());
});
```
 
**Python**
 
```python
# tests/api/test_users_api.py
import pytest
 
@pytest.mark.api
def test_create_user_returns_201(users_client, contract, unique_email):
    res = users_client.create({"name": "Ada", "email": unique_email})
 
    assert res.status == 201
    contract.assert_response("POST", "/users", res.status, res.json())
```
 
**Java**
 
```java
// tests/api/UsersApiTest.java
@Tag("api")
class UsersApiTest extends BaseApiTest {
    @Test
    void createUserReturns201() {
        APIResponse res = usersClient.create(new CreateUser("Ada", uniqueEmail()));
 
        assertEquals(201, res.status());
        contract.assertResponse("POST", "/users", res.status(), res.text());
    }
}
```
 
**Cross-layer E2E (TypeScript)**
 
```ts
// tests/e2e/checkout.e2e.spec.ts
test('placing an order is persisted', async ({ checkoutPage, ordersClient, user }) => {
  await checkoutPage.placeOrder(user.cart);
 
  const orders = await ordersClient.listFor(user.id);
  expect(orders).toContainEqual(expect.objectContaining({ status: 'PLACED' }));
});
```
 
This catches bugs where the UI shows success but the backend never saved anything.
 
### Backend validation
 
Prefer validating through the API. Use direct database access only for things the API does not expose (audit logs, async job results):
 
- Keep it in a separate `backend/` module with **read-only** credentials, against dev or staging only.
- Centralize SQL in one place so schema changes do not ripple through tests.
- Select the driver per project through config, since backends may use different databases.

### API Reporting
- Every API call is attached to the Allure result (method, URL, status, headers, body).
- Redact `Authorization` headers, tokens, and passwords before attaching.
- Label results with `layer` (`ui`, `api`, `e2e`) so the combined report can be filtered.
---
 
## Failure Capture (Screenshots, Video, Traces)
 
| Artifact | Java | Python | TypeScript |
|---|---|---|---|
| Screenshot | `FailureExtension` (TestWatcher) | `--screenshot=only-on-failure` | `screenshot: 'only-on-failure'` |
| Video | `setRecordVideoDir`, attached in TestWatcher | `--video=retain-on-failure` | `video: 'retain-on-failure'` |
| Trace | Manual `context.tracing()` start/stop | `--tracing=retain-on-failure` | `trace: 'retain-on-failure'` |
 
All artifacts are attached to the Allure result so they appear inline in the report. API tests have no screenshots or video; they attach the request and response instead (see [API Testing](#api-testing)).
 
---
 
## Parallel Execution
 
| Stack | Mechanism | Setting |
|---|---|---|
| Java | JUnit 5 parallel execution | `junit.jupiter.execution.parallel.enabled=true` in `junit-platform.properties` |
| Python | `pytest-xdist` | `-n auto` in `pytest.ini` |
| TypeScript | Playwright workers | `fullyParallel: true`, `workers` in `playwright.config.ts` |
 
Tests must be independent: no shared mutable state, no ordering assumptions, and each test owns its browser context.
 
---
 
## Reporting
 
**Pipeline:**
 
1. Each stack writes to its own `allure-results/` directory.
2. `shared/scripts/merge-allure-results.sh` copies them into `reports/allure-results/`.
3. `allure generate reports/allure-results -o reports/allure-report --clean` builds one combined report.
4. `shared/scripts/summarize-report.sh` extracts failures and sends them to `llm-cli`.
Use Allure labels so the combined report can be filtered: `parentSuite` (or `epic`) set to the language name, a `layer` label set to `ui`, `api`, or `e2e`, a `project` label, and a `backend` label (`java`, `python`, or `typescript`) taken from `project.json`.
 
---
 
## AI-Assisted Workflows
 
### Failure summarization
 
```bash
# shared/scripts/summarize-report.sh
#!/usr/bin/env bash
jq -r 'select(.status=="failed" or .status=="broken")
       | "\(.name)\n\(.statusDetails.message)\n\(.statusDetails.trace)\n---"' \
   reports/allure-results/*-result.json > reports/failures.txt
 
cat ai/prompts/summarize-failures.md reports/failures.txt \
  | llm -m <your-model> > reports/summary.md
```
 
The prompt instructs the model to:
 
- group failures by likely root cause,
- flag probable flakes (timeouts, stale elements, race conditions),
- separate product bugs from test bugs,
- keep the summary short enough to post in a PR comment or Slack.
Run it as the last step of `report.yml` and post `reports/summary.md`.
 
### AI coding assistance
 
- `ai/context/ARCHITECTURE.md` defines the rules an LLM must follow.
- A root `CLAUDE.md` / `AGENTS.md` points to that file so tools such as Claude Code pick it up automatically.
- `ai/prompts/generate-page-object.md` takes an HTML snippet or Playwright codegen output and emits a page object in the requested language.
- `ai/prompts/generate-api-client.md` takes an OpenAPI operation or path and emits a typed client plus a starter test in the requested language.
- `ai/prompts/triage-flaky-tests.md` analyzes repeated failures and suggests fixes.
---
 
## Coding Conventions
 
These are enforced in review and stated in `ai/context/ARCHITECTURE.md`:
 
1. **Pages never contain assertions:** Tests assert; pages expose state.
2. **Locator priority:** `getByRole` -> `getByLabel` / `getByText` -> `data-testid` -> CSS (last resort). Avoid XPath.
3. **Components are reused:** never duplicated across pages.
4. **All test data comes from `shared/test-data/`:** No hardcoded credentials or inline datasets.
5. **No fixed sleeps:** Rely on Playwright auto-waiting and web-first assertions.
6. **Mirror existing naming:** per language (`LoginPage.java`, `login_page.py`, `LoginPage.ts`).
7. **One browser context per test:** for isolation.
8. **Keep logic out of tests:** Anything reusable moves into a page, component, or util.
9. **API clients never assert:** They return typed responses; tests assert.
10. **Validate against the contract:** API tests check at least status and body schema against `shared/contracts/`.
11. **Set up data through the API, not the UI:** Reserve the UI for the behavior under test.
12. **Unique data per test:** Parallel workers must never share or collide on records.
13. **Never log secrets:** Redact tokens and credentials from Allure attachments and console output.
14. **Project code stays in its project folder:** Anything reused by two or more projects moves to `common/`. Tests never branch on the project name.
---
 
