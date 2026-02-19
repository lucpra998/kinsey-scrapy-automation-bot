package framework.tests;

import framework.config.FrameworkConstants;
import framework.driver.DriverFactory;
import framework.driver.DriverManager;
import framework.pages.LoginPage;
import framework.pages.ProductSearchPage;
import framework.utils.CSVUtils;
import framework.utils.FileUtils;
import framework.utils.ProgressTracker;
import framework.utils.ReportLogger;
import framework.utils.ScreenshotUtils;
import framework.utils.WaitUtils;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Parallel runner: each TestNG invocation processes one batch with its own
 * driver and CSV. Use testng-parallel.xml to enable parallelism.
 */
public class UPCAddToCartParallelTest {

	private static final int UPC_MIN_LEN = 8;
	private static final int UPC_MAX_LEN = 14;

	/** Creates parallel batch slices of the UPC list. */
	@DataProvider(name = "batches", parallel = true)
	public Object[][] batches() {
		List<String> allUpcs = FileUtils.readUpcs(FrameworkConstants.UPC_FILE);
		if (allUpcs == null || allUpcs.isEmpty()) {
			throw new RuntimeException("No UPCs found in: " + FrameworkConstants.UPC_FILE);
		}

		// Resume support: skip UPCs that were already processed in a previous run.
		Set<String> processed = ProgressTracker.loadProcessed();
		List<String> upcs;
		if (processed.isEmpty()) {
			upcs = allUpcs;
		} else {
			upcs = new ArrayList<>(allUpcs);
			upcs.removeAll(processed);
			ReportLogger.info("Resuming run: " + processed.size() + " UPCs already done, "
					+ upcs.size() + " remaining.");
		}

		int batchSize = FrameworkConstants.BATCH_SIZE;
		int batchCount = (int) Math.ceil((double) upcs.size() / batchSize);

		Object[][] data = new Object[batchCount][2];

		int batchNumber = 0;
		for (int i = 0; i < upcs.size(); i += batchSize) {
			batchNumber++;
			int end = Math.min(i + batchSize, upcs.size());
			List<String> batchUpcs = new ArrayList<>(upcs.subList(i, end));
			data[batchNumber - 1][0] = batchNumber;
			data[batchNumber - 1][1] = batchUpcs;
		}

		return data;
	}

	/** Processes one batch with its own driver and CSV. */
	@Test(dataProvider = "batches")
	public void processBatch(int batchNumber, List<String> upcs) {
		String csvPath = FrameworkConstants.getResultCsvPath(batchNumber);
		CSVUtils.initCsvFull(csvPath);

		WebDriver driver = DriverFactory.initDriver();
		DriverManager.set(driver);

		try {
			ReportLogger.info("Starting parallel batch: " + batchNumber + " | CSV: " + csvPath);

			relogin(driver);

			ProductSearchPage productPage = new ProductSearchPage(driver);

			for (String upc : upcs) {
				driver = processUpcWithRecovery(driver, csvPath, upc, productPage);
				productPage = new ProductSearchPage(driver);
			}

			ReportLogger.pass("Batch completed: " + batchNumber);

		} finally {
			try {
				driver.quit();
			} catch (Exception ignored) {
			}
			DriverManager.unload();
			CSVUtils.close(csvPath);
		}
	}

	/** Handles retries and driver recovery for a single UPC. */
	private WebDriver processUpcWithRecovery(WebDriver driver, String csvPath, String upc,
			ProductSearchPage productPage) {
		try {
			final WebDriver currentDriver = driver;
			WaitUtils.runWithRetry(FrameworkConstants.NETWORK_RETRY_COUNT, FrameworkConstants.NETWORK_RETRY_SLEEP_MS,
					() -> processSingleUpc(csvPath, upc, productPage, currentDriver));
			return driver;
		} catch (RuntimeException e) {
			if (WaitUtils.isDriverInvalid(e)) {
				ReportLogger.info("Driver invalid for UPC: " + upc + " -> restarting browser and retrying once.");
				driver = restartDriverAndLogin(driver);
				ProductSearchPage newPage = new ProductSearchPage(driver);
				try {
					processSingleUpc(csvPath, upc, newPage, driver);
					return driver;
				} catch (Exception ex2) {
					writeFailed(driver, csvPath, upc, ex2);
					return driver;
				}
			}

			writeFailed(driver, csvPath, upc, e);
			return driver;
		}
	}

