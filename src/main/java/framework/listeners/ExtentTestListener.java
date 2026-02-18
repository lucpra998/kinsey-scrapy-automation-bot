package framework.listeners;

import com.aventstack.extentreports.ExtentTest;
import framework.driver.DriverManager;
import framework.utils.ExtentManager;
import framework.utils.ReportLogger;
import framework.utils.ScreenshotUtils;
import org.openqa.selenium.WebDriver;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * TestNG listener for Extent report lifecycle. Uses ThreadLocal ExtentTest for
 * parallel safety and prefers DriverManager (ThreadLocal WebDriver) for
 * reliable screenshots in parallel runs.
 */
public class ExtentTestListener implements ITestListener {

	/** Thread-local ExtentTest to support parallel execution safely. */
	private static final ThreadLocal<ExtentTest> TEST = new ThreadLocal<>();

	/**
	 * Returns ExtentTest for the current thread.
	 *
	 * @return ExtentTest instance or null
	 */
	public static ExtentTest getTest() {
		return TEST.get();
	}

	/** Creates a new ExtentTest for the current test method. */
	@Override
	public void onTestStart(ITestResult result) {
		ExtentTest t = ExtentManager.getInstance().createTest(result.getMethod().getMethodName());
		TEST.set(t);
		ReportLogger.info("Test started: " + result.getMethod().getMethodName());
	}

	/** Logs success for the current test method. */
	@Override
	public void onTestSuccess(ITestResult result) {
		// No PASS screenshots by design (keeps execution fast and disk usage low)
		ReportLogger.pass("Test passed");
	}

	/** Logs failure and captures a screenshot if possible. */
	@Override
	public void onTestFailure(ITestResult result) {
		String msg = (result.getThrowable() != null) ? result.getThrowable().getMessage() : "";
		ReportLogger.fail("Test failed: " + msg);

		// Prefer ThreadLocal driver (parallel-safe)
		WebDriver driver = DriverManager.get();

		// Fallback for older sequential flow that still sets context attribute
		if (driver == null) {
			Object drv = result.getTestContext().getAttribute("DRIVER");
			if (drv instanceof WebDriver) {
				driver = (WebDriver) drv;
			}
		}

		if (driver != null) {
			String path = ScreenshotUtils.capture(driver, "TEST_FAIL");
			if (path != null) {
				ReportLogger.attachScreenshot(path, "Failure screenshot");
			}
		}
	}

	/** Flushes the report once the suite finishes. */
	@Override
	public void onFinish(ITestContext context) {
		ExtentManager.flush();
	}
}