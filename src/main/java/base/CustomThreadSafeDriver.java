package base;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.function.Supplier;

public class CustomThreadSafeDriver {
    private static final ThreadLocal<WebDriver> DRIVERS = new ThreadLocal<>();

    private CustomThreadSafeDriver() {
        // Private constructor to prevent instantiation
    }

    public static synchronized WebDriver getThreadSafeWebDriver(DriverType driverType) {
        if (DRIVERS.get() == null) {
            WebDriver driver = initializeDriver(driverType);
            WebDriver healeniumDriver = HealeniumWebDriverFactory.wrapDriver(driver);
            DRIVERS.set(healeniumDriver);
            BaseTest.incrementDriverCounter();
        }
        return DRIVERS.get();
    }

    private static WebDriver initializeDriver(DriverType driverType) {
        return driverType.getDriverSupplier().get();
    }

    public static WebDriver getCurrentDriver() {
        return DRIVERS.get();
    }

    public static void quitDriver() {
        WebDriver driver = DRIVERS.get();
        if (driver != null) {
            driver.quit();
            DRIVERS.remove();
        }
    }

    public enum DriverType {
        CHROME(() -> {
            ChromeOptions options = new ChromeOptions();
            if (Boolean.parseBoolean(System.getProperty("headless", "false"))) {
                options.addArguments("--headless");
                options.addArguments("--no-sandbox");
                options.addArguments("--disable-dev-shm-usage");
                options.addArguments("--window-size=1920,1080");
            }
            return new ChromeDriver(options);
        }),
        FIREFOX(FirefoxDriver::new),
        IE(InternetExplorerDriver::new);

        private final Supplier<WebDriver> driverSupplier;

        DriverType(Supplier<WebDriver> driverSupplier) {
            this.driverSupplier = driverSupplier;
        }

        public Supplier<WebDriver> getDriverSupplier() {
            return driverSupplier;
        }
    }
}