package implementation;

import com.epam.healenium.SelfHealingDriver;
import interfaces.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.function.Supplier;

public class HealeniumSeleniumActions extends SeleniumActions {
    private final Supplier<WebDriver> driverSupplier;
    private final WebDriverWait wait;
    private static final ThreadLocal<HealeniumSeleniumActions> instance = new ThreadLocal<>();
    private static final Logger LOGGER = LogManager.getLogger(HealeniumSeleniumActions.class);

    private HealeniumSeleniumActions(Supplier<WebDriver> driverSupplier) {
        super(driverSupplier);
        this.driverSupplier = driverSupplier;
        this.wait = new WebDriverWait(driverSupplier.get(), Duration.ofSeconds(10));
    }

    public static HealeniumSeleniumActions getInstance(Supplier<WebDriver> driverSupplier) {
        if (instance.get() == null) {
            instance.set(new HealeniumSeleniumActions(driverSupplier));
            LOGGER.info("SeleniumActions CREATED for thread: {}", Thread.currentThread().getId());
        }
        return instance.get();
    }

    @Override
    public WebElement findElement(By locator) {
        try {
            return ((SelfHealingDriver) getDriver()).findElement(locator);
        } catch (Exception e) {
            return super.findElement(locator);
        }
    }

    @Override
    public void click(WebElement element) {
        try {
            wait.until(ExpectedConditions.elementToBeClickable(element));
            element.click();
        } catch (Exception e) {
            JavascriptExecutor js = (JavascriptExecutor) getDriver();
            js.executeScript("arguments[0].click();", element);
        }
    }

    @Override
    public void sendKeys(WebElement element, String text) {
        try {
            wait.until(ExpectedConditions.visibilityOf(element));
            element.clear();
            element.sendKeys(text);
        } catch (Exception e) {
            JavascriptExecutor js = (JavascriptExecutor) getDriver();
            js.executeScript("arguments[0].value='" + text + "';", element);
        }
    }
}
