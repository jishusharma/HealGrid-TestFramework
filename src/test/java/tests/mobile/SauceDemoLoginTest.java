package tests.mobile;

import base.BaseTest;
import base.DriverManager;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.testng.Assert;
import pages.SauceDemoLoginPage;

public class SauceDemoLoginTest extends BaseTest {

    private SauceDemoLoginPage loginPage;

    @BeforeMethod(alwaysRun = true)
    public void initPage() {
        loginPage = new SauceDemoLoginPage(DriverManager::getDriver);
    }

    @Test
    public void testSuccessfulLogin() {
        loginPage.loginAsStandardUser();
        Assert.assertTrue(loginPage.isProductsPageDisplayed(),
                "Products page should be displayed after successful login");
    }

    @Test
    public void testFailedLogin() {
        loginPage.login("locked_out_user", "secret_sauce");
        Assert.assertTrue(loginPage.isErrorMessageDisplayed(),
                "Error message should be displayed for locked out user");
        Assert.assertTrue(loginPage.getErrorMessageText()
                        .contains("this user has been locked out"),
                "Error message should indicate user is locked out");
    }
}