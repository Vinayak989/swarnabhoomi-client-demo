package in.swarnabhoomi.farmlog;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PublicBatchController {
  private final JdbcTemplate db;

  public PublicBatchController(JdbcTemplate db) {
    this.db = db;
  }

  @GetMapping("/api/marketplace/batches/{token}")
  public Map<String, Object> batch(@PathVariable String token) {
    if (!token.matches("[a-f0-9-]{36}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return db
        .query(
            "SELECT"
                + " l.product_name,l.crop_type,l.batch_number,l.harvest_date,l.traceability_notes,l.seller_name,p.organic_verification_status,p.organic_certificate_valid_until"
                + " FROM farm_product_listing l LEFT JOIN account_profile p ON p.user_id=l.owner_id"
                + " WHERE l.traceability_token=? AND l.traceability_public=TRUE",
            (r, n) -> {
              var m = new LinkedHashMap<String, Object>();
              m.put("productName", safe(r.getString(1)));
              m.put("cropType", safe(r.getString(2)));
              m.put("batchNumber", safe(r.getString(3)));
              m.put("harvestDate", r.getString(4));
              m.put("notes", safe(r.getString(5)));
              m.put("sellerName", safe(r.getString(6)));
              var until = r.getDate(8);
              m.put(
                  "organicVerified",
                  "VERIFIED".equals(r.getString(7))
                      && until != null
                      && !until.toLocalDate().isBefore(TradeService.today()));
              m.put("certificateValidUntil", until == null ? null : until.toLocalDate().toString());
              m.put("source", "विक्रेत्याने दिलेली बॅच माहिती");
              return m;
            },
            token)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  static String safe(String s) {
    return FarmMarketplaceController.containsContactDetails(s) ? null : s;
  }
}
