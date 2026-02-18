package framework.pages;

import framework.utils.ReportLogger;
import framework.utils.WaitUtils;
import org.openqa.selenium.*;

import java.util.List;

/**
 * Page object for UPC search + PDP extraction. Adds robust outcome handling: -
 * NO_PRODUCTS_FOUND (multiple variants) - BLOCKED (access denied / captcha) -
 * LOGIN_REQUIRED (session expiry) - Spinner/async stabilization for Add to Cart
 * detection
 */
public class ProductSearchPage {

	/** Search flow outcome. */
	public enum SearchOutcome {
		PRODUCT_OPENED, NO_PRODUCTS_FOUND, BLOCKED, LOGIN_REQUIRED, MAINTENANCE
	}

	/** Add-to-cart state on PDP. */
	public enum AddToCartState {
		ENABLED, DISABLED, MISSING
	}

	private final WebDriver driver;

	// Locators (existing)
	private final By searchBox = By.xpath("//form//input[@id='header-main-search-input']");
	private final By searchBoxAlt = By.cssSelector("input[id*='search'][type='search'], input[id*='search'][type='text']");
	private final By link_productInfo = By.xpath("//div[@class='product-info']//a[@class='product-name']");
	private final By btn_addToCart = By
			.xpath("//form[@id='productDetailPageBuyProductForm']//button[@title='Add to Cart']");
	private final By select_variant = By
			.xpath("//form[@id='productDetailPageBuyProductForm']//select|//form[@id='productDetailPageBuyProductForm']//input[@type='radio']");

	private final By txt_productName = By.xpath("//div[@class='h1 product-name']");
	private final By txt_itemNumber = By.xpath("//div[@class='product-number']//span");
	private final By txt_productUPC = By
			.xpath("//div[@class='product-upc']//span[contains(text(),'UPC')]/following-sibling::span");
	private final By txt_vendorItemNumber = By.xpath(
			"//div[@class='product-vendor-item-no']//span[contains(text(),'Vendor Item No')]/following-sibling::span");
	private final By txt_casePack = By
			.xpath("//div[@class='product-case-pack']//span[contains(text(),'Case Pack')]/following-sibling::span");
	private final By txt_productDetailDescription = By.xpath("//div[@class='product-detail-description-text']");
	private final By txt_productDetailPrice = By.xpath(
			"//span[contains(@class,'customer-price')]");
	private final By txt_productDetailPriceFallback = By.xpath(
			"//p[contains(@class,'product-detail-price')]//span[contains(@class,'price') and not(contains(@class,'customer-price'))]");
	private final By txt_MSRPPricing = By
			.xpath("//div[@class='msrp-info']//span[contains(text(),'MSRP Pricing')]/following-sibling::span");
	private final By txt_stock = By
			.xpath("//div[@class='product-data']//span[@class='product-stock']//span[@class='stock']");
	private final By txt_outOfStock = By
			.xpath("//div[@class='product-data']//span[@class='product-stock']//span[contains(@class,'out-of-stock')]");
	private final By specTableBody = By.xpath("//table[contains(@class,'product-detail-properties-table')]/tbody");

	// New locators (negative-case coverage)
	private final By noProductsBanner1 = By.xpath("//*[contains(.,'No products found')]");
	private final By noProductsBanner2 = By.xpath("//*[contains(.,'No results')]");
	private final By noProductsBanner3 = By.xpath("//*[contains(.,'0 results') or contains(.,'0 Results')]");

	private final By loginEmailField = By.id("loginMail"); // session expired / login required

	private final By buyBoxSpinner = By.xpath(
			"//div[contains(@class,'spinner') or contains(@class,'loading') or contains(@class,'spinner-border')]");

	private final By txt_welcomePopup = By.xpath("//h2[contains(text(),'Welcome')]");
	private final By btn_acceptAll = By.xpath("//button[normalize-space(.)='Accept all']");

