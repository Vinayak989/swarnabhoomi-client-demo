package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MandiPriceService {
    private static final Logger log = LoggerFactory.getLogger(MandiPriceService.class);
    private static final String RESOURCE_URL = "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070";
    private static final String SOURCE_URL = "https://www.data.gov.in/catalog/current-daily-price-various-commodities-various-markets-mandi";
    private static final Duration CACHE_LIFETIME = Duration.ofMinutes(15);

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${app.market-prices.api-key}")
    private String apiKey;

    private volatile Feed feed = new Feed(List.of(), null, null, false,
            "Maharashtra", "Data.gov.in / AGMARKNET", SOURCE_URL,
            "Market prices are temporarily unavailable.");

    public MandiPriceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Feed currentFeed() {
        Instant checkedAt = feed.checkedAt();
        if (checkedAt == null || checkedAt.isBefore(Instant.now().minus(CACHE_LIFETIME))) {
            refresh();
        }
        return feed;
    }

    public Feed refreshIfAllowed() {
        Instant checkedAt = feed.checkedAt();
        if (checkedAt == null || checkedAt.isBefore(Instant.now().minus(Duration.ofMinutes(1)))) {
            refresh();
        }
        return feed;
    }

    @Scheduled(fixedDelayString = "${app.market-prices.refresh-interval-ms:900000}",
            initialDelayString = "${app.market-prices.initial-delay-ms:10000}")
    public synchronized void refresh() {
        try {
            List<Price> prices = new ArrayList<>();
            String dataUpdatedAt = "";
            int offset = 0;
            int total = Integer.MAX_VALUE;
            while (offset < total && offset < 50_000) {
                JsonNode root = fetchPage(offset);
                if (offset == 0) total = root.path("total").asInt(0);
                dataUpdatedAt = value(root, "updated_date");
                JsonNode records = root.path("records");
                if (!records.isArray() || records.isEmpty()) break;
                for (JsonNode record : records) {
                    String commodity = value(record, "commodity");
                    if (commodity.isBlank()) continue;
                    prices.add(new Price(value(record, "state"), value(record, "district"),
                            value(record, "market"), commodity, value(record, "variety"),
                            value(record, "grade"), value(record, "arrival_date"),
                            decimal(record, "min_price"), decimal(record, "max_price"),
                            decimal(record, "modal_price")));
                }
                offset += records.size();
            }
            if (prices.isEmpty()) {
                throw new IOException("Data.gov.in returned no Maharashtra market prices.");
            }
            feed = new Feed(List.copyOf(prices), Instant.now(), dataUpdatedAt, true,
                    "Maharashtra", "Data.gov.in / AGMARKNET", SOURCE_URL, null);
        } catch (Exception exception) {
            log.warn("Unable to refresh Maharashtra mandi prices ({}): {}",
                    exception.getClass().getSimpleName(), exception.getMessage(), exception);
            Feed previous = feed;
            feed = new Feed(previous.prices(), Instant.now(), previous.dataUpdatedAt(), previous.available(),
                    previous.state(), previous.source(), previous.sourceUrl(),
                    "Unable to refresh market prices. Showing the last available data, if any.");
        }
    }

    private JsonNode fetchPage(int offset) throws IOException, InterruptedException {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String url = RESOURCE_URL + "?api-key=" + encodedKey
                + "&format=json&offset=" + offset + "&limit=1000&filters%5Bstate%5D=Maharashtra";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", "Swarnabhoomi/1.0 (agricultural market price directory)")
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Data.gov.in returned HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (!"ok".equalsIgnoreCase(root.path("status").asText())) {
            throw new IOException("Data.gov.in did not return a price dataset.");
        }
        return root;
    }

    private static String value(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isMissingNode() || value.isNull() ? "" : value.asText().trim();
    }

    private static BigDecimal decimal(JsonNode node, String name) {
        String value = value(node, name);
        if (value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record Feed(List<Price> prices, Instant checkedAt, String dataUpdatedAt, boolean available,
                       String state, String source, String sourceUrl, String error) {}

    public record Price(String state, String district, String market, String commodity, String variety,
                        String grade, String arrivalDate, BigDecimal minPrice, BigDecimal maxPrice,
                        BigDecimal modalPrice) {}
}
