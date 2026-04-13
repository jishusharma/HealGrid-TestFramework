package base;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import utils.GenericUtil;

import java.net.URL;
import java.util.Properties;

public class DriverFactory {

    private static final Properties config = GenericUtil.getPropertiesFile("config.properties");

    public static WebDriver createDriver() {
        String browser = getConfigValue("browser", "chrome");
        String execution = getConfigValue("execution", "local");
        String gridUrl = getConfigValue("grid.url", "http://localhost:4444");
        WebDriver driver = "grid".equalsIgnoreCase(execution)
                ? createRemoteDriver(browser)
                : createLocalDriver(browser);
        return HealeniumWebDriverFactory.wrapDriver(driver);
    }

    private static WebDriver createRemoteDriver(String browser) {

        try {
            String gridUrl = System.getProperty("grid.url",
                    config.getProperty("grid.url", "http://localhost:4444"));

            URL url = new URL(gridUrl);

            switch (browser.toLowerCase()) {

                case "chrome":
                    return new RemoteWebDriver(url, getChromeOptions());

                case "firefox":
                    return new RemoteWebDriver(url, new FirefoxOptions());

                default:
                    throw new RuntimeException("Unsupported browser: " + browser);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to create RemoteWebDriver", e);
        }
    }

    private static WebDriver createLocalDriver(String browser) {

        switch (browser.toLowerCase()) {

            case "chrome":
                return new ChromeDriver(getChromeOptions());

            case "firefox":
                return new FirefoxDriver();

            default:
                throw new RuntimeException("Unsupported browser: " + browser);
        }
    }

    private static ChromeOptions getChromeOptions() {

        ChromeOptions options = new ChromeOptions();

        boolean isHeadless = Boolean.parseBoolean(
                getConfigValue("headless", "false")
        );

        if (isHeadless) {
            options.addArguments("--headless");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
        }

        return options;
    }

    private static String getConfigValue(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value != null) return value;

        value = System.getenv(key);
        if (value != null) return value;

        value = config.getProperty(key);
        if (value != null) return value;

        return defaultValue;
    }
}