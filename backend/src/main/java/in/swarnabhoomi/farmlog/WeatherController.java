package in.swarnabhoomi.farmlog;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.*;
import java.util.*;

@RestController
@RequestMapping("/api/weather")
@CrossOrigin(origins = "http://localhost:5173")
public class WeatherController {
    private final RestClient client = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();
    @GetMapping("/{pincode}")
    public Map<String,Object> byPin(@PathVariable String pincode) {
        if (!pincode.matches("[1-9][0-9]{5}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "वैध ६ अंकी पिनकोड द्या");
        try {
            String postalBase = System.getenv().getOrDefault("POSTAL_API_URL", "https://api.postalpincode.in/pincode/");
            JsonNode pin = mapper.readTree(client.get().uri(postalBase + pincode).retrieve().body(String.class));
            JsonNode office = pin.path(0).path("PostOffice").path(0);
            if (office.isMissingNode() || office.isNull()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "या पिनकोडचा जिल्हा सापडला नाही");
            String district = office.path("District").asText();
            JsonNode warnings = mapper.readTree(client.get().uri("https://api.imd.gov.in/api/v1/districtwarning").retrieve().body(String.class));
            JsonNode forecast = mapper.readTree(client.get().uri("https://api.imd.gov.in/api/v1/state_district_rainfall_forecast").retrieve().body(String.class));
            JsonNode hit = findByDistrict(warnings, district, "District");
            JsonNode rain = findByDistrict(forecast, district, "District");
            Map<String,Object> result = new LinkedHashMap<>();
            result.put("district", district); result.put("state", office.path("State").asText());
            result.put("warning", hit == null ? "जिल्हा इशारा उपलब्ध नाही" : text(hit, "day1_warning", "Day_1", "message"));
            result.put("rainForecast", rain == null ? "पर्जन्य अंदाज उपलब्ध नाही" : text(rain, "day1_distribution", "Day1"));
            result.put("date", rain == null ? "" : text(rain, "date_obs", "Date"));
            result.put("source", "भारत हवामान विभाग (IMD)"); result.put("sourceUrl", "https://api.imd.gov.in/public/api_reference.html");
            result.put("note", "जिल्हास्तरीय सरकारी अंदाज; शेतावरील प्रत्यक्ष स्थिती वेगळी असू शकते.");
            return result;
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "हवामान सेवा सध्या उपलब्ध नाही. हाताने हवामान निवडून नोंद जतन करा."); }
    }
    private JsonNode findByDistrict(JsonNode root, String district, String field) {
        JsonNode list = root.isArray() ? root : root.path("data");
        if (!list.isArray()) return null;
        for (JsonNode item : list) if (item.path(field).asText().equalsIgnoreCase(district)) return item;
        return null;
    }
    private String text(JsonNode n, String... fields) { for (String f : fields) if (!n.path(f).isMissingNode()) return n.path(f).asText(); return ""; }
}
