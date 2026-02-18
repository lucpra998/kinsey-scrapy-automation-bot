package framework.pages;

import framework.base.BasePage;
import framework.config.FrameworkConstants;
import framework.utils.ReportLogger;
import framework.utils.ScreenshotUtils;
import framework.utils.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Page Object for login flow.
 */
public class LoginPage extends BasePage {

	// Region: Locators (kept as-is)
	private static final By TXT_WELCOME_POPUP = By.xpath("//h2[contains(text(),'Welcome')]");
	private static final By BTN_ACCEPT_ALL = By.xpath("//button[normalize-space(.)='Accept all']");
	private static final By IMG_ACCOUNT_ICON = By.id("accountWidget");
	private static final By LINK_LOGIN = By
			.xpath("//button/following-sibling::div//div[@class='account-menu-login']//a");
	private static final By TXT_ALREADY_LOGGED_IN = By.xpath(
			"//button/following-sibling::div[@aria-labelledby='accountWidget']//div[contains(text(),'Welcome')]");
	private static final By BTN_LOGOUT = By.xpath(
			"//button/following-sibling::div[@aria-labelledby='accountWidget']//div//a[normalize-space(.)='Log out']");
	private static final By TXT_USERNAME = By.id("loginMail");
	private static final By TXT_PASSWORD = By.id("loginPassword");
	private static final By BTN_LOGIN = By.xpath("//div[@class='login-submit']//button");
	// Endregion

	/**
	 * Constructor.
	 *
	 * @param driver WebDriver
	 */
	public LoginPage(WebDriver driver) {
		super(driver);
	}

	/**
	 * Opens the website and handles welcome popup if present.
	 */
	public void open() {
		driver.get(FrameworkConstants.BASE_URL);
		WaitUtils.waitForPageLoad(driver, 30);
		WaitUtils.applyZoom(driver);

		if (WaitUtils.isVisible(driver, TXT_WELCOME_POPUP, 5)) {
			WaitUtils.clickWhenReady(driver, BTN_ACCEPT_ALL, 10);
			WaitUtils.waitForPageLoad(driver, 15);
			WaitUtils.applyZoom(driver);
		}

		ReportLogger.pass("Website opened");
	}

	/**
	 * Logs in and handles "already logged in" state.
	 *
	 * @param username username
	 * @param password password
	 */
	public void login(String username, String password) {
		try {
			WaitUtils.clickWhenReady(driver, IMG_ACCOUNT_ICON, 15);

			if (WaitUtils.isVisible(driver, LINK_LOGIN, 5)) {
				WaitUtils.clickWhenReady(driver, LINK_LOGIN, 10);
			} else if (WaitUtils.isVisible(driver, TXT_ALREADY_LOGGED_IN, 5)) {
				WaitUtils.clickWhenReady(driver, BTN_LOGOUT, 10);

				WaitUtils.waitForPageLoad(driver, 15);
				WaitUtils.applyZoom(driver);

				WaitUtils.clickWhenReady(driver, IMG_ACCOUNT_ICON, 10);
				WaitUtils.clickWhenReady(driver, LINK_LOGIN, 10);
			} else {
				throw new IllegalStateException("Login menu not in expected state.");
			}

			WaitUtils.waitForPageLoad(driver, 15);
			WaitUtils.applyZoom(driver);

			WaitUtils.type(driver, TXT_USERNAME, username, 20);
			WaitUtils.type(driver, TXT_PASSWORD, password, 20);

			WaitUtils.clickWhenReady(driver, BTN_LOGIN, 20);

			WaitUtils.waitForPageLoad(driver, 20);
			WaitUtils.applyZoom(driver);

			ReportLogger.pass("Login completed");

		} catch (Exception e) {
			ReportLogger.fail("Login failed: " + e.getMessage());

			String path = ScreenshotUtils.capture(driver, "LOGIN_FAIL");
			if (path != null)
				ReportLogger.attachScreenshot(path, "Login failure");

			throw e;
		}
	}
}