	// Captured fields
	private String productName, itemNumber, productUPC, vendorItemNumber, casePack, productDetailDescription,
			productDetailPrice, msrpPricing, stock, outOfStock;

	private String brandName, itemUpcEanNumber, bulletFeatures, catalogPageNumber, dropShipOnly, msrpPrice,
			primaryColor, prohibitedStates, vendorItemNo, yearLaunched, prop65Applies, prop65CancerHarm,
			prop65ReproductiveHarm;

	/** Constructs the page object with its WebDriver. */
	public ProductSearchPage(WebDriver driver) {
		this.driver = driver;
	}

	/** Backward compatible: original method signature. */
	public void searchUpc(String upc) {
		SearchOutcome outcome = searchUpcWithOutcome(upc);
		if (outcome != SearchOutcome.PRODUCT_OPENED) {
			throw new RuntimeException("Search did not open product page. Outcome=" + outcome);
		}
	}

	/**
	 * Robust search that returns explicit outcome instead of throwing for expected
	 * negatives.
	 */
	public SearchOutcome searchUpcWithOutcome(String upc) {
		ReportLogger.info("Searching UPC: " + upc);

		for (int attempt = 0; attempt < 2; attempt++) {
			WaitUtils.waitForPageLoad(driver, 20);
			WaitUtils.applyZoom(driver);

			if (!ensureSearchReady()) {
				if (WaitUtils.isVisible(driver, loginEmailField, 1))
					return SearchOutcome.LOGIN_REQUIRED;
				if (WaitUtils.isMaintenancePage(driver))
					return SearchOutcome.MAINTENANCE;
				if (WaitUtils.isBlockedPage(driver))
					return SearchOutcome.BLOCKED;
				throw new RuntimeException("Search input not available after recovery.");
			}

			WebElement box = getSearchBox(20);
			WaitUtils.scrollIntoViewCenter(driver, box);

			box.clear();
			box.sendKeys(upc);

			// Try native ENTER first
			String urlBefore = driver.getCurrentUrl();
			box.sendKeys(Keys.ENTER);

			// Fallback if nothing happened: force submit and retry ENTER
			try {
				Thread.sleep(800);
				if (urlBefore != null && urlBefore.equals(driver.getCurrentUrl())) {
					try {
						((JavascriptExecutor) driver).executeScript("arguments[0].form && arguments[0].form.submit();", box);
					} catch (Exception ignored) {
					}
					try {
						box.sendKeys(Keys.ENTER);
					} catch (Exception ignored) {
					}
				}
			} catch (InterruptedException ignored) {
			}

			long start = System.currentTimeMillis();
			boolean retriedSubmit = false;
			while (System.currentTimeMillis() - start < 45000) {
				if (WaitUtils.isVisible(driver, loginEmailField, 1))
					return SearchOutcome.LOGIN_REQUIRED;
				if (WaitUtils.isMaintenancePage(driver))
					return SearchOutcome.MAINTENANCE;
				if (WaitUtils.isBlockedPage(driver))
					return SearchOutcome.BLOCKED;
				if (WaitUtils.isVisible(driver, noProductsBanner1, 1) || WaitUtils.isVisible(driver, noProductsBanner2, 1)
						|| WaitUtils.isVisible(driver, noProductsBanner3, 1))
					return SearchOutcome.NO_PRODUCTS_FOUND;
				if (!driver.findElements(link_productInfo).isEmpty())
					break;
				if (WaitUtils.isVisible(driver, txt_productName, 1) || WaitUtils.isVisible(driver, btn_addToCart, 1))
					break;

				if (!retriedSubmit && urlBefore != null && urlBefore.equals(driver.getCurrentUrl())) {
					try {
						((JavascriptExecutor) driver).executeScript("arguments[0].form && arguments[0].form.submit();", box);
						retriedSubmit = true;
					} catch (Exception ignored) {
					}
				}

				try {
					Thread.sleep(500);
				} catch (InterruptedException ignored) {
				}
			}

			if (WaitUtils.isVisible(driver, loginEmailField, 1)) {
				return SearchOutcome.LOGIN_REQUIRED;
			}

			if (WaitUtils.isMaintenancePage(driver)) {
				return SearchOutcome.MAINTENANCE;
			}

			if (WaitUtils.isBlockedPage(driver)) {
				return SearchOutcome.BLOCKED;
			}

			if (WaitUtils.isVisible(driver, noProductsBanner1, 1) || WaitUtils.isVisible(driver, noProductsBanner2, 1)
					|| WaitUtils.isVisible(driver, noProductsBanner3, 1)) {
				return SearchOutcome.NO_PRODUCTS_FOUND;
			}

			if (!driver.findElements(link_productInfo).isEmpty()) {
				int attempts = 0;
				while (attempts < 3) {
					try {
						WebElement first = driver.findElements(link_productInfo).get(0);
						WaitUtils.scrollIntoViewCenter(driver, first);
						try {
							first.click();
						} catch (ElementClickInterceptedException e) {
							WaitUtils.jsClick(driver, first);
						}
						WaitUtils.waitForPageLoad(driver, 30);
						WaitUtils.applyZoom(driver);
						return SearchOutcome.PRODUCT_OPENED;
					} catch (StaleElementReferenceException e) {
						attempts++;
					} catch (Exception e) {
						attempts++;
						if (attempts >= 3)
							throw e;
					}
				}
				return SearchOutcome.NO_PRODUCTS_FOUND;
			}

			if (WaitUtils.isVisible(driver, txt_productName, 1) || WaitUtils.isVisible(driver, btn_addToCart, 1)) {
				WaitUtils.waitForPageLoad(driver, 20);
				WaitUtils.applyZoom(driver);
				return SearchOutcome.PRODUCT_OPENED;
			}
		}

		return SearchOutcome.NO_PRODUCTS_FOUND;
	}

