package tests;

import base.DriverManager;
import org.testng.annotations.Test;
import org.testng.Assert;
import pages.DemoQAMainPage;
import base.BaseTest;
import utils.WaitHelper;

import java.util.Arrays;
import java.util.List;

public class DemoQAMainPageTest extends BaseTest {

    private static final String EXPECTED_PAGE_TITLE = "demosite";
    private static final List<String> EXPECTED_CATEGORY_CARDS = Arrays.asList(
            "Elements", "Forms", "Alerts, Frame & Windows", "Widgets", "Interactions", "Book Store Application"
    );

    @Test(description = "Verify main page title")
    public void verifyPageTitle() {
        DemoQAMainPage mainPage = new DemoQAMainPage(DriverManager::getDriver);
        String actualTitle = mainPage.getPageTitle();
        Assert.assertEquals(actualTitle, EXPECTED_PAGE_TITLE, "Page title doesn't match");
    }

    @Test(description = "Verify banner image")
    public void verifyBannerImage() {
        DemoQAMainPage mainPage = new DemoQAMainPage(DriverManager::getDriver);
        WaitHelper.waitForElementClickable(DriverManager.getDriver(), mainPage.getBannerImage());
        Assert.assertTrue(mainPage.isBannerImageDisplayed(), "Banner image is not displayed");
        Assert.assertEquals(mainPage.getBannerImageAltText(), "Selenium Online Training",
                "Banner image alt text doesn't match");
    }

    @Test(description = "Verify category cards")
    public void verifyCategoryCards() {
        DemoQAMainPage mainPage = new DemoQAMainPage(DriverManager::getDriver);
        Assert.assertEquals(mainPage.getCategoryCardsCount(), EXPECTED_CATEGORY_CARDS.size(),
                "Number of category cards doesn't match");
        Assert.assertEquals(mainPage.getCategoryCardTitles(), EXPECTED_CATEGORY_CARDS,
                "Category card titles don't match");
    }

    @Test(description = "Click all category cards")
    public void clickAllCategoryCards() {
        DemoQAMainPage mainPage = new DemoQAMainPage(DriverManager::getDriver);
        for (String cardTitle : EXPECTED_CATEGORY_CARDS) {
            mainPage.clickCategoryCard(cardTitle);
            DriverManager.getDriver().navigate().back();
        }
    }
}