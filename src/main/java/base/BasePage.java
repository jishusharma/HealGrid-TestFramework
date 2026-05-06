package base;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.PageFactory;
import implementation.SeleniumActions;
import utils.WaitHelper;

import java.util.function.Supplier;

public abstract class BasePage {
    protected static final Logger LOGGER = LogManager.getLogger(BasePage.class);
    protected final Supplier<WebDriver> driverSupplier;
    protected final SeleniumActions actions;

    public BasePage(Supplier<WebDriver> driverSupplier) {
        this.driverSupplier = driverSupplier;
        this.actions = SeleniumActions.getInstance(driverSupplier);
        initializePage();
    }

    public BasePage(Supplier<WebDriver> driverSupplier, String url) {
        this.driverSupplier = driverSupplier;
        this.actions = SeleniumActions.getInstance(driverSupplier);
        driverSupplier.get().get(url);
        initializePage();
    }

    private void initializePage() {
        try {
            PageFactory.initElements(getDriver(), this);
            waitForPageLoad();
            LOGGER.info("Initialized {}", this.getClass().getSimpleName());
        } catch (Exception e) {
            LOGGER.error("Failed to initialize {}", this.getClass().getSimpleName());
            throw e;
        }
    }

    protected WebDriver getDriver() {
        return driverSupplier.get();
    }

    protected void waitForPageLoad() {
        try {
            WaitHelper.waitForPageLoad(getDriver());

            By uniqueElement = getUniqueElement();
            if (uniqueElement != null) {
                WaitHelper.waitForVisible(getDriver(), uniqueElement);
            }

            waitForAdditionalConditions();
            LOGGER.info("{} loaded successfully", this.getClass().getSimpleName());
        } catch (Exception e) {
            LOGGER.error("Page load failed for {}: {}", this.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    protected void waitForAdditionalConditions() {
        // default does nothing
    }

    public String getPageTitle() {
        return getDriver().getTitle();
    }

    public String getCurrentUrl() {
        return getDriver().getCurrentUrl();
    }

    public boolean isPageLoaded() {
        try {
            waitForPageLoad();
            return true;
        } catch (Exception e) {
            LOGGER.warn("Page load check failed: {}", e.getMessage());
            return false;
        }
    }

    protected void navigateToPage(String url) {
        try {
            actions.navigateToUrl(url);
            waitForPageLoad();
            LOGGER.info("Successfully navigated to: {}", url);
        } catch (Exception e) {
            LOGGER.error("Navigation failed: {}", e.getMessage());
            throw e;
        }
    }

    protected abstract By getUniqueElement();
}