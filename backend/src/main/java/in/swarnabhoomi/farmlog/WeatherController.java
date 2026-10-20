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
import java.text.Normalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/weather")
@CrossOrigin(origins = "http://localhost:5173")
public class WeatherController {
    private static final Logger log = LoggerFactory.getLogger(WeatherController.class);
    private static final String IMD_WARNING_RSS =
            "https://mausam.imd.gov.in/imd_latest/contents/dist_nowcast_rss.php";
    private static final Pattern OFFSET_PATTERN = Pattern.compile("([+-])(\\d{1,2}):(\\d{2})$");

    private final RestClient client = createHttp11Client();
    private final ObjectMapper mapper = new ObjectMapper();

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
            String rss = client.get().uri(IMD_WARNING_RSS).retrieve().body(String.class);
            List<String> warnings = activeWarningsForDistrict(rss, district);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("district", district);
            result.put("state", state);
            result.put("warning", warnings.isEmpty()
                    ? "सध्या सक्रिय जिल्हास्तरीय इशारा नाही"
                    : String.join(" • ", warnings));
            result.put("rainForecast", "");
            result.put("date", "");
            result.put("source", "भारत हवामान विभाग (IMD)");
            result.put("sourceUrl", IMD_WARNING_RSS);
            result.put("note", "ही IMD ची जिल्हास्तरीय तात्काळ इशारा माहिती आहे; शेतावरील प्रत्यक्ष हवामान वेगळे असू शकते.");
            return result;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("IMD weather lookup failed", e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "IMD कडून हवामान माहिती मिळाली नाही. कृपया हवामान हाताने निवडा.",
                    e);
        }
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
            if (!description.isEmpty() && !warnings.contains(description)) {
                warnings.add(description);
            }
        }
        return warnings;
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
