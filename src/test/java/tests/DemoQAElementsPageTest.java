package tests;

import base.DriverManager;
import org.testng.annotations.Test;
import org.testng.Assert;
import pages.DemoQAElementsPage;
import base.BaseTest;
import com.aventstack.extentreports.Status;
import utils.listeners.ExtentTestManager;
import implementation.HealeniumSeleniumActions;

public class DemoQAElementsPageTest extends BaseTest {

    @Test(description = "Verify Text Box functionality")
    public void verifyTextBox() {
        DemoQAElementsPage elementsPage = new DemoQAElementsPage(() ->
                HealeniumSeleniumActions.getInstance(DriverManager::getDriver).getDriver());
        elementsPage.navigateToElementsPage();
        elementsPage.navigateToTextBox();
        String fullName = "Full Name Here";
        String email = "name@example.com";
        String currentAddress = "Current address here";
        String permanentAddress = "Permanent Address Here";
        elementsPage.fillTextBoxForm(fullName, email, currentAddress, permanentAddress);
        elementsPage.submitTextBoxForm();
        Assert.assertTrue(elementsPage.isOutputDisplayed(), "Output is not displayed");
        Assert.assertEquals(elementsPage.getOutputName().substring(5), fullName, "Output name doesn't match");
        Assert.assertEquals(elementsPage.getOutputEmail().substring(6), email, "Output email doesn't match");
        Assert.assertEquals(elementsPage.getOutputCurrentAddress().substring(17),
                currentAddress, "Output current address doesn't match");
        Assert.assertEquals(elementsPage.getOutputPermanentAddress().substring(20),
                permanentAddress, "Output permanent address doesn't match");

        ExtentTestManager.getTest().log(Status.PASS, "Text Box functionality verified successfully");
    }


    @Test(description = "Verify Check Box functionality")
    public void verifyCheckBox() {
        String HOME = "Home";
        String DOWNLOADS = "Downloads";
        String DESKTOP = "Desktop";
        DemoQAElementsPage elementsPage = new DemoQAElementsPage(() ->
                HealeniumSeleniumActions.getInstance(DriverManager::getDriver).getDriver());
        elementsPage.navigateToElementsPage();
        elementsPage.navigateToCheckBox();
        elementsPage.expandAllCheckBoxes();
        elementsPage.selectCheckBox(HOME);
        elementsPage.selectCheckBox(DOWNLOADS);
        Assert.assertTrue(elementsPage.isCheckBoxSelected(HOME), "Home checkbox is selected");
        Assert.assertTrue(elementsPage.isCheckBoxSelected(DOWNLOADS), "Downloads checkbox is not selected");
        Assert.assertTrue(elementsPage.isCheckBoxSelected(DESKTOP), "Desktop checkbox is not selected");
        ExtentTestManager.getTest().log(Status.PASS, "Check Box functionality verified successfully");
    }

    @Test(description = "Verify Radio Button functionality")
    public void verifyRadioButton() {
        DemoQAElementsPage elementsPage = new DemoQAElementsPage(() ->
                HealeniumSeleniumActions.getInstance(DriverManager::getDriver).getDriver());
        elementsPage.navigateToElementsPage();
        elementsPage.navigateToRadioButton();
        elementsPage.selectRadioButton("Impressive");
        Assert.assertTrue(elementsPage.isRadioButtonSelected("Impressive"),
                "Impressive radio button is not selected");
        Assert.assertEquals(elementsPage.getSelectedRadioButtonText(), "Impressive",
                "Selected radio button text doesn't match");
        ExtentTestManager.getTest().log(Status.PASS, "Radio Button functionality verified successfully");
    }

    @Test(description = "Verify Web Tables functionality")
    public void verifyWebTables() {
        DemoQAElementsPage elementsPage = new DemoQAElementsPage(() ->
                HealeniumSeleniumActions.getInstance(DriverManager::getDriver).getDriver());
        elementsPage.navigateToElementsPage();
        elementsPage.navigateToWebTables();
        elementsPage.addNewRecord("Test", "Table", "name@email.com", "50", "1000000",
                "IT");
        Assert.assertTrue(elementsPage.isRecordPresent("Test", "Table"),
                "New record is not present in the table");
        ExtentTestManager.getTest().log(Status.PASS, "Web Tables functionality verified successfully");
    }
}
