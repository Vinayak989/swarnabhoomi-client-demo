package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketplacePriceAlertScheduler {
    private static final Logger log = LoggerFactory.getLogger(MarketplacePriceAlertScheduler.class);
    private static final String RESOURCE_URL = "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070";
    private static final DateTimeFormatter MARKET_DATE = DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT);

    private final MarketplacePriceAlertRepository alerts;
    private final BrowserPushService pushService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    @Value("${app.marketplace.price-alert-api-key:}") private String apiKey;

    public MarketplacePriceAlertScheduler(MarketplacePriceAlertRepository alerts,
            BrowserPushService pushService, ObjectMapper objectMapper) {
        this.alerts = alerts;
        this.pushService = pushService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${app.marketplace.price-alert-check-ms:900000}",
            initialDelayString = "${app.marketplace.price-alert-initial-delay-ms:60000}")
    public void checkPriceAlerts() {
        if (apiKey == null || apiKey.isBlank()) return;
        List<MarketplacePriceAlert> allAlerts = alerts.findAll();
        if (allAlerts.isEmpty()) return;

        Map<String, List<MarketplacePriceAlert>> byCommodity = new HashMap<>();
        allAlerts.forEach(alert -> byCommodity.computeIfAbsent(key(alert.getCommodity()), ignored -> new ArrayList<>()).add(alert));
        for (List<MarketplacePriceAlert> commodityAlerts : byCommodity.values()) {
            if (commodityAlerts.isEmpty()) continue;
            String commodity = commodityAlerts.get(0).getCommodity();
            List<MarketPrice> prices;
            try {
                prices = fetchCommodityPrices(commodity);
            } catch (Exception exception) {
                log.warn("Could not refresh AGMARKNET prices for {}: {}", commodity, exception.getMessage());
                continue;
            }
            for (MarketplacePriceAlert alert : commodityAlerts) {
                MarketPrice latest = prices.stream()
                        .filter(price -> alert.getDistrict() == null || alert.getDistrict().isBlank()
                                || key(alert.getDistrict()).equals(key(price.district())))
                        .max(Comparator.comparing(MarketPrice::date, Comparator.nullsFirst(Comparator.naturalOrder())))
                        .orElse(null);
                if (latest == null) continue;
                updateAlert(alert, latest);
            }
        }
    }

    private List<MarketPrice> fetchCommodityPrices(String commodity) throws Exception {
        String url = RESOURCE_URL + "?api-key=" + encode(apiKey)
                + "&format=json&filters%5Bstate%5D=Maharashtra&filters%5Bcommodity%5D=" + encode(commodity)
                + "&limit=1000&offset=0";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Data.gov.in returned HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode records = root.path("records");
        List<MarketPrice> result = new ArrayList<>();
        if (!records.isArray()) return result;
        for (JsonNode record : records) {
            BigDecimal modalPrice = decimal(record.path("modal_price").asText());
            if (modalPrice == null || modalPrice.signum() <= 0) continue;
            result.add(new MarketPrice(record.path("district").asText(""), record.path("market").asText(""),
                    marketDate(record.path("arrival_date").asText("")), modalPrice));
        }
        return result;
    }

    private void updateAlert(MarketplacePriceAlert alert, MarketPrice observation) {
        BigDecimal price = alert.getUnit() == MarketplacePriceAlert.PriceUnit.KG
                ? observation.modalPrice().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                : observation.modalPrice();
        boolean matched = alert.getDirection() == MarketplacePriceAlert.Direction.BELOW
                ? price.compareTo(alert.getThreshold()) <= 0 : price.compareTo(alert.getThreshold()) >= 0;
        boolean newlyMatched = matched && !alert.isCurrentlyMatched();
        alert.setCurrentlyMatched(matched);
        alert.setLastPrice(price.setScale(2, RoundingMode.HALF_UP));
        if (newlyMatched) alert.setLastTriggeredAt(java.time.Instant.now());
        alerts.save(alert);
        if (!newlyMatched) return;
        String direction = alert.getDirection() == MarketplacePriceAlert.Direction.BELOW ? "कमी" : "जास्त";
        String market = observation.market().isBlank() ? observation.district() : observation.market() + " · " + observation.district();
        try {
            pushService.notifyUser(alert.getUserId(), alert.getCommodity() + " चा बाजारभाव " + direction + " झाला",
                    market + " · ₹" + alert.getLastPrice().toPlainString() + " / "
                            + (alert.getUnit() == MarketplacePriceAlert.PriceUnit.KG ? "किलो" : "क्विंटल"),
                    "/?tab=prices");
        } catch (RuntimeException exception) {
            log.debug("Price alert push was not delivered for alert {}: {}", alert.getId(), exception.getMessage());
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal decimal(String value) {
        try { return new BigDecimal(value.trim().replace(",", "")); }
        catch (RuntimeException exception) { return null; }
    }

    private static LocalDate marketDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE); }
        catch (DateTimeParseException ignored) {
            try { return LocalDate.parse(value, MARKET_DATE); }
            catch (DateTimeParseException ignoredAgain) { return null; }
        }
    }

    private record MarketPrice(String district, String market, LocalDate date, BigDecimal modalPrice) {}
}
