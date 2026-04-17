# HealGrid-TestFramework

[![Build Status](https://canon-anymore-maturity.ngrok-free.dev/buildStatus/icon?job=HealGrid-Pipeline&ngrok-skip-browser-warning=true)](https://canon-anymore-maturity.ngrok-free.dev/job/HealGrid-Pipeline/)
![Java](https://img.shields.io/badge/Java-17-blue)
![Selenium](https://img.shields.io/badge/Selenium-4.11-green)
![Healenium](https://img.shields.io/badge/Healenium-3.5.1-purple)
![Docker](https://img.shields.io/badge/Docker-ready-blue)
![Grid](https://img.shields.io/badge/Selenium%20Grid-4.21-orange)
![TestNG](https://img.shields.io/badge/TestNG-7.4-red)

A production-grade, AI-ready Selenium framework with self-healing locators,
Grid parallel execution, and full Docker containerization.  
Designed as a reusable foundation — extendable into any project.

> ⚡ True parallel execution via ThreadLocal WebDriver —
> each thread gets its own isolated driver instance, zero interference.

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

## Run

```bash
docker-compose up --build   # first run
docker-compose up           # subsequent runs
mvn test -Dbrowser=chrome   # local run
```

Reports: `./target/extent-reports/ExecutionReport.html`

---

## Roadmap

| Phase | Feature | Status |
|---|---|---|
| 1 | Framework stabilization — BaseTest, BasePage, ThreadLocal | ✅ Done |
| 2 | Docker containerization | ✅ Done |
| 3 | Selenium Grid — parallel cross-browser | ✅ Done |
| 4 | Jenkins CI/CD pipeline | ⏳ Planned |
| 5 | REST Assured API testing layer | ⏳ Planned |
| 6 | BrowserStack / LambdaTest cloud execution | ⏳ Planned |
| 7 | AI enhancements + smart locator tool | ⏳ Planned |

---

## License

MIT