package ai;

import org.apache.commons.io.FileUtils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;

import java.io.File;
import java.io.IOException;

public class OpenCVUtil implements AIUtil {
    private static final Logger LOGGER = LogManager.getLogger(OpenCVUtil.class);
    private static OpenCVUtil instance;

    static {
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        } catch (UnsatisfiedLinkError e) {
            String libraryPath = System.getProperty("java.library.path");
            LOGGER.error("Failed to load OpenCV native library. Library path: {}", libraryPath, e);
            throw new RuntimeException("OpenCV native library could not be loaded: " + Core.NATIVE_LIBRARY_NAME, e);
        }
    }

    private OpenCVUtil() {}

    public static synchronized OpenCVUtil getInstance() {
        if (instance == null) {
            instance = new OpenCVUtil();
        }
        return instance;
    }

    // Helper to resize second image to match first image's dimensions
    private static Mat resizeToMatch(Mat target, Mat source) {
        if (target.size().width == source.size().width && target.size().height == source.size().height) {
            return source;
        }
        Mat resized = new Mat();
        Imgproc.resize(source, resized, target.size());
        return resized;
    }

    @Override
    public boolean compareImages(String expectedImagePath, String actualImagePath, double threshold) {
        Mat expected = Imgcodecs.imread(expectedImagePath);
        Mat actual = Imgcodecs.imread(actualImagePath);
        if (expected.empty() || actual.empty()) {
            LOGGER.error("Failed to read images");
            return false;
        }

        // Resize actual to match expected dimensions
        Mat actualResized = resizeToMatch(expected, actual);

        Mat diff = new Mat();
        Core.absdiff(expected, actualResized, diff);

        Mat grayDiff = new Mat();
        Imgproc.cvtColor(diff, grayDiff, Imgproc.COLOR_BGR2GRAY);

        Mat binaryDiff = new Mat();
        Imgproc.threshold(grayDiff, binaryDiff, 30, 255, Imgproc.THRESH_BINARY);

        int totalPixels = binaryDiff.rows() * binaryDiff.cols();
        int diffPixels = Core.countNonZero(binaryDiff);

        double similarity = 1.0 - (double) diffPixels / totalPixels;

        LOGGER.info("Image similarity: {}", similarity);
        return similarity >= threshold;
    }

    @Override
    public void captureElementScreenshot(WebElement element, String outputPath) {
        File screenshot = ((TakesScreenshot) element).getScreenshotAs(OutputType.FILE);
        try {
            FileUtils.copyFile(screenshot, new File(outputPath));
            LOGGER.info("Element screenshot saved to: {}", outputPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save element screenshot", e);
        }
    }

    @Override
    public void captureFullPageScreenshot(WebDriver driver, String outputPath) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
        Long totalHeight = (Long) js.executeScript("return document.body.scrollHeight");
        js.executeScript("window.resizeTo(1366, " + totalHeight + ")");
        File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        try {
            FileUtils.copyFile(screenshot, new File(outputPath));
            LOGGER.info("Full page screenshot saved to: {}", outputPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save full page screenshot", e);
        }
        js.executeScript("window.resizeTo(1366, 768)");
    }
}