	/** Restarts the driver and performs login. */
	private WebDriver restartDriverAndLogin(WebDriver driver) {
		try {
			if (driver != null)
				driver.quit();
		} catch (Exception ignored) {
		}

		driver = DriverFactory.initDriver();
		DriverManager.set(driver);
		relogin(driver);
		return driver;
	}

	/** Executes one UPC using inlined processing logic. */
	private void processSingleUpc(String csvPath, String upc, ProductSearchPage productPage, WebDriver currentDriver) {
		String normUpc = (upc == null) ? "" : upc.trim();
		if (!isValidUpc(normUpc)) {
			writeInvalidUpc(currentDriver, csvPath, normUpc);
			return;
		}

		ensureOnlineOrThrow(currentDriver);

		ProductSearchPage.SearchOutcome outcome = productPage.searchUpcWithOutcome(normUpc);

		if (outcome == ProductSearchPage.SearchOutcome.LOGIN_REQUIRED) {
			ReportLogger.info("Login required detected for UPC: " + normUpc + " -> re-login and retry.");
			relogin(currentDriver);

			outcome = productPage.searchUpcWithOutcome(normUpc);
			if (outcome == ProductSearchPage.SearchOutcome.LOGIN_REQUIRED) {
				throw new RuntimeException("Session expired; re-login did not recover.");
			}
		}

		if (outcome == ProductSearchPage.SearchOutcome.MAINTENANCE) {
			writeMaintenance(currentDriver, csvPath, normUpc);
			return;
		}

		if (outcome == ProductSearchPage.SearchOutcome.BLOCKED) {
			writeBlocked(currentDriver, csvPath, normUpc, "Blocked/CAPTCHA detected after search");
			backoffIfConfigured();
			return;
		}

		if (outcome == ProductSearchPage.SearchOutcome.NO_PRODUCTS_FOUND) {
			writeNoProductsFound(currentDriver, csvPath, normUpc);
			return;
		}

		ProductSearchPage.AddToCartState atcState = productPage.getAddToCartState();
		boolean outOfStock = productPage.isOutOfStock();
		boolean selectionRequired = productPage.isSelectionRequired();

		String url = productPage.currentUrl();
		String status;
		String message;
		String addToCartCsv;

		if (atcState == ProductSearchPage.AddToCartState.MISSING) {
			status = "ADD TO CART NOT PRESENT";
			message = "Add to Cart button not displayed";
			addToCartCsv = "NO";
		} else {
			status = "ADD TO CART PRESENT";
			addToCartCsv = "YES";
			if (outOfStock) {
				message = "Out of stock indicator detected";
			} else if (selectionRequired) {
				message = "Product requires selection before add to cart";
			} else if (atcState == ProductSearchPage.AddToCartState.DISABLED) {
				message = "Add to Cart is present but disabled";
			} else {
				message = "";
			}
		}

		if (!"ADD TO CART PRESENT".equals(status)) {
			String shot = ScreenshotUtils.capture(currentDriver, status + "_" + normUpc);
			if (shot != null)
				ReportLogger.attachScreenshot(shot, status + ": " + normUpc);
		}

		CSVUtils.appendFull(csvPath, normUpc, addToCartCsv, url, status, message, productPage.getProductName(),
				productPage.getItemNumber(), productPage.getProductUPC(), productPage.getVendorItemNumber(),
				productPage.getCasePack(), productPage.getProductDetailDescription(),
				productPage.getProductDetailPrice(), productPage.getMsrpPricing(), productPage.getStock(),
				productPage.getOutOfStock(), productPage.getBrandName(), productPage.getItemUpcEanNumber(),
				productPage.getBulletFeatures(), productPage.getCatalogPageNumber(), productPage.getDropShipOnly(),
				productPage.getMsrpPrice(), productPage.getPrimaryColor(), productPage.getProhibitedStates(),
				productPage.getVendorItemNo(), productPage.getYearLaunched(), productPage.getProp65Applies(),
				productPage.getProp65CancerHarm(), productPage.getProp65ReproductiveHarm());
		ProgressTracker.markProcessed(normUpc);

		if ("ADD TO CART PRESENT".equals(status))
			ReportLogger.pass("ADD TO CART PRESENT for UPC: " + normUpc);
		else
			ReportLogger.info(status + " for UPC: " + normUpc + " | " + message);
	}

	/** Performs a login using configured credentials. */
	private void relogin(WebDriver currentDriver) {
		LoginPage loginPage = new LoginPage(currentDriver);
		loginPage.open();
		loginPage.login(FrameworkConstants.USERNAME, FrameworkConstants.PASSWORD);
	}

