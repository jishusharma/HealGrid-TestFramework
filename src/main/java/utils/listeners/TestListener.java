package utils.listeners;

import base.BrowserStackSessionContext;
import base.DriverManager;
import config.ConfigResolver;
import io.qameta.allure.Allure;
import io.qameta.allure.Attachment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TestListener implements ITestListener {

    private static final Logger LOGGER = LogManager.getLogger(TestListener.class);
    private static final Map<Integer, HealingSnapshot> HEALING_SNAPSHOTS = new ConcurrentHashMap<>();

    @Override
    public void onStart(ITestContext context) {
        LOGGER.info("*** Test Suite {} started ***", context.getName());
    }

    @Override
    public void onFinish(ITestContext context) {
        LOGGER.info("*** Test Suite {} ending ***", context.getName());
    }

    @Override
    public void onTestStart(ITestResult result) {
        LOGGER.info("*** Running test method {} ***", result.getMethod().getMethodName());
        HEALING_SNAPSHOTS.put(key(result), captureHealingSnapshot());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        LOGGER.info("*** Executed {} test successfully ***", result.getMethod().getMethodName());
        attachBrowserStackSessionId();
        attachHealeniumSummary(result);
        markBrowserStackSession("passed", null);
    }


    @Override
    public void onTestFailure(ITestResult result) {
        LOGGER.info("*** Test execution {} failed ***", result.getMethod().getMethodName());
        attachBrowserStackSessionId();
        attachHealeniumSummary(result);
        markBrowserStackSession("failed", result.getThrowable().getMessage());
        WebDriver driver = DriverManager.getDriver();
        if (driver != null) {
            saveScreenshot(driver);
        } else {
            LOGGER.error("WebDriver is null. Unable to capture screenshot.");
        }
    }

    private void markBrowserStackSession(String status, String reason) {
        String sessionId = BrowserStackSessionContext.getSessionId();
        if (sessionId == null) return;

        try {
            String username = System.getenv("BROWSERSTACK_USERNAME");
            String accessKey = System.getenv("BROWSERSTACK_ACCESS_KEY");
            String payload = reason != null
                    ? "{\"status\":\"" + status + "\",\"reason\":\"" + reason + "\"}"
                    : "{\"status\":\"" + status + "\",\"reason\":\"\"}";

            java.net.URL url = new java.net.URL(
                    "https://api.browserstack.com/automate/sessions/" + sessionId + ".json");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            String auth = java.util.Base64.getEncoder()
                    .encodeToString((username + ":" + accessKey).getBytes());
            conn.setRequestProperty("Authorization", "Basic " + auth);
            conn.getOutputStream().write(payload.getBytes());
            conn.getResponseCode();
        } catch (Exception e) {
            LOGGER.warn("Failed to mark BrowserStack session status: {}", e.getMessage());
        }
    }

    private void attachBrowserStackSessionId() {
        String sessionId = BrowserStackSessionContext.getSessionId();
        if (sessionId != null) {
            saveSessionId(sessionId);
        }
    }

    @Attachment(value = "BrowserStack Session ID", type = "text/plain")
    private String saveSessionId(String sessionId) {
        return sessionId;
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        Throwable cause = result.getThrowable();
        if (cause != null && !(cause instanceof org.testng.SkipException)) {
            LOGGER.info("*** Test {} failed due to @BeforeMethod failure — overriding status to FAILED ***",
                    result.getMethod().getMethodName());
            result.setStatus(ITestResult.FAILURE);
            WebDriver driver = DriverManager.getDriver();
            if (driver != null) {
                saveScreenshot(driver);
            } else {
                LOGGER.warn("Driver null during @BeforeMethod failure for test: {}",
                        result.getMethod().getMethodName());
            }
        } else {
            LOGGER.info("*** Test {} skipped ***", result.getMethod().getMethodName());
        }
        attachHealeniumSummary(result);
    }

    // Allure automatically picks up @Attachment methods and adds the
    // returned bytes as an attachment in the report.
    @Attachment(value = "Screenshot on Failure", type = "image/png")
    private byte[] saveScreenshot(WebDriver driver) {
        try {
            // Healenium wraps the driver — we must unwrap before taking screenshot
            WebDriver originalDriver = driver;
            if (driver instanceof com.epam.healenium.SelfHealingDriver) {
                originalDriver = ((com.epam.healenium.SelfHealingDriver) driver).getDelegate();
            }
            return ((TakesScreenshot) originalDriver).getScreenshotAs(OutputType.BYTES);
        } catch (Exception e) {
            LOGGER.error("Failed to capture screenshot: ", e);
            return new byte[0];
        }
    }

    private void attachHealeniumSummary(ITestResult result) {
        HealingSnapshot snapshot = HEALING_SNAPSHOTS.remove(key(result));
        String summary = buildHealeniumSummary(snapshot);

        boolean healingHappened = summary.contains("Healing happened in this test window: yes");
        Allure.label("healenium.enabled", String.valueOf(isHealingEnabled()));
        Allure.label("healenium.healing_happened", String.valueOf(healingHappened));
        saveHealeniumSummary(summary);
    }

    @Attachment(value = "Healenium Healing Summary", type = "text/plain")
    private String saveHealeniumSummary(String summary) {
        return summary;
    }

    private HealingSnapshot captureHealingSnapshot() {
        boolean healingEnabled = isHealingEnabled();
        if (!healingEnabled) {
            return HealingSnapshot.disabled();
        }

        try (Connection conn = healingConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "select coalesce(max(id), 0) as max_result_id from healenium.healing_result");
             ResultSet rs = stmt.executeQuery()) {
            long maxResultId = 0;
            if (rs.next()) {
                maxResultId = rs.getLong("max_result_id");
            }
            return HealingSnapshot.ready(maxResultId);
        } catch (Exception e) {
            LOGGER.warn("Unable to capture Healenium baseline for Allure summary: {}", e.getMessage());
            return HealingSnapshot.unavailable(e.getMessage());
        }
    }

    private String buildHealeniumSummary(HealingSnapshot snapshot) {
        if (snapshot == null) {
            snapshot = HealingSnapshot.unavailable("No test-start healing baseline was captured.");
        }

        if (!snapshot.healingEnabled) {
            return String.join(System.lineSeparator(),
                    "Healing enabled: false",
                    "Healing happened in this test window: no",
                    "Source: heal.enabled runtime configuration",
                    "Note: This run intentionally used standard Selenium behavior without self-healing.");
        }

        if (!snapshot.available) {
            return String.join(System.lineSeparator(),
                    "Healing enabled: true",
                    "Healing happened in this test window: unknown",
                    "Source: Healenium Postgres tables",
                    "Reason: " + snapshot.error,
                    "Drill-down report: http://localhost:7878/healenium/report/");
        }

        List<String> events = new ArrayList<>();
        try (Connection conn = healingConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "select h.create_date, s.url, s.class_name, s.method_name, " +
                             "s.locator as original_locator, hr.locator as healed_locator, " +
                             "round(hr.score::numeric, 4) as score, hr.success_healing " +
                             "from healenium.healing_result hr " +
                             "join healenium.healing h on h.uid = hr.healing_id " +
                             "join healenium.selector s on s.uid = h.selector_id " +
                             "where hr.id > ? " +
                             "order by h.create_date, hr.score desc " +
                             "limit 20")) {
            stmt.setLong(1, snapshot.maxHealingResultId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    events.add("- healed_at: " + rs.getString("create_date") + System.lineSeparator() +
                            "  page: " + rs.getString("url") + System.lineSeparator() +
                            "  code: " + rs.getString("class_name") + "#" + rs.getString("method_name") + System.lineSeparator() +
                            "  original: " + rs.getString("original_locator") + System.lineSeparator() +
                            "  candidate: " + rs.getString("healed_locator") + System.lineSeparator() +
                            "  score: " + rs.getString("score") + System.lineSeparator() +
                            "  success: " + rs.getBoolean("success_healing"));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to query Healenium events for Allure summary: {}", e.getMessage());
            return String.join(System.lineSeparator(),
                    "Healing enabled: true",
                    "Healing happened in this test window: unknown",
                    "Source: Healenium Postgres tables",
                    "Reason: " + e.getMessage(),
                    "Drill-down report: http://localhost:7878/healenium/report/");
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Healing enabled: true").append(System.lineSeparator());
        summary.append("Healing happened in this test window: ")
                .append(events.isEmpty() ? "no" : "yes").append(System.lineSeparator());
        summary.append("Source: healenium.healing_result joined to healenium.healing and healenium.selector")
                .append(System.lineSeparator());
        summary.append("Drill-down report: http://localhost:7878/healenium/report/")
                .append(System.lineSeparator());

        if (events.isEmpty()) {
            summary.append("Events: none");
        } else {
            summary.append("Events:").append(System.lineSeparator());
            summary.append(String.join(System.lineSeparator(), events));
        }
        return summary.toString();
    }

    private boolean isHealingEnabled() {
        return ConfigResolver.getBoolean("heal.enabled", true);
    }

    private Connection healingConnection() throws Exception {
        String dbHost = setting("DB_HOST", "localhost");
        String dbPort = setting("DB_PORT", "5432");
        String dbName = setting("DB_NAME", "healenium");
        String dbUser = setting("DB_USER", "healenium_user");
        String dbPass = setting("DB_PASSWORD", "healenium_password");
        String url = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
        return java.sql.DriverManager.getConnection(url, dbUser, dbPass);
    }

    private String setting(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return defaultValue;
    }

    private int key(ITestResult result) {
        return System.identityHashCode(result);
    }

    private static class HealingSnapshot {
        private final boolean healingEnabled;
        private final boolean available;
        private final long maxHealingResultId;
        private final String error;

        private HealingSnapshot(boolean healingEnabled, boolean available, long maxHealingResultId, String error) {
            this.healingEnabled = healingEnabled;
            this.available = available;
            this.maxHealingResultId = maxHealingResultId;
            this.error = error;
        }

        private static HealingSnapshot ready(long maxHealingResultId) {
            return new HealingSnapshot(true, true, maxHealingResultId, null);
        }

        private static HealingSnapshot disabled() {
            return new HealingSnapshot(false, true, 0, null);
        }

        private static HealingSnapshot unavailable(String error) {
            return new HealingSnapshot(true, false, 0, error);
        }
    }
}
