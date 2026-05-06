package pages;

import base.BasePage;
import implementation.SeleniumActions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.function.Supplier;

import org.openqa.selenium.WebDriver;

import java.util.List;
import java.util.stream.Collectors;

public class DemoQAMainPage extends BasePage {
    private final SeleniumActions actions;

    @FindBy(className = "banner-image")
    private WebElement bannerImage;

    @FindBy(className = "card-body")
    private List<WebElement> categoryCards;

    @FindBy(css = "a[href='https://demoqa.com'] img")
    private WebElement logo;

    public DemoQAMainPage(Supplier<WebDriver> driverSupplier) {
        super(driverSupplier, "https://demoqa.com");
        this.actions = SeleniumActions.getInstance(driverSupplier);
    }

    @Override
    public boolean isPageLoaded() {
        return false;
    }

    @Override
    protected By getUniqueElement() {
        return null;
    }

    public DemoQAElementsPage clickElementsCard() {
        clickCategoryCard("Elements");
        return new DemoQAElementsPage(driverSupplier);
    }

    public boolean isBannerImageDisplayed() {
        return actions.isDisplayed(bannerImage);
    }

    public String getBannerImageAltText() {
        return bannerImage.getAttribute("alt");
    }

    public int getCategoryCardsCount() {
        actions.waitForElements(ExpectedConditions.visibilityOfAllElements(categoryCards));
        return categoryCards.size();
    }

    public List<String> getCategoryCardTitles() {
        actions.waitForElements(ExpectedConditions.visibilityOfAllElements(categoryCards));
        return categoryCards.stream()
                .map(WebElement::getText)
                .collect(Collectors.toList());
    }

    public void clickCategoryCard(String cardTitle) {
        actions.waitForElements(ExpectedConditions.visibilityOfAllElements(categoryCards));

        WebElement categoryCard = categoryCards.stream()
                .filter(card -> card.getText().equals(cardTitle))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Category card not found: " + cardTitle));
        actions.click(categoryCard);
    }

    public WebElement getLogoElement() {
        actions.waitForElement(ExpectedConditions.visibilityOf(logo));
        return logo;
    }

    public WebElement getBannerImage() {
        return bannerImage;
    }
}
