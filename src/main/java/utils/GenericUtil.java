package utils;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class GenericUtil {

    private static final Logger LOGGER = LogManager.getLogger(GenericUtil.class);
    private static final ConcurrentHashMap<String, Properties> PROPERTIES_CACHE = new ConcurrentHashMap<>();
    private static final String TARGET = "target";
    private static final SecureRandom RANDOM = new SecureRandom();

    private GenericUtil() {
        // Private constructor to prevent instantiation
    }

    public static String currentDate(String format) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
    }

    public static String generateUniqueNumber() {
        return currentDate("yyyyMMddHHmmssSSS");
    }

    public static Properties getConfig() {
        return getPropertiesFile("config.properties");
    }

    public static String formatDate(String strDate, String sourceFormat, String resultFormat) {
        try {
            LocalDateTime date = LocalDateTime.parse(strDate, DateTimeFormatter.ofPattern(sourceFormat));
            String formattedDate = date.format(DateTimeFormatter.ofPattern(resultFormat));
            LOGGER.info("Source Format: {}, Given Date: {}, Formatted Date: {}", sourceFormat, strDate, formattedDate);
            return formattedDate;
        } catch (Exception e) {
            LOGGER.error("Error: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid date format", e);
        }
    }

    public static File createTempFile(String fileName) throws IOException {
        File tempFile = File.createTempFile(fileName, ".pdf");
        FileUtils.writeStringToFile(tempFile, "Table of Content file", StandardCharsets.UTF_8);
        tempFile.deleteOnExit();
        return tempFile;
    }

    public static boolean compareTexts(String string1, String string2) {
        boolean result = string1.equals(string2);
        LOGGER.info("Text comparison: {} equals {}: {}", string1, string2, result);
        return result;
    }

    public static boolean isElementPresent(List<WebElement> elements) {
        boolean isPresent = !elements.isEmpty();
        if (isPresent) {
            LOGGER.debug("Element is present {}", elements);
        }
        return isPresent;
    }

    public static boolean textContains(WebElement element, String expectedText) {
        String actualText = element.getText();
        boolean contains = actualText.contains(expectedText);
        LOGGER.info("Text contains '{}': {}", expectedText, contains);
        return contains;
    }

    public static void selectCheckbox(WebElement element, boolean toCheck) {
        if (toCheck != element.isSelected()) {
            element.click();
            LOGGER.debug("Checkbox state changed");
        }
    }

    public static void setFilePath(WebElement element, String fileLocation) {
        try {
            String filePath = new File(System.getProperty("user.dir") + fileLocation).getCanonicalPath();
            element.sendKeys(filePath);
            LOGGER.info("File path set: {}", filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to set file path", e);
        }
    }

    public static boolean getScreenshot(WebDriver driver, String screenshotName, String filepath) {
        try {
            WebDriver originalDriver = driver;
            if (driver instanceof com.epam.healenium.SelfHealingDriver) {
                originalDriver = ((com.epam.healenium.SelfHealingDriver) driver).getDelegate();
            }
            File source = ((TakesScreenshot) originalDriver).getScreenshotAs(OutputType.FILE);
            File destination = new File(filepath + screenshotName + ".png");
            FileUtils.copyFile(source, destination);
            LOGGER.info("Screenshot captured successfully: {}", destination.getAbsolutePath());
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to capture screenshot: ", e);
            return false;
        }
    }

    // Screenshot path now uses allure-results directory (Extent removed)
    public static String getScreenShotPath() {
        String path = Paths.get(System.getProperty("user.dir"), "target", "allure-results", "screenshots")
                .toString() + File.separator;
        File screenshotDir = new File(path);
        if (!screenshotDir.exists() && !screenshotDir.mkdirs()) {
            LOGGER.error("Failed to create screenshot directory: {}", path);
        }
        LOGGER.info("Screenshot path: {}", path);
        return path;
    }

    public static Properties getPropertiesFile(String propertiesFile) {
        return PROPERTIES_CACHE.computeIfAbsent(propertiesFile, key -> {
            Properties prop = new Properties();
            try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(key)) {
                prop.load(stream);
            } catch (IOException e) {
                LOGGER.error("Failed to read properties file: {}", e.getMessage());
            }
            return prop;
        });
    }

    public static int getRandomNumber(int digits) {
        if (digits <= 0) {
            throw new IllegalArgumentException("Number of digits must be positive");
        }
        int min = (int) Math.pow(10, digits - 1);
        int max = (int) Math.pow(10, digits) - 1;
        return RANDOM.nextInt(max - min + 1) + min;
    }
}