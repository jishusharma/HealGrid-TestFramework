# Selenium-Parallel-TestFrameWork

Parallel TestNG framework with ThreadLocal WebDriver, Healenium self-healing, and Docker execution.

## Run

```bash
docker-compose up --build   # first run
docker-compose up           # subsequent runs
```

Reports: `./target/extent-reports/ExecutionReport.html`

## Features

- ThreadLocal WebDriver for parallel execution
- Healenium auto-healing locators
- Full Docker containerization (no local Java/Chrome)
- ExtentReports with screenshots
- WaitHelper for page load & AJAX
- Java 17, Selenium 4.11

## License

MIT
