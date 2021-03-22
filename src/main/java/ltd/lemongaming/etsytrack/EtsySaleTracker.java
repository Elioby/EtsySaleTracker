package ltd.lemongaming.etsytrack;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import javax.swing.text.html.HTML;

public class EtsySaleTracker {

	private static final String APPLICATION_NAME = "Google Sheets API Java Quickstart";
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

	private static final String SHEET_ID = "1DsdrPopNWqP0-zPrPBxlYbQAPRndUNbJeNOiG5cXfWk";
	private static final String SHEET_TABLE_RANGE = "SALES_TABLE";
	private static final String SHEET_TABLE_HEADER_RANGE = "SALES_TABLE_HEADER";

	private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
	private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

	private static final ZoneId UTC = ZoneId.of("UTC");
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final StringBuffer HTML_STRING_BUFFER = new StringBuffer();

	private static ZonedDateTime lastDataUpdate = LocalDate.now().atStartOfDay(UTC);

	private static Config config;

	public static void main(String... args) throws IOException, GeneralSecurityException, InterruptedException {
		File target = new File("application.conf");
		if (!target.exists()) {
			Files.copy(EtsySaleTracker.class.getResourceAsStream("/application.conf"), target.toPath());
			System.out.println("Created config file.");
		}

		config = ConfigFactory.parseFile(target).withFallback(ConfigFactory.load());

		Sheets service = createSheetsService();

		while (true) {
			ZonedDateTime checkTime = LocalDate.now().atStartOfDay(UTC);
			if (checkTime.isAfter(lastDataUpdate)) {
				service.spreadsheets().values()
						.update(SHEET_ID, SHEET_TABLE_HEADER_RANGE, getHeaderValues())
						.setValueInputOption("USER_ENTERED")
						.execute();

				service.spreadsheets().values().append(SHEET_ID, SHEET_TABLE_RANGE, getTodayShopValues())
						.setValueInputOption("USER_ENTERED")
						.execute();

				lastDataUpdate = checkTime;
			}

			Thread.sleep(10_000);
		}
	}

	private static Sheets createSheetsService() throws GeneralSecurityException, IOException {
		final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
		GoogleCredentials credentials = ServiceAccountCredentials.fromStream(EtsySaleTracker.class.getResourceAsStream(CREDENTIALS_FILE_PATH))
				.createScoped(SCOPES);

		HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);
		return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, requestInitializer)
				.setApplicationName(APPLICATION_NAME)
				.build();
	}

	private static ValueRange getHeaderValues() {
		List<Object> headers = new ArrayList<>();
		headers.add("Date");
		headers.addAll(config.getStringList("shops"));
		return new ValueRange().setValues(Collections.singletonList(headers));
	}

	private static ValueRange getTodayShopValues() throws IOException {
		List<Object> shopRow = new ArrayList<>();
		shopRow.add(ZonedDateTime.now(UTC).format(DATE_TIME_FORMATTER)); // add date at start
		for (String shopName : config.getStringList("shops")) {
			long sales = getEtsyShopSales(shopName);
			shopRow.add(sales);
		}

		return new ValueRange().setValues(Collections.singletonList(shopRow));
	}

	private static long getEtsyShopSales(String shopName) throws IOException {
		String shopBaseUrl = config.getString("shop-base-url");

		HTML_STRING_BUFFER.setLength(0);
		URL url = new URL(shopBaseUrl + shopName);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openConnection().getInputStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				HTML_STRING_BUFFER.append(line);
			}

			String html = HTML_STRING_BUFFER.toString();

			String[] salesSplit = html.split(" Sales</span>", 2);
			if (salesSplit.length >= 2) {
				String beforeSales = salesSplit[0];
				int startOfNumberIndex = beforeSales.lastIndexOf('>');
				return Long.parseLong(beforeSales.substring(startOfNumberIndex + 1).replace(",", ""));
			} else {
				String beforeSales = html.split(" Sales</a>", 2)[0];
				int startOfNumberIndex = beforeSales.lastIndexOf('>');
				return Long.parseLong(beforeSales.substring(startOfNumberIndex + 1).replace(",", ""));
			}
		}
	}

}
