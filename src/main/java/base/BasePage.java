package base;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.PageFactory;
import com.aventstack.extentreports.Status;
import implementation.SeleniumActions;
import utils.WaitHelper;
import utils.listeners.ExtentTestManager;

import java.util.function.Supplier;

public abstract class BasePage {
    protected final Supplier<WebDriver> driverSupplier;
    protected final SeleniumActions actions;

    public BasePage(Supplier<WebDriver> driverSupplier) {
        this.driverSupplier = driverSupplier;
        this.actions = SeleniumActions.getInstance(driverSupplier);
        initializePage();
    }

    private void initializePage() {
        try {
            PageFactory.initElements(getDriver(), this);
            waitForPageLoad();
            logInitialization(true);
        } catch (Exception e) {
            logInitialization(false);
            throw e;
        }
    }

    private void logInitialization(boolean success) {
        Status status = success ? Status.INFO : Status.ERROR;
        String message = success
                ? String.format("Initialized %s", this.getClass().getSimpleName())
                : String.format("Failed to initialize %s", this.getClass().getSimpleName());
        ExtentTestManager.getTest().log(status, message);
    }

    protected WebDriver getDriver() {
        return driverSupplier.get();
    }

    // Uses WaitHelper for unified page load and AJAX waits
    protected void waitForPageLoad() {
        try {
            WaitHelper.waitForPageLoad(getDriver());

            // Wait for page-specific element
            By uniqueElement = getUniqueElement();
            if (uniqueElement != null) {
                WaitHelper.waitForVisible(getDriver(), uniqueElement);
            }

            waitForAdditionalConditions();

            ExtentTestManager.getTest().log(Status.PASS,
                    String.format("%s loaded successfully", this.getClass().getSimpleName()));
        } catch (Exception e) {
            ExtentTestManager.getTest().log(Status.FAIL,
                    String.format("Page load failed for %s: %s", this.getClass().getSimpleName(), e.getMessage()));
            throw e;
        }
    }

    // Override in child classes to add custom wait conditions
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
            ExtentTestManager.getTest().log(Status.WARNING,
                    "Page load check failed: " + e.getMessage());
            return false;
        }
    }

    protected void navigateToPage(String url) {
        try {
            actions.navigateToUrl(url);
            waitForPageLoad();
            ExtentTestManager.getTest().log(Status.PASS,
                    "Successfully navigated to: " + url);
        } catch (Exception e) {
            ExtentTestManager.getTest().log(Status.FAIL,
                    "Navigation failed: " + e.getMessage());
            throw e;
        }
    }

    protected abstract By getUniqueElement();
}