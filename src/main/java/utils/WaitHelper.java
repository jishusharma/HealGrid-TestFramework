package utils;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;

public final class WaitHelper {

    private WaitHelper() {}

    // Core wait method
    public static <T> T waitFor(WebDriver driver, ExpectedCondition<T> condition) {
        return waitFor(driver, condition, WaitConfig.ELEMENT_VISIBLE_TIMEOUT);
    }

    public static <T> T waitFor(WebDriver driver, ExpectedCondition<T> condition, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        wait.pollingEvery(WaitConfig.POLLING_INTERVAL);
        return wait.until(condition);
    }

    // Element waits
    public static WebElement waitForClickable(WebDriver driver, WebElement element) {
        return waitFor(driver, ExpectedConditions.elementToBeClickable(element), WaitConfig.ELEMENT_CLICKABLE_TIMEOUT);
    }

    public static WebElement waitForElementClickable(WebDriver driver, WebElement element) {
        return waitForClickable(driver, element);
    }

    public static WebElement waitForClickable(WebDriver driver, By locator) {
        return waitFor(driver, ExpectedConditions.elementToBeClickable(locator), WaitConfig.ELEMENT_CLICKABLE_TIMEOUT);
    }

    public static WebElement waitForVisible(WebDriver driver, WebElement element) {
        return waitFor(driver, ExpectedConditions.visibilityOf(element), WaitConfig.ELEMENT_VISIBLE_TIMEOUT);
    }

    public static WebElement waitForVisible(WebDriver driver, By locator) {
        return waitFor(driver, ExpectedConditions.visibilityOfElementLocated(locator), WaitConfig.ELEMENT_VISIBLE_TIMEOUT);
    }

    // Page load & AJAX waits (with exception tolerance)
    public static void waitForPageLoad(WebDriver driver) {
        waitForDocumentReady(driver);
        waitForAjaxCalls(driver);
    }

    private static void waitForDocumentReady(WebDriver driver) {
        ExpectedCondition<Boolean> documentReady = d -> {
            try {
                JavascriptExecutor js = (JavascriptExecutor) d;
                return js.executeScript("return document.readyState").equals("complete");
            } catch (Exception e) {
                return true; // if script fails, assume ready
            }
        };
        waitFor(driver, documentReady, WaitConfig.PAGE_LOAD_TIMEOUT);
    }

    private static void waitForAjaxCalls(WebDriver driver) {
        ExpectedCondition<Boolean> ajaxComplete = d -> {
            try {
                JavascriptExecutor js = (JavascriptExecutor) d;
                boolean jQueryDone = (Boolean) js.executeScript(
                        "return (typeof jQuery === 'undefined') || jQuery.active == 0;");
                boolean angularDone = (Boolean) js.executeScript(
                        "return (typeof angular === 'undefined') || " +
                                "angular.element(document).injector() === undefined || " +
                                "angular.element(document).injector().get('$http').pendingRequests.length === 0;");
                boolean fetchDone = (Boolean) js.executeScript(
                        "return (typeof window.fetchCallsInProgress === 'undefined') || window.fetchCallsInProgress === 0;");
                return jQueryDone && angularDone && fetchDone;
            } catch (Exception e) {
                // If any script fails, assume AJAX is complete
                return true;
            }
        };
        waitFor(driver, ajaxComplete, WaitConfig.AJAX_TIMEOUT);
    }
}