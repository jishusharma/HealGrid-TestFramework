package base;

import org.openqa.selenium.WebDriver;

public class DriverManager {

    private static final ThreadLocal<WebDriver> driver = new ThreadLocal<>();

    public static void setDriver(WebDriver driverInstance) {
        driver.set(driverInstance);
    }

    public static WebDriver getDriver() {
        return driver.get();
    }

    public static void quitDriver() {
        if (driver.get() != null) {
            try {
                driver.get().quit();
            } catch (Exception e) {
                // driver.quit() timed out — process will be cleaned up by OS
            } finally {
                driver.remove();
            }
        }
    }
}