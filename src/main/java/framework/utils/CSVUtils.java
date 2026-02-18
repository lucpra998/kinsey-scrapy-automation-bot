package framework.utils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CSV writer optimized for large runs (17k+ rows). Uses one BufferedWriter per
 * csvPath and keeps existing API intact.
 */
public final class CSVUtils {

	private static final Object LOCK = new Object();
	private static final Map<String, BufferedWriter> WRITERS = new ConcurrentHashMap<>();

	private CSVUtils() {
	}

	/** Sanitizes a value for CSV output. */
	private static String sanitize(String v) {
		if (v == null)
			return "\"\"";
		return "\"" + v.replace("\"", "'").replace("\n", " ").replace("\r", " ").trim() + "\"";
	}

	/** Initializes a CSV file with header row. */
	public static void initCsvFull(String csvPath) {
		synchronized (LOCK) {
			try {
				Path p = Paths.get(csvPath);
				if (p.getParent() != null)
					Files.createDirectories(p.getParent());

				close(csvPath);

				BufferedWriter bw = Files.newBufferedWriter(p, StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING);
				WRITERS.put(csvPath, bw);

				bw.write(
						"UPC,AddToCart,ProductURL,Status,Message,ProductName,ItemNumber,ProductUPC,VendorItemNumber,CasePack,"
								+ "ProductDetailDescription,ProductDetailPrice,MsrpPricing,Stock,OutOfStock,BrandName,ItemUpcEanNumber,"
								+ "BulletFeatures,CatalogPageNumber,DropShipOnly,MsrpPrice,PrimaryColor,ProhibitedStates,VendorItemNo,"
								+ "YearLaunched,Prop65Applies,Prop65CancerHarm,Prop65ReproductiveHarm");
				bw.newLine();
			} catch (IOException e) {
				throw new RuntimeException("Unable to init CSV: " + csvPath, e);
			}
		}
	}

	/** Appends a full row to the CSV. */
	public static void appendFull(String csvPath, String upc, String addToCart, String url, String status,
			String message, String productName, String itemNumber, String productUPC, String vendorItemNumber,
			String casePack, String productDetailDescription, String productDetailPrice, String msrpPricing,
			String stock, String outOfStock, String brandName, String itemUpcEanNumber, String bulletFeatures,
			String catalogPageNumber, String dropShipOnly, String msrpPrice, String primaryColor,
			String prohibitedStates, String vendorItemNo, String yearLaunched, String prop65Applies,
			String prop65CancerHarm, String prop65ReproductiveHarm) {

		synchronized (LOCK) {
			try {
				BufferedWriter bw = WRITERS.get(csvPath);
				if (bw == null) {
					Path p = Paths.get(csvPath);
					if (p.getParent() != null)
						Files.createDirectories(p.getParent());
					bw = Files.newBufferedWriter(p, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
					WRITERS.put(csvPath, bw);
				}

				bw.write(String.join(",", sanitize(upc), sanitize(addToCart), sanitize(url), sanitize(status),
						sanitize(message), sanitize(productName), sanitize(itemNumber), sanitize(productUPC),
						sanitize(vendorItemNumber), sanitize(casePack), sanitize(productDetailDescription),
						sanitize(productDetailPrice), sanitize(msrpPricing), sanitize(stock), sanitize(outOfStock),
						sanitize(brandName), sanitize(itemUpcEanNumber), sanitize(bulletFeatures),
						sanitize(catalogPageNumber), sanitize(dropShipOnly), sanitize(msrpPrice),
						sanitize(primaryColor), sanitize(prohibitedStates), sanitize(vendorItemNo),
						sanitize(yearLaunched), sanitize(prop65Applies), sanitize(prop65CancerHarm),
						sanitize(prop65ReproductiveHarm)));
				bw.newLine();
			} catch (IOException e) {
				throw new RuntimeException("Failed to append CSV: " + csvPath, e);
			}
		}
	}

	/** Flushes and closes a CSV writer if present. */
	public static void close(String csvPath) {
		synchronized (LOCK) {
			BufferedWriter bw = WRITERS.remove(csvPath);
			if (bw != null) {
				try {
					bw.flush();
				} catch (Exception ignored) {
				}
				try {
					bw.close();
				} catch (Exception ignored) {
				}
			}
		}
	}
}