	private boolean ensureSearchReady() {
		if (WaitUtils.isVisible(driver, searchBox, 3) || WaitUtils.isVisible(driver, searchBoxAlt, 3))
			return true;

		try {
			driver.get(framework.config.FrameworkConstants.BASE_URL);
			WaitUtils.waitForPageLoad(driver, 30);
			WaitUtils.applyZoom(driver);
			if (WaitUtils.isVisible(driver, txt_welcomePopup, 3)) {
				WaitUtils.clickWhenReady(driver, btn_acceptAll, 10);
				WaitUtils.waitForPageLoad(driver, 15);
				WaitUtils.applyZoom(driver);
			}
		} catch (Exception ignored) {
		}

		return WaitUtils.isVisible(driver, searchBox, 5) || WaitUtils.isVisible(driver, searchBoxAlt, 5);
	}

	private WebElement getSearchBox(long seconds) {
		try {
			return WaitUtils.waitVisible(driver, searchBox, seconds);
		} catch (Exception e) {
			return WaitUtils.waitVisible(driver, searchBoxAlt, seconds);
		}
	}

	/**
	 * Backward compatible boolean check. Uses stabilization + richer add-to-cart
	 * state logic.
	 */
	public boolean isAddToCartPresent() {
		return getAddToCartState() == AddToCartState.ENABLED;
	}

