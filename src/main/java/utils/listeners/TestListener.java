package utils.listeners;

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
    }

    @Override
    public void onTestFailure(ITestResult result) {
        LOGGER.info("*** Test execution {} failed ***", result.getMethod().getMethodName());
        WebDriver driver = DriverManager.getDriver();
        if (driver != null) {
            saveScreenshot(driver);
        } else {
            LOGGER.error("WebDriver is null. Unable to capture screenshot.");
        }
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        LOGGER.info("*** Test {} skipped ***", result.getMethod().getMethodName());
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