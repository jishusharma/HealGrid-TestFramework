package tests;

import org.testng.annotations.Test;
import org.testng.Assert;
import pages.DemoQAMainPage;
import base.BaseTest;
import com.aventstack.extentreports.Status;
import utils.WaitHelper;
import utils.listeners.ExtentTestManager;

import java.util.Arrays;
import java.util.List;

public class DemoQAMainPageTest extends BaseTest {

    private static final String EXPECTED_PAGE_TITLE = "demosite";
    private static final List<String> EXPECTED_CATEGORY_CARDS = Arrays.asList(
            "Elements", "Forms", "Alerts, Frame & Windows", "Widgets", "Interactions", "Book Store Application"
    );

    @Test(description = "Verify main page title")
    public void verifyPageTitle() {
        DemoQAMainPage mainPage = new DemoQAMainPage(threadLocalDriver::get);
        String actualTitle = mainPage.getPageTitle();

        Assert.assertEquals(actualTitle, EXPECTED_PAGE_TITLE, "Page title doesn't match");

        ExtentTestManager.getTest().log(Status.PASS, "Page title verified successfully");
    }

    @Test(description = "Verify banner image")
    public void verifyBannerImage() {
        DemoQAMainPage mainPage = new DemoQAMainPage(threadLocalDriver::get);
        WaitHelper.waitForElementClickable(getDriver(), mainPage.getBannerImage());
        Assert.assertTrue(mainPage.isBannerImageDisplayed(), "Banner image is not displayed");
        Assert.assertEquals(mainPage.getBannerImageAltText(), "Selenium Online Training",
                "Banner image alt text doesn't match");

        ExtentTestManager.getTest().log(Status.PASS, "Banner image verified successfully");
    }

    @Test(description = "Verify category cards")
    public void verifyCategoryCards() {
        DemoQAMainPage mainPage = new DemoQAMainPage(threadLocalDriver::get);

        Assert.assertEquals(mainPage.getCategoryCardsCount(), EXPECTED_CATEGORY_CARDS.size(),
                "Number of category cards doesn't match");
        Assert.assertEquals(mainPage.getCategoryCardTitles(), EXPECTED_CATEGORY_CARDS,
                "Category card titles don't match");

        ExtentTestManager.getTest().log(Status.PASS, "Category cards verified successfully");
    }

    @Test(description = "Click all category cards")
    public void clickAllCategoryCards() {
        DemoQAMainPage mainPage = new DemoQAMainPage(threadLocalDriver::get);

        for (String cardTitle : EXPECTED_CATEGORY_CARDS) {
            mainPage.clickCategoryCard(cardTitle);
            // Add assertions for each card's destination page
            getDriver().navigate().back();
            ExtentTestManager.getTest().log(Status.INFO, "Clicked on " + cardTitle + " card");
        }
        ExtentTestManager.getTest().log(Status.PASS, "All category cards clicked successfully");
    }
}
