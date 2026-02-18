package framework.config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Framework constants loaded from config.properties via ConfigLoader.
 */
public final class FrameworkConstants {

	private FrameworkConstants() {
	}

	public static final String BASE_URL = ConfigLoader.getString("base.url");
	public static final String USERNAME = ConfigLoader.getString("username");
	public static final String PASSWORD = ConfigLoader.getString("password");

	public static final int BATCH_SIZE = ConfigLoader.getInt("batch.size", 250);
	public static final String UPC_FILE = ConfigLoader.getString("upc.file");

	/** Optional de-duplication of UPC input list. */
	public static final boolean DEDUP_UPC = ConfigLoader.getBoolean("upc.deduplicate", true);

	public static final String SCRAPING_OUTPUT_DIR = ConfigLoader.getString("scraping.output.dir",
			"ScrapingOutputResults");

	public static final String REPORT_DIR = ConfigLoader.getString("report.dir", SCRAPING_OUTPUT_DIR + "/reports");

	public static final String SCREENSHOT_DIR = ConfigLoader.getString("screenshot.dir",
			SCRAPING_OUTPUT_DIR + "/screenshots");

	public static final boolean HEADLESS = ConfigLoader.getBoolean("headless", false);

	// 0.5 => 50%
	public static final double WINDOW_ZOOM = ConfigLoader.getDouble("window.zoom", 0.5);
	public static final int WINDOW_WIDTH = ConfigLoader.getInt("window.width", 1920);
	public static final int WINDOW_HEIGHT = ConfigLoader.getInt("window.height", 1080);

	/** Global screenshot switch. */
	public static final boolean SCREENSHOTS_ENABLED = ConfigLoader.getBoolean("screenshots.enabled", true);

	/** Retry for transient network/browser interruptions. */
	public static final int NETWORK_RETRY_COUNT = ConfigLoader.getInt("network.retry.count", 1);

	public static final int NETWORK_RETRY_SLEEP_MS = ConfigLoader.getInt("network.retry.sleep.ms", 1500);

	/** Optional slowdown when blocked is detected. */
	public static final int BLOCKED_BACKOFF_MS = ConfigLoader.getInt("blocked.backoff.ms", 5000);

	/** Returns a timestamp used for output naming. */
	public static String timestamp() {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
	}

	/** Builds a result CSV path for a batch. */
	public static String getResultCsvPath(int batchNumber) {
		return String.format("%s/results_batch_%d_%s.csv", SCRAPING_OUTPUT_DIR, batchNumber, timestamp());
	}

	/** Builds the report output path for this run. */
	public static String getReportPath() {
		return String.format("%s/Kinsey_UPC_Report_%s.html", REPORT_DIR, timestamp());
	}
}