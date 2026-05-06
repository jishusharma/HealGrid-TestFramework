package pages;

import base.BasePage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import utils.GenericUtil;

import java.util.Properties;
import java.util.function.Supplier;

public class SauceDemoLoginPage extends BasePage {

    private static final Properties sauceConfig =
            GenericUtil.getPropertiesFile("saucedemo.properties");
    public static final String BASE_URL = sauceConfig.getProperty("saucedemo.base.url");
    private static final String USERNAME = sauceConfig.getProperty("saucedemo.username");
    private static final String PASSWORD = sauceConfig.getProperty("saucedemo.password");

    @FindBy(id = "user-name")
    private WebElement usernameInput;

    @FindBy(id = "password")
    private WebElement passwordInput;

    @FindBy(id = "login-button")
    private WebElement loginButton;

    @FindBy(css = ".title")
    private WebElement productsTitle;

    @FindBy(css = "[data-test='error']")
    private WebElement errorMessage;

    public SauceDemoLoginPage(Supplier<WebDriver> driverSupplier) {
        super(driverSupplier, BASE_URL);
    }

    public void login(String username, String password) {
        actions.sendKeys(usernameInput, username);
        actions.sendKeys(passwordInput, password);
        actions.click(loginButton);
    }

    public void loginAsStandardUser() {
        login(USERNAME, PASSWORD);
    }

    public boolean isProductsPageDisplayed() {
        return actions.isDisplayed(productsTitle) &&
                actions.getText(productsTitle).equalsIgnoreCase("Products");
    }

    public boolean isErrorMessageDisplayed() {
        return actions.isDisplayed(errorMessage);
    }

    public String getErrorMessageText() {
        return actions.getText(errorMessage);
    }

    @Override
    protected org.openqa.selenium.By getUniqueElement() {
        return org.openqa.selenium.By.id("login-button");
    }
}