	/** Writes NO_PRODUCTS_FOUND status row and screenshot. */
	private void writeNoProductsFound(WebDriver currentDriver, String csvPath, String upc) {
		String shot = ScreenshotUtils.capture(currentDriver, "NO_PRODUCTS_FOUND_" + upc);
		if (shot != null)
			ReportLogger.attachScreenshot(shot, "NO_PRODUCTS_FOUND: " + upc);

		appendEmptyRow(csvPath, upc, "NO PRODUCT FOUND", "NO PRODUCT FOUND", "No products found for this UPC",
				currentDriver);
	}

	/** Writes BLOCKED status row and screenshot. */
	private void writeBlocked(WebDriver currentDriver, String csvPath, String upc, String reason) {
		String shot = ScreenshotUtils.capture(currentDriver, "BLOCKED_" + upc);
		if (shot != null)
			ReportLogger.attachScreenshot(shot, "BLOCKED: " + upc);

		appendEmptyRow(csvPath, upc, "NA", "BLOCKED", reason, currentDriver);

		ReportLogger.fail("BLOCKED for UPC: " + upc + " | " + reason);
	}

	/** Writes FAILED status row and screenshot. */
	private void writeFailed(WebDriver currentDriver, String csvPath, String upc, Exception e) {
		String msg = safeMessage(e);

		String shot = ScreenshotUtils.capture(currentDriver, "FAILED_" + upc);
		if (shot != null)
			ReportLogger.attachScreenshot(shot, "FAILED: " + upc);

		appendEmptyRow(csvPath, upc, "NA", "FAILED", msg, currentDriver);

		ReportLogger.fail("UPC failed: " + upc + " | " + msg);
	}

	/** Writes MAINTENANCE status row and screenshot. */
	private void writeMaintenance(WebDriver currentDriver, String csvPath, String upc) {
		String shot = ScreenshotUtils.capture(currentDriver, "MAINTENANCE_" + upc);
		if (shot != null)
			ReportLogger.attachScreenshot(shot, "MAINTENANCE: " + upc);

		appendEmptyRow(csvPath, upc, "NA", "MAINTENANCE", "Site is in maintenance mode", currentDriver);
	}

	/** Writes INVALID_UPC status row. */
	private void writeInvalidUpc(WebDriver currentDriver, String csvPath, String upc) {
		appendEmptyRow(csvPath, upc, "NA", "INVALID_UPC", "Invalid UPC format", currentDriver);
		ReportLogger.info("INVALID_UPC for input: " + upc);
	}

	/** Optional backoff when blocked or rate-limited is detected. */
	private void backoffIfConfigured() {
		try {
			if (FrameworkConstants.BLOCKED_BACKOFF_MS > 0) {
				Thread.sleep(FrameworkConstants.BLOCKED_BACKOFF_MS);
			}
		} catch (InterruptedException ignored) {
		}
	}

	/** Detects offline browser state and throws if still offline after refresh. */
	private void ensureOnlineOrThrow(WebDriver currentDriver) {
		if (WaitUtils.isOfflinePage(currentDriver)) {
			currentDriver.navigate().refresh();
			WaitUtils.waitForPageLoad(currentDriver, 30);
		}
		if (WaitUtils.isOfflinePage(currentDriver)) {
			throw new RuntimeException("Browser is offline after refresh.");
		}
	}

	/** Appends a minimal CSV row with empty product fields. */
	private void appendEmptyRow(String csvPath, String upc, String addToCart, String status, String message,
			WebDriver currentDriver) {
		CSVUtils.appendFull(csvPath, upc, addToCart, safeUrl(currentDriver), status, message, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
				null, null);
		ProgressTracker.markProcessed(upc);
	}

	/** Safely returns the current URL or empty string. */
	private String safeUrl(WebDriver currentDriver) {
		try {
			return currentDriver.getCurrentUrl();
		} catch (Exception ignored) {
			return "";
		}
	}

	/** Safely returns a message from an exception. */
	private String safeMessage(Exception e) {
		String m = (e == null) ? "" : e.getMessage();
		return (m == null) ? "" : m;
	}

	/** Returns true if the UPC is a numeric string of expected length. */
	private boolean isValidUpc(String upc) {
		if (upc == null)
			return false;
		String t = upc.trim();
		if (t.length() < UPC_MIN_LEN || t.length() > UPC_MAX_LEN)
			return false;
		for (int i = 0; i < t.length(); i++) {
			char c = t.charAt(i);
			if (c < '0' || c > '9')
				return false;
		}
		return true;
	}
}