	/**
	 * Returns enabled/disabled/missing for Add-to-Cart (more accurate status
	 * mapping).
	 */
	public AddToCartState getAddToCartState() {
		resetCapturedData();

		// Bug C stabilization: spinner -> price/stock -> then evaluate
		WaitUtils.waitInvisibleIfPresent(driver, buyBoxSpinner, 10);

		// Best-effort: wait for price/stock to populate to reduce false negatives
		try {
			WaitUtils.waitTextNotEmpty(driver, txt_productDetailPrice, 8);
		} catch (Exception ignored) {
		}
		try {
			WaitUtils.waitTextNotEmpty(driver, txt_stock, 4);
		} catch (Exception ignored) {
		}

		captureProductDetails();
		captureProductSpecifications();

		try {
			WebElement btn = driver.findElement(btn_addToCart);
			if (!btn.isDisplayed())
				return AddToCartState.MISSING;

			// disabled detection
			String ariaDisabled = btn.getAttribute("aria-disabled");
			boolean disabled = !btn.isEnabled() || "true".equalsIgnoreCase(ariaDisabled)
					|| btn.getAttribute("disabled") != null;

			return disabled ? AddToCartState.DISABLED : AddToCartState.ENABLED;

		} catch (NoSuchElementException e) {
			return AddToCartState.MISSING;
		} catch (Exception e) {
			return AddToCartState.MISSING;
		}
	}

	/** Returns true when the current item appears to be out of stock. */
	public boolean isOutOfStock() {
		String oos = outOfStock;
		if (oos != null && !oos.trim().isEmpty())
			return true;

		String st = stock;
		if (st == null)
			return false;
		String s = st.toLowerCase();
		return s.contains("out of stock") || s.contains("0") && s.contains("stock");
	}

	/** Resets captured product fields before each read. */
	private void resetCapturedData() {
		productName = itemNumber = productUPC = vendorItemNumber = casePack = productDetailDescription = productDetailPrice = msrpPricing = stock = outOfStock = null;

		brandName = itemUpcEanNumber = bulletFeatures = catalogPageNumber = dropShipOnly = msrpPrice = primaryColor = prohibitedStates = vendorItemNo = yearLaunched = null;

		prop65Applies = prop65CancerHarm = prop65ReproductiveHarm = null;
	}

	/** Captures product details from PDP fields. */
	private void captureProductDetails() {
		productName = WaitUtils.safeGetText(driver, txt_productName, 8);
		itemNumber = WaitUtils.safeGetText(driver, txt_itemNumber, 8);
		productUPC = WaitUtils.safeGetText(driver, txt_productUPC, 8);
		vendorItemNumber = WaitUtils.safeGetText(driver, txt_vendorItemNumber, 8);
		casePack = WaitUtils.safeGetText(driver, txt_casePack, 8);
		productDetailDescription = WaitUtils.safeGetText(driver, txt_productDetailDescription, 8);

		productDetailPrice = WaitUtils.safeGetText(driver, txt_productDetailPrice, 4);
		if (productDetailPrice == null || productDetailPrice.isEmpty()) {
			productDetailPrice = WaitUtils.safeGetText(driver, txt_productDetailPriceFallback, 4);
		}

		msrpPricing = WaitUtils.safeGetText(driver, txt_MSRPPricing, 8);
		stock = WaitUtils.safeGetText(driver, txt_stock, 3);
		outOfStock = WaitUtils.safeGetText(driver, txt_outOfStock, 3);
	}

	/** Captures product specs from the specs table if present. */
	private void captureProductSpecifications() {
		try {
			WebElement tbody = WaitUtils.waitVisible(driver, specTableBody, 5);
			List<WebElement> rows = tbody.findElements(By.cssSelector("tr.properties-row"));

			for (WebElement row : rows) {
				String label = row.findElement(By.cssSelector("th.properties-label")).getText().replace("\n", "")
						.trim();
				String value = row.findElement(By.cssSelector("td.properties-value")).getText().trim();

				if (label.equals("Brand Name"))
					brandName = value;
				if (label.equals("Item UPC/EAN Number"))
					itemUpcEanNumber = value;
				if (label.equals("BulletFeatures"))
					bulletFeatures = value;
				if (label.equals("Catalog Page Number"))
					catalogPageNumber = value;
				if (label.equals("Drop Ship Only"))
					dropShipOnly = value;
				if (label.equals("MSRP Price"))
					msrpPrice = value;
				if (label.equals("Primary Color"))
					primaryColor = value;
				if (label.equals("ProhibitedStates"))
					prohibitedStates = value;
				if (label.equals("Vendor Item No."))
					vendorItemNo = value;
				if (label.equals("Year Launched"))
					yearLaunched = value;
				if (label.equals("Case Pack") && (casePack == null || casePack.isEmpty()))
					casePack = value;
				if (label.equals("Prop65Applies"))
					prop65Applies = value;
				if (label.equals("Prop65CancerHarm"))
					prop65CancerHarm = value;
				if (label.equals("Prop65ReproductiveHarm"))
					prop65ReproductiveHarm = value;
			}
		} catch (Exception ignored) {
			// optional specs
		}
	}

