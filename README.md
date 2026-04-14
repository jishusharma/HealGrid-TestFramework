# HealGrid-TestFramework

A production-grade, AI-ready Selenium automation framework with self-healing locators,
Selenium Grid parallel execution, and full Docker containerization.

Built as a reusable foundation — designed to be extended into any project.

---

## What Makes This Different

| Capability | Technology |
|---|---|
| Self-healing locators | Healenium — auto-recovers broken selectors |
| Parallel cross-browser execution | Selenium Grid 4 — Chrome + Firefox |
| Full containerization | Docker + Docker Compose — single command run |
| Thread-safe driver management | ThreadLocal WebDriver — zero test interference |
| Portable configuration | 4-level fallback — JVM → OS env → config → default |
| AI-enhanced testing | OpenCV image comparison, visual regression |

---

## Architecture

BaseTest
└── DriverFactory        (creates driver — local or remote)
└── DriverManager        (ThreadLocal storage — one driver per thread)
└── HealeniumWebDriverFactory  (wraps driver with self-healing)
BasePage
└── SeleniumActions      (reusable element interactions)
└── WaitHelper           (unified page load + AJAX waits)
TestListener               (ExtentReports + screenshots on failure)

---

## Run

```bash
# First run or after code changes
docker-compose up --build

# Subsequent runs
docker-compose up

# Local run — specific browser
mvn test -Dbrowser=chrome
mvn test -Dbrowser=firefox

# Local run — Grid
mvn test -Dbrowser=chrome -Dexecution=grid -Dgrid.url=http://localhost:4444
```

Reports: `./target/extent-reports/ExecutionReport.html`

---

## Configuration

Framework uses a 4-level config fallback — no hardcoded values:
System.getProperty() → System.getenv() → config.properties → hardcoded default

This makes the framework portable across IDE, Maven CLI, Docker, and CI/CD
without changing code or rebuilding.

| Property | Default | Description |
|---|---|---|
| `browser` | `chrome` | Browser to run tests |
| `execution` | `local` | `local` or `grid` |
| `grid.url` | `http://localhost:4444` | Selenium Grid Hub URL |
| `headless` | `false` | Headless mode for Docker/CI |
| `healenium.host` | `localhost` | Healenium backend host |

---

## Tech Stack

- Java 17
- Selenium 4.11
- TestNG 7.4
- Healenium 3.5.1
- Docker + Docker Compose
- Selenium Grid 4.21
- ExtentReports 4
- Log4j2
- OpenCV 4.7 (AI features)
- Maven

---

## Roadmap

| Phase | Feature | Status |
|---|---|---|
| 1 | Framework stabilization — BaseTest, BasePage, ThreadLocal | ✅ Done |
| 2 | Docker containerization — single command execution | ✅ Done |
| 3 | Selenium Grid — parallel cross-browser execution | ✅ Done |
| 4 | Jenkins CI/CD pipeline | ⏳ Planned |
| 5 | REST Assured API testing layer | ⏳ Planned |
| 6 | BrowserStack / LambdaTest cloud execution | ⏳ Planned |
| 7 | Smart locator generation tool | ⏳ Planned |
| 8 | AI enhancements — self-healing + visual regression | ⏳ Planned |

---

## Vision

This framework is designed to evolve into a **reusable library** —
packaged as a `.jar` and imported as a Maven dependency into any project.

Consumer teams would:
1. Add dependency to their `pom.xml`
2. Extend `BaseTest` and `BasePage`
3. Pass their app URL and config via properties or env vars
4. Get ThreadLocal drivers, Healenium, Grid, and reporting out of the box

---

## License

MIT