package utils.listeners;

import base.BaseTest;
import base.DriverManager;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.Status;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import utils.GenericUtil;

import java.io.File;
import java.io.IOException;

public class TestListener implements ITestListener {
    private static final Logger LOGGER = LogManager.getLogger(TestListener.class);

    @Override
    public void onStart(ITestContext context) {
        LOGGER.info("*** Test Suite {} started ***", context.getName());
    }

    @Override
    public void onFinish(ITestContext context) {
        LOGGER.info("*** Test Suite {} ending ***", context.getName());
        flushExtentReports();
    }

    @Override
    public void onTestStart(ITestResult result) {
        LOGGER.info("*** Running test method {} ***", result.getMethod().getMethodName());
        String testName = getTestName(result);
        ExtentTestManager.startTest(testName);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        LOGGER.info("*** Executed {} test successfully ***", result.getMethod().getMethodName());
        ExtentTestManager.getTest().log(Status.PASS, "Test Passed");
    }

    @Override
    public void onTestFailure(ITestResult result) {
        LOGGER.info("*** Test execution {} failed ***", result.getMethod().getMethodName());
        WebDriver driver = DriverManager.getDriver();
        if (driver != null) {
            String screenshotPath = captureScreenshot(driver, result.getMethod().getMethodName());
            if (screenshotPath != null) {
                attachScreenshotToReport(screenshotPath);
            } else {
                logScreenshotError();
            }
        } else {
            logWebDriverError();
        }
        logTestFailure(result);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        LOGGER.info("*** Test {} skipped ***", result.getMethod().getMethodName());
        ExtentTestManager.getTest().log(Status.SKIP, "Test Skipped");
    }

    private String getTestName(ITestResult result) {
        String driverName = "Unknown";
        if (result.getInstance() instanceof BaseTest) {
            BaseTest baseTest = (BaseTest) result.getInstance();
            driverName = baseTest.getDriverName();
        }
        return result.getTestClass().getName() + " :: " + result.getMethod().getMethodName() + " [" + driverName + "]";
    }

    private String captureScreenshot(WebDriver driver, String methodName) {
        String screenshotName = methodName + "_" + GenericUtil.currentDate("yyyyMMddHHmmss");
        String screenshotPath = GenericUtil.getScreenShotPath() + screenshotName + ".png";
        LOGGER.info("Attempting to capture screenshot: {}", screenshotPath);

        boolean screenshotCaptured = GenericUtil.getScreenshot(driver, screenshotName, GenericUtil.getScreenShotPath());
        LOGGER.info("Screenshot capture result: {}", screenshotCaptured);

        if (screenshotCaptured) {
            File screenshotFile = new File(screenshotPath);
            if (screenshotFile.exists()) {
                LOGGER.info("Screenshot found: {}", screenshotPath);
                return screenshotPath;
            } else {
                LOGGER.error("Screenshot file not found: {}", screenshotPath);
            }
        }

        LOGGER.error("Failed to capture screenshot");
        return null;
    }

    private void attachScreenshotToReport(String screenshotPath) {
        try {
            File screenshotFile = new File(screenshotPath);
            if (screenshotFile.exists()) {
                String relativeScreenshotPath = getRelativeScreenshotPath(screenshotPath);
                ExtentTestManager.getTest().fail("Test failed. Screenshot attached:",
                        MediaEntityBuilder.createScreenCaptureFromPath(relativeScreenshotPath).build());
                LOGGER.info("Screenshot attached to report: {}", relativeScreenshotPath);
            } else {
                LOGGER.error("Screenshot file not found: {}", screenshotPath);
                ExtentTestManager.getTest().log(Status.WARNING, "Screenshot file not found: " + screenshotPath);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to attach screenshot to report: ", e);
            ExtentTestManager.getTest().log(Status.WARNING, "Failed to attach screenshot to report: " + e.getMessage());
        }
    }

    private String getRelativeScreenshotPath(String absolutePath) {
        String reportDir = ExtentManager.getReportFilePath();
        return new File(reportDir).toURI().relativize(new File(absolutePath).toURI()).getPath();
    }

    private void flushExtentReports() {
        try {
            ExtentManager.getInstance().flush();
            LOGGER.info("Extent report flushed successfully");
        } catch (Exception e) {
            LOGGER.error("Error flushing Extent report: ", e);
        }
    }

    private void logScreenshotError() {
        LOGGER.error("Failed to capture screenshot");
        ExtentTestManager.getTest().log(Status.WARNING, "Failed to capture screenshot");
    }

    private void logWebDriverError() {
        LOGGER.error("WebDriver is null. Unable to capture screenshot.");
        ExtentTestManager.getTest().log(Status.WARNING, "WebDriver is null. Unable to capture screenshot.");
    }

    private void logTestFailure(ITestResult result) {
        ExtentTestManager.getTest().log(Status.FAIL, "Test Failed");
        ExtentTestManager.getTest().log(Status.FAIL, result.getThrowable());
    }
}