	/** Returns the current page URL. */
	public String currentUrl() {
		return driver.getCurrentUrl();
	}

	// Getters (unchanged)
	/** Returns the product name. */
	public String getProductName() {
		return productName;
	}

	/** Returns the item number. */
	public String getItemNumber() {
		return itemNumber;
	}

	/** Returns the product UPC. */
	public String getProductUPC() {
		return productUPC;
	}

	/** Returns the vendor item number. */
	public String getVendorItemNumber() {
		return vendorItemNumber;
	}

	/** Returns the case pack. */
	public String getCasePack() {
		return casePack;
	}

	/** Returns the product detail description. */
	public String getProductDetailDescription() {
		return productDetailDescription;
	}

	/** Returns the product detail price. */
	public String getProductDetailPrice() {
		return productDetailPrice;
	}

	/** Returns the MSRP pricing text. */
	public String getMsrpPricing() {
		return msrpPricing;
	}

	/** Returns the stock text. */
	public String getStock() {
		return stock;
	}

	/** Returns the out-of-stock label text if present. */
	public String getOutOfStock() {
		return outOfStock;
	}

	/** Returns the brand name. */
	public String getBrandName() {
		return brandName;
	}

	/** Returns the item UPC/EAN number. */
	public String getItemUpcEanNumber() {
		return itemUpcEanNumber;
	}

	/** Returns the bullet features text. */
	public String getBulletFeatures() {
		return bulletFeatures;
	}

	/** Returns the catalog page number. */
	public String getCatalogPageNumber() {
		return catalogPageNumber;
	}

	/** Returns the drop-ship-only flag. */
	public String getDropShipOnly() {
		return dropShipOnly;
	}

	/** Returns the MSRP price. */
	public String getMsrpPrice() {
		return msrpPrice;
	}

	/** Returns the primary color. */
	public String getPrimaryColor() {
		return primaryColor;
	}

	/** Returns the prohibited states text. */
	public String getProhibitedStates() {
		return prohibitedStates;
	}

	/** Returns the vendor item number from specs. */
	public String getVendorItemNo() {
		return vendorItemNo;
	}

	/** Returns the year launched. */
	public String getYearLaunched() {
		return yearLaunched;
	}

	/** Returns Prop65 applies value. */
	public String getProp65Applies() {
		return prop65Applies;
	}

	/** Returns Prop65 cancer harm value. */
	public String getProp65CancerHarm() {
		return prop65CancerHarm;
	}

	/** Returns Prop65 reproductive harm value. */
	public String getProp65ReproductiveHarm() {
		return prop65ReproductiveHarm;
	}

	/** Returns true when a variant/option selection is required. */
	public boolean isSelectionRequired() {
		try {
			List<WebElement> options = driver.findElements(select_variant);
			for (WebElement el : options) {
				if (el.isDisplayed() && el.isEnabled())
					return true;
			}
		} catch (Exception ignored) {
		}

		try {
			WebElement btn = driver.findElement(btn_addToCart);
			String text = String.valueOf(btn.getText()).toLowerCase();
			return text.contains("select") && text.contains("option");
		} catch (Exception ignored) {
			return false;
		}
	}
}