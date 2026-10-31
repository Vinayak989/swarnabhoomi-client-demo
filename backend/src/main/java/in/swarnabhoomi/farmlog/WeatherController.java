package in.swarnabhoomi.farmlog;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {
    private static final Logger log = LoggerFactory.getLogger(WeatherController.class);
    private static final String IMD_WARNING_RSS =
            "https://mausam.imd.gov.in/imd_latest/contents/dist_nowcast_rss.php";
    private static final Pattern OFFSET_PATTERN = Pattern.compile("([+-])(\\d{1,2}):(\\d{2})$");

    private final RestClient client = createHttp11Client();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, CachedWeather> currentWeatherCache = new ConcurrentHashMap<>();

    private record CachedWeather(Map<String, Object> details, Instant expiresAt) {}

    private RestClient createHttp11Client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "SwarnabhoomiFarmDiary/1.0")
                .defaultHeader("Accept", "application/json, application/rss+xml, application/xml, text/xml, */*")
                .build();
    }

    @GetMapping("/{pincode}")
    public Map<String, Object> byPin(@PathVariable String pincode) {
        if (!pincode.matches("[1-9][0-9]{5}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "वैध ६ अंकी पिनकोड द्या");
        }

        try {
            String postalBase = System.getenv().getOrDefault(
                    "POSTAL_API_URL", "https://api.postalpincode.in/pincode/");
            JsonNode pin = mapper.readTree(
                    client.get().uri(postalBase + pincode).retrieve().body(String.class));
            JsonNode office = pin.path(0).path("PostOffice").path(0);
            if (office.isMissingNode() || office.isNull()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "या पिनकोडचा जिल्हा सापडला नाही");
            }

            String district = office.path("District").asText();
            String state = office.path("State").asText();
            String cacheKey = normalize(district) + ":" + normalize(state);
            CachedWeather cached = currentWeatherCache.get(cacheKey);
            Map<String, Object> result = cached != null && cached.expiresAt().isAfter(Instant.now())
                    ? new LinkedHashMap<>(cached.details())
                    : fetchCurrentWeather(district, state);
            if (cached == null || !cached.expiresAt().isAfter(Instant.now())) {
                currentWeatherCache.put(cacheKey, new CachedWeather(
                        Collections.unmodifiableMap(new LinkedHashMap<>(result)), Instant.now().plusSeconds(600)));
            }

            try {
                String rss = client.get().uri(IMD_WARNING_RSS).retrieve().body(String.class);
                List<String> warnings = activeWarningsForDistrict(rss, district);
                result.put("warning", warnings.isEmpty()
                        ? "सध्या सक्रिय जिल्हास्तरीय इशारा नाही"
                        : String.join(" • ", warnings));
            } catch (Exception warningError) {
                log.warn("IMD warning lookup failed for district {}", district, warningError);
                result.put("warning", "IMD जिल्हा इशारा सध्या मिळाला नाही");
            }
            result.put("warningSourceUrl", IMD_WARNING_RSS);
            return result;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("PIN weather lookup failed", e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "या पिनकोडसाठी हवामान माहिती मिळाली नाही. कृपया हवामान हाताने निवडा.",
                    e);
        }
    }

    private Map<String, Object> fetchCurrentWeather(String district, String state) throws Exception {
        String geocodingBase = System.getenv().getOrDefault(
                "OPEN_METEO_GEOCODING_URL", "https://geocoding-api.open-meteo.com/v1/search");
        String geocodingUrl = geocodingBase
                + "?name=" + encode(district)
                + "&count=10&language=en&format=json&countryCode=IN";
        JsonNode places = mapper.readTree(client.get().uri(geocodingUrl).retrieve().body(String.class))
                .path("results");
        JsonNode place = choosePlace(places, district, state);
        if (place.isMissingNode() || place.isNull()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "या पिनकोडच्या जिल्ह्यासाठी हवामान ठिकाण सापडले नाही");
        }

        String forecastBase = System.getenv().getOrDefault(
                "OPEN_METEO_FORECAST_URL", "https://api.open-meteo.com/v1/forecast");
        String forecastUrl = forecastBase
                + "?latitude=" + place.path("latitude").asText()
                + "&longitude=" + place.path("longitude").asText()
                + "&current=temperature_2m,weather_code,cloud_cover,precipitation"
                + "&timezone=auto";
        String apiKey = System.getenv("OPEN_METEO_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            forecastUrl += "&apikey=" + encode(apiKey);
        }
        JsonNode current = mapper.readTree(client.get().uri(forecastUrl).retrieve().body(String.class))
                .path("current");
        if (current.isMissingNode() || current.isNull() || !current.has("weather_code")) {
            throw new IllegalStateException("Weather API response did not include current conditions");
        }

        int weatherCode = current.path("weather_code").asInt(-1);
        double cloudCover = current.path("cloud_cover").asDouble(0);
        double precipitation = current.path("precipitation").asDouble(0);
        String condition = weatherCondition(weatherCode, cloudCover, precipitation);
        String forecastSource = System.getenv().getOrDefault(
                "OPEN_METEO_FORECAST_URL", "https://api.open-meteo.com/v1/forecast");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("district", district);
        result.put("state", state);
        result.put("condition", condition);
        result.put("temperatureC", current.path("temperature_2m").isNumber()
                ? current.path("temperature_2m").asDouble() : null);
        result.put("weatherCode", weatherCode);
        result.put("cloudCoverPercent", cloudCover);
        result.put("precipitationMm", precipitation);
        result.put("weatherUpdatedAt", current.path("time").asText(""));
        result.put("source", "Open-Meteo");
        result.put("sourceUrl", forecastSource);
        result.put("note", "हा जिल्हा केंद्रावरील सध्याच्या हवामानाचा अंदाज आहे; शेतातील प्रत्यक्ष हवामान वेगळे असू शकते.");
        return result;
    }

    private JsonNode choosePlace(JsonNode places, String district, String state) {
        if (!places.isArray()) return mapper.createObjectNode();
        String targetDistrict = normalize(district);
        String targetState = normalize(state);
        JsonNode best = mapper.createObjectNode();
        int bestScore = Integer.MIN_VALUE;
        for (JsonNode candidate : places) {
            if (!"IN".equalsIgnoreCase(candidate.path("country_code").asText())) continue;
            String name = normalize(candidate.path("name").asText());
            String admin2 = normalize(candidate.path("admin2").asText());
            String admin1 = normalize(candidate.path("admin1").asText());
            if (!name.equals(targetDistrict) && !admin2.equals(targetDistrict)) continue;
            int score = 0;
            if (name.equals(targetDistrict)) score += 4;
            if (admin2.equals(targetDistrict)) score += 3;
            if (admin1.equals(targetState)) score += 5;
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return bestScore < 0 ? mapper.createObjectNode() : best;
    }

    private String weatherCondition(int code, double cloudCover, double precipitation) {
        if ((code >= 51 && code <= 69) || (code >= 80 && code <= 84)
                || (code >= 91 && code <= 99)) return "पाऊस";
        if (code == 2) return "ऊन आणि ढगाळ";
        if (code == 3 || code == 45 || code == 48 || (code >= 70 && code <= 79)
                || (code >= 85 && code <= 90)) return "ढगाळ";
        if (precipitation > 0) return "पाऊस";
        if (cloudCover >= 80) return "ढगाळ";
        if (cloudCover >= 30) return "ऊन आणि ढगाळ";
        return "ऊन";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private List<String> activeWarningsForDistrict(String xml, String district) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        Document document = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        NodeList items = document.getElementsByTagName("item");
        String targetDistrict = normalize(district);
        Instant now = Instant.now();
        List<String> warnings = new ArrayList<>();

        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            if (!normalize(childText(item, "title")).equals(targetDistrict)) {
                continue;
            }

            Instant expires = parseInstant(childText(item, "Expires"));
            if (expires == null || !expires.isAfter(now)) {
                continue;
            }

            String description = childText(item, "description")
                    .replaceAll("\\s+", " ")
                    .replaceAll("\\s*,\\s*$", "")
                    .trim();
            String translated = translateWarning(description);
            if (!description.isEmpty() && !warnings.contains(translated)) {
                warnings.add(translated);
            }
        }
        return warnings;
    }

    private String translateWarning(String value) {
        String warning = value.toLowerCase(Locale.ROOT);
        if (warning.contains("extremely heavy rain")) return "अतिवृष्टीचा इशारा";
        if (warning.contains("very heavy rain")) return "अतिजोरदार पावसाचा इशारा";
        if (warning.contains("moderate rain")) return "मध्यम पाऊस: ताशी ५ ते १५ मिमी";
        if (warning.contains("light rain")) return "हलका पाऊस: ताशी ५ मिमीपेक्षा कमी";
        if (warning.contains("heavy rain")) return "जोरदार पाऊस: ताशी १५ मिमीपेक्षा जास्त";
        if (warning.contains("thunderstorm") || warning.contains("lightning")) {
            return "मेघगर्जना व विजांसह वादळी पावसाचा इशारा";
        }
        if (warning.contains("hail")) return "गारपीट होण्याचा इशारा";
        if (warning.contains("dust storm") || warning.contains("dust raising")) {
            return "धुळीच्या वादळाचा इशारा";
        }
        if (warning.contains("strong surface wind")) return "जोरदार वाऱ्यांचा इशारा";
        if (warning.contains("heat wave")) return "उष्णतेच्या लाटेचा इशारा";
        if (warning.contains("hot day")) return "उष्ण दिवसाचा इशारा";
        if (warning.contains("cold wave")) return "थंडीच्या लाटेचा इशारा";
        if (warning.contains("fog")) return "धुक्याचा इशारा";
        if (warning.contains("snow")) return "हिमवृष्टीचा इशारा";
        if (warning.contains("no weather") || warning.contains("no warning")) {
            return "सध्या सक्रिय जिल्हास्तरीय इशारा नाही";
        }
        return "जिल्ह्यासाठी IMD ने तात्काळ हवामान इशारा जारी केला आहे";
    }

    private String childText(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagName(name);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher offset = OFFSET_PATTERN.matcher(value.trim());
        if (offset.find()) {
            String hour = offset.group(2);
            if (hour.length() == 1) {
                hour = "0" + hour;
            }
            value = value.trim().substring(0, offset.start())
                    + offset.group(1) + hour + ":" + offset.group(3);
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String normalize(String value) {
        String plain = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return plain.replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{L}\\p{N}]", "")
                .toLowerCase(Locale.ROOT);
    }
}
