package tests;

import org.opencv.core.Core;
import org.testng.annotations.Test;
import org.testng.Assert;
import pages.DemoQAMainPage;
import base.BaseTest;
import ai.AIUtil;
import ai.OpenCVUtil;
import org.openqa.selenium.WebElement;

public class AiCompareImagesTest extends BaseTest {

    private final AIUtil aiUtil;

    public AiCompareImagesTest() {
        this(OpenCVUtil.getInstance());
    }

    public AiCompareImagesTest(AIUtil aiUtil) {
        this.aiUtil = aiUtil;
    }

    @Test(priority = 1, description = "Verify logo using AI image comparison")
    public void verifyLogoUsingAI() {
        LOGGER.info(">>> Running verifyLogoUsingAI (priority=1) second <<<");
        DemoQAMainPage mainPage = new DemoQAMainPage(threadLocalDriver::get);
        WebElement logoElement = mainPage.getLogoElement();

        String expectedLogoPath = "src/test/java/resources/ai_images/expected/Toolsqa.jpg";
        String actualLogoPath = "src/test/java/resources/ai_images/actual/Toolsqa.jpg";

        aiUtil.captureElementScreenshot(logoElement, actualLogoPath);
        boolean isLogoMatching = aiUtil.compareImages(expectedLogoPath, actualLogoPath, 0.95);

        Assert.assertTrue(isLogoMatching, "Logo does not match the expected image");
    }

    @Test(priority = 0, description = "Test OpenCV is loaded correctly")
    public void testOpenCVLoaded() {
        LOGGER.info(">>> Running testOpenCVLoaded (priority=0) first <<<");
        Assert.assertNotNull(Core.VERSION, "OpenCV is not loaded");
        System.out.println("OpenCV Version: " + Core.VERSION);
    }

    // One‑time helper to generate a valid reference image (run once, then comment out)
    // @Test
    public void generateReferenceLogo() {
        DemoQAMainPage mainPage = new DemoQAMainPage(threadLocalDriver::get);
        WebElement logoElement = mainPage.getLogoElement();
        String referencePath = "src/test/java/resources/ai_images/expected/Toolsqa.jpg";
        aiUtil.captureElementScreenshot(logoElement, referencePath);
        System.out.println("Reference image saved to: " + new java.io.File(referencePath).getAbsolutePath());
    }
}