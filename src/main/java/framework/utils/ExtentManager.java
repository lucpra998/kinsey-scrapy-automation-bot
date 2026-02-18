package framework.utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import framework.config.FrameworkConstants;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Singleton manager for ExtentReports.
 */
public final class ExtentManager {

	/** Singleton instance. */
	private static ExtentReports extent;

	/** Report file path used by this run. */
	private static String reportPath;

	private ExtentManager() {
		// Utility class
	}

	/**
	 * Returns the singleton ExtentReports instance.
	 *
	 * @return ExtentReports
	 */
	public static synchronized ExtentReports getInstance() {
		if (extent == null) {
			try {
				Files.createDirectories(Paths.get(FrameworkConstants.REPORT_DIR));
			} catch (Exception ignored) {
				// Best-effort
			}

			reportPath = FrameworkConstants.getReportPath();

			ExtentSparkReporter spark = new ExtentSparkReporter(reportPath);
			spark.config().setReportName("Kinsey UPC AddToCart Report");

			extent = new ExtentReports();
			extent.attachReporter(spark);

			extent.setSystemInfo("Base URL", FrameworkConstants.BASE_URL);
			extent.setSystemInfo("Batch Size", String.valueOf(FrameworkConstants.BATCH_SIZE));
			extent.setSystemInfo("Output Dir", FrameworkConstants.SCRAPING_OUTPUT_DIR);
		}
		return extent;
	}

	/**
	 * Flushes Extent report output to disk.
	 */
	public static synchronized void flush() {
		if (extent != null)
			extent.flush();
	}

	/**
	 * Returns the report path for this run.
	 *
	 * @return report path
	 */
	public static String getReportPathValue() {
		return reportPath;
	}
}
