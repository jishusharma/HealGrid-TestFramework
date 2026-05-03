package utils.listeners;

import base.BrowserStackSessionContext;
import base.DriverManager;
import io.qameta.allure.Attachment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

public class TestListener implements ITestListener {

    private static final Logger LOGGER = LogManager.getLogger(TestListener.class);

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
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        LOGGER.info("*** Executed {} test successfully ***", result.getMethod().getMethodName());
        attachBrowserStackSessionId();
        markBrowserStackSession("passed", null);
    }


    @Override
    public void onTestFailure(ITestResult result) {
        LOGGER.info("*** Test execution {} failed ***", result.getMethod().getMethodName());
        attachBrowserStackSessionId();
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
}