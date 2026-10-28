package in.swarnabhoomi.farmlog;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@DependsOn("platformTables")
public class TradeService {
  static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
  final JdbcTemplate db;
  final FarmProductListingRepository listings;
  final MarketplacePurchaseRequestRepository orders;
  final AppUserRepository users;
  final BrowserPushService push;

  public TradeService(
      JdbcTemplate db,
      FarmProductListingRepository listings,
      MarketplacePurchaseRequestRepository orders,
      AppUserRepository users,
      BrowserPushService push) {
    this.db = db;
    this.listings = listings;
    this.orders = orders;
    this.users = users;
    this.push = push;
  }

  static void role(AppUser u, String... roles) {
    if (u == null || !Arrays.asList(roles).contains(u.getRole()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  static ResponseStatusException bad(String s) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, s);
  }

  static ResponseStatusException conflict(String s) {
    return new ResponseStatusException(HttpStatus.CONFLICT, s);
  }

  static String text(String s, int max) {
    if (s == null
        || s.isBlank()
        || s.length() > max
        || FarmMarketplaceController.containsContactDetails(s))
      throw bad("माहिती तपासा. संपर्क तपशील लिहू नका.");
    return s.trim();
  }

  static BigDecimal quantity(BigDecimal q) {
    if (q == null
        || q.signum() <= 0
        || q.compareTo(new BigDecimal("1000000000")) > 0
        || q.scale() > 3) throw bad("प्रमाण तपासा.");
    return q;
  }

  static BigDecimal money(BigDecimal q) {
    if (q == null
        || q.signum() <= 0
        || q.compareTo(new BigDecimal("9999999999.99")) > 0
        || q.scale() > 2) throw bad("किंमत तपासा.");
    return q;
  }

  static String address(String a) {
    if (!FarmMarketplaceController.validDeliveryAddress(
        MarketplacePurchaseRequest.Fulfillment.DELIVERY, a))
      throw bad("वितरणाचा पूर्ण पत्ता द्या; मोबाईल किंवा ईमेल लिहू नका.");
    return a.trim();
  }

  static LocalDate today() {
    return LocalDate.now(INDIA);
  }

  List<Map<String, Object>> rows(String sql, Object... args) {
    return db.query(
        sql,
        (r, n) -> {
          var m = new LinkedHashMap<String, Object>();
          var md = r.getMetaData();
          for (int i = 1; i <= md.getColumnCount(); i++) {
            String key = md.getColumnLabel(i).toLowerCase(Locale.ROOT);
            StringBuilder b = new StringBuilder();
            boolean up = false;
            for (char ch : key.toCharArray()) {
              if (ch == '_') {
                up = true;
                continue;
              }
              b.append(up ? Character.toUpperCase(ch) : ch);
              up = false;
            }
            Object value = r.getObject(i);
            if (value instanceof Timestamp t) value = t.toInstant().toString();
            if (value instanceof java.sql.Date d) value = d.toLocalDate().toString();
            m.put(b.toString(), value);
          }
          return m;
        },
        args);
  }

  Map<String, Object> one(String sql, Object... args) {
    var r = rows(sql, args);
    if (r.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return r.get(0);
  }

  long insert(String sql, Object... args) {
    var key = new GeneratedKeyHolder();
    db.update(
        c -> {
          var s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
          return s;
        },
        key);
    return key.getKey().longValue();
  }

  static long id(Map<String, Object> r, String k) {
    return ((Number) r.get(k)).longValue();
  }

  static BigDecimal decimal(Map<String, Object> r, String k) {
    return new BigDecimal(r.get(k).toString());
  }

  FarmProductListing listing(Long id) {
    return listings
        .findByIdForUpdate(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  FarmProductListing own(Long id, AppUser u) {
    role(u, "seller", "farmer");
    var l = listing(id);
    if (l.getOwner() == null || !l.getOwner().getId().equals(u.getId()))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return l;
  }

  void stock(FarmProductListing l, BigDecimal qty, AppUser buyer) {
    if (l.getOwner() == null
        || l.getOwner().getId().equals(buyer.getId())
        || Boolean.FALSE.equals(l.getAvailable())
        || !Double.isFinite(l.getQuantity())
        || BigDecimal.valueOf(l.getQuantity()).compareTo(qty) < 0
        || l.effectivePrice() == null)
      throw conflict("साठा किंवा किंमत बदलली आहे. माल पुन्हा तपासा.");
  }

  MarketplacePurchaseRequest reserve(
      FarmProductListing l,
      AppUser buyer,
      String name,
      BigDecimal qty,
      String a,
      BigDecimal agreedPrice) {
    stock(l, qty, buyer);
    var o =
        FarmMarketplaceController.newPurchaseRequest(
            l.getId(), l, buyer, name, qty, MarketplacePurchaseRequest.Fulfillment.DELIVERY, null);
    o.setDeliveryAddress(a);
    if (agreedPrice != null) o.setPricePerUnit(agreedPrice);
    FarmMarketplaceController.reserveStock(l, qty);
    listings.save(l);
    return orders.saveAndFlush(o);
  }

  void notifyOrder(MarketplacePurchaseRequest o, FarmProductListing l) {
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              @Override
              public void afterCommit() {
                try {
                  push.notifyUser(
                      o.getBuyerUserId(),
                      "नवीन ऑर्डर",
                      "ऑर्डर #" + o.getId() + " नोंदवली आहे.",
                      "/?tab=marketplace&view=orders");
                  push.notifyUser(
                      l.getOwner().getId(),
                      "नवीन खरेदी ऑर्डर",
                      "ऑर्डर #" + o.getId() + " तपासा.",
                      "/?tab=marketplace&view=seller");
                } catch (Exception ignored) {
                }
              }
            });
  }

  public record Offer(Instant startsAt, Instant endsAt) {}

  @Transactional
  public Map<String, Object> offer(Long id, Offer input, AppUser u) {
    var l = own(id, u);
    if (input.endsAt() != null
        && input.startsAt() != null
        && !input.endsAt().isAfter(input.startsAt()))
      throw bad("ऑफरची शेवटची वेळ सुरुवातीनंतर असावी.");
    l.setOfferStartsAt(input.startsAt());
    l.setOfferEndsAt(input.endsAt());
    listings.save(l);
    return listingSettings(l);
  }

  public record Batch(LocalDate harvestDate, String batchNumber, String notes, boolean published) {}

  @Transactional
  public Map<String, Object> batch(Long id, Batch b, AppUser u) {
    var l = own(id, u);
    if (b.harvestDate() != null && b.harvestDate().isAfter(today()))
      throw bad("काढणीचा दिनांक भविष्यातील असू शकत नाही.");
    l.setHarvestDate(b.harvestDate());
    l.setBatchNumber(
        b.batchNumber() == null || b.batchNumber().isBlank() ? null : text(b.batchNumber(), 80));
    l.setTraceabilityNotes(b.notes() == null || b.notes().isBlank() ? null : text(b.notes(), 1000));
    if (b.published() && (l.getHarvestDate() == null || l.getBatchNumber() == null))
      throw bad("सार्वजनिक करण्यापूर्वी काढणी व बॅच क्रमांक भरा.");
    l.setTraceabilityPublic(b.published());
    if (l.getTraceabilityToken() == null) l.setTraceabilityToken(UUID.randomUUID().toString());
    listings.save(l);
    return listingSettings(l);
  }

  Map<String, Object> listingSettings(FarmProductListing l) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", l.getId());
    m.put("productName", l.getProductName());
    m.put("unit", l.getUnit());
    m.put("offerStartsAt", l.getOfferStartsAt());
    m.put("offerEndsAt", l.getOfferEndsAt());
    m.put("harvestDate", l.getHarvestDate());
    m.put("batchNumber", l.getBatchNumber());
    m.put("notes", l.getTraceabilityNotes());
    m.put("published", l.isTraceabilityPublic());
    m.put("token", l.getTraceabilityToken());
    return m;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> sellerSettings(AppUser u) {
    role(u, "seller", "farmer");
    return listings.findAllByOwner_Id(u.getId()).stream().map(this::listingSettings).toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> settings(Long id, AppUser u) {
    role(u, "seller", "farmer");
    return listingSettings(
        listings
            .findByIdAndOwnerId(id, u.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
  }

  MarketplacePurchaseRequest lockedOrder(Long id) {
    return orders.lockById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  void ready(MarketplacePurchaseRequest o) {
    if (o.getStatus() != MarketplacePurchaseRequest.Status.ACCEPTED
        || o.getFulfillmentStage() != MarketplacePurchaseRequest.FulfillmentStage.READY_FOR_PICKUP
            && o.getFulfillmentStage()
                != MarketplacePurchaseRequest.FulfillmentStage.OUT_FOR_DELIVERY)
      throw conflict("माल वितरणासाठी तयार झाल्यावर कोड वापरता येईल.");
  }

  @Transactional
  public Map<String, Object> deliveryCode(Long id, boolean reset, AppUser u) {
    role(u, "buyer");
    var o = lockedOrder(id);
    if (!u.getId().equals(o.getBuyerUserId()))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    ready(o);
    var saved = rows("SELECT * FROM trade_delivery WHERE order_id=?", id);
    String nonce;
    Instant expiry;
    boolean renewed =
        saved.isEmpty()
            || reset
            || !Instant.parse(saved.get(0).get("expiresAt").toString()).isAfter(Instant.now());
    if (renewed) {
      nonce = UUID.randomUUID().toString();
      expiry = Instant.now().plus(Duration.ofHours(24));
      db.update("DELETE FROM trade_delivery WHERE order_id=?", id);
      db.update(
          "INSERT INTO trade_delivery(order_id,nonce,expires_at,attempts) VALUES(?,?,?,0)",
          id,
          nonce,
          Timestamp.from(expiry));
    } else {
      nonce = saved.get(0).get("nonce").toString();
      expiry = Instant.parse(saved.get(0).get("expiresAt").toString());
    }
    return Map.of(
        "code",
        code(id, nonce),
        "expiresAt",
        expiry.toString(),
        "attemptsRemaining",
        5 - (renewed ? 0 : ((Number) saved.get(0).get("attempts")).intValue()));
  }

  String code(Long id, String nonce) {
    var secrets =
        db.queryForList(
            "SELECT setting_value FROM platform_setting WHERE setting_key='delivery-hmac'",
            String.class);
    if (secrets.isEmpty()) {
      // Serialise first creation across orders and instances using the existing migration singleton
      // row.
      db.queryForList(
          "SELECT setting_key FROM platform_setting WHERE setting_key='delivery-lock' FOR UPDATE");
      secrets =
          db.queryForList(
              "SELECT setting_value FROM platform_setting WHERE setting_key='delivery-hmac'",
              String.class);
      if (secrets.isEmpty()) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String secret = Base64.getEncoder().encodeToString(bytes);
        db.update(
            "INSERT INTO platform_setting(setting_key,setting_value) VALUES('delivery-hmac',?)",
            secret);
        secrets = List.of(secret);
      }
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(Base64.getDecoder().decode(secrets.get(0)), "HmacSHA256"));
      byte[] bytes = mac.doFinal((id + ":" + nonce).getBytes(StandardCharsets.UTF_8));
      long number = java.nio.ByteBuffer.wrap(bytes).getInt() & 0xffffffffL;
      return String.format(Locale.ROOT, "%06d", number % 1000000);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public Map<String, Object> confirmDelivery(Long id, String supplied, AppUser u) {
    role(u, "seller", "farmer");
    var before =
        orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    var l = own(before.getListingId(), u);
    var o = lockedOrder(id);
    ready(o);
    if (o.getPaymentStatus() != MarketplacePurchaseRequest.PaymentStatus.PAID)
      throw conflict("प्रथम मिळालेले पेमेंट नोंदवा.");
    var r = one("SELECT * FROM trade_delivery WHERE order_id=? FOR UPDATE", id);
    if (((Number) r.get("attempts")).intValue() >= 5
        || Instant.parse(r.get("expiresAt").toString()).isBefore(Instant.now()))
      throw conflict("कोड कालबाह्य किंवा बंद आहे. खरेदीदाराने नवीन कोड घ्यावा.");
    boolean valid =
        supplied != null
            && supplied.matches("[0-9]{6}")
            && MessageDigest.isEqual(
                code(id, r.get("nonce").toString()).getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    db.update("UPDATE trade_delivery SET attempts=attempts+1 WHERE order_id=?", id);
    if (!valid) return Map.of("verified", false, "message", "कोड चुकीचा आहे. पुन्हा तपासा.");
    db.update("UPDATE trade_delivery SET verified_at=CURRENT_TIMESTAMP WHERE order_id=?", id);
    o.setStatus(MarketplacePurchaseRequest.Status.COMPLETED);
    o.setFulfillmentStage(MarketplacePurchaseRequest.FulfillmentStage.COMPLETED);
    orders.save(o);
    return Map.of("verified", true, "message", "वितरण पूर्ण झाले. संवाद बंद झाला आहे.");
  }

  public record Subscription(
      Long listingId,
      BigDecimal quantity,
      int intervalDays,
      LocalDate nextDate,
      BigDecimal maxPrice,
      String buyerName,
      String deliveryAddress) {}

  @Transactional
  public long subscribe(Subscription input, AppUser u) {
    role(u, "buyer");
    quantity(input.quantity());
    money(input.maxPrice());
    if (!Set.of(1, 7).contains(input.intervalDays())
        || input.nextDate() == null
        || input.nextDate().isBefore(today())
        || input.nextDate().isAfter(today().plusDays(365)))
      throw bad("दररोज किंवा दर आठवड्याचे वेळापत्रक निवडा.");
    var l = listing(input.listingId());
    stock(l, input.quantity(), u);
    if (input.maxPrice().compareTo(l.effectivePrice()) < 0)
      throw bad("कमाल दर सध्याच्या दरापेक्षा कमी आहे.");
    return insert(
        "INSERT INTO"
            + " trade_subscription(buyer_id,listing_id,buyer_name,quantity,unit,max_price,interval_days,next_date,delivery_address,status,created_at)"
            + " VALUES(?,?,?,?,?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP)",
        u.getId(),
        l.getId(),
        text(input.buyerName(), 100),
        input.quantity(),
        l.getUnit(),
        input.maxPrice(),
        input.intervalDays(),
        input.nextDate(),
        address(input.deliveryAddress()));
  }

  public List<Map<String, Object>> subscriptions(AppUser u) {
    role(u, "buyer");
    return rows(
        "SELECT s.*,l.product_name FROM trade_subscription s LEFT JOIN farm_product_listing l ON"
            + " l.id=s.listing_id WHERE s.buyer_id=? ORDER BY s.id DESC LIMIT 100",
        u.getId());
  }

  public List<Map<String, Object>> occurrences(Long id, AppUser u) {
    role(u, "buyer");
    one("SELECT id FROM trade_subscription WHERE id=? AND buyer_id=?", id, u.getId());
    return rows(
        "SELECT * FROM trade_occurrence WHERE subscription_id=? ORDER BY delivery_date DESC LIMIT"
            + " 100",
        id);
  }

  @Transactional
  public void subscriptionAction(Long id, String action, AppUser u) {
    role(u, "buyer");
    var r =
        one("SELECT * FROM trade_subscription WHERE id=? AND buyer_id=? FOR UPDATE", id, u.getId());
    if ("CANCELLED".equals(r.get("status"))) throw conflict("हे वेळापत्रक बंद झाले आहे.");
    switch (action) {
      case "PAUSE" -> db.update("UPDATE trade_subscription SET status='PAUSED' WHERE id=?", id);
      case "RESUME" ->
          db.update(
              "UPDATE trade_subscription SET status='ACTIVE',next_date=? WHERE id=?",
              LocalDate.parse(r.get("nextDate").toString()).isBefore(today())
                  ? today()
                  : LocalDate.parse(r.get("nextDate").toString()),
              id);
      case "CANCEL" -> db.update("UPDATE trade_subscription SET status='CANCELLED' WHERE id=?", id);
      case "SKIP" -> {
        LocalDate date = LocalDate.parse(r.get("nextDate").toString());
        db.update(
            "INSERT INTO trade_occurrence(subscription_id,delivery_date,outcome,detail,created_at)"
                + " VALUES(?,?,'SKIPPED','Skipped by buyer',CURRENT_TIMESTAMP)",
            id,
            date);
        db.update(
            "UPDATE trade_subscription SET next_date=? WHERE id=?",
            date.plusDays(((Number) r.get("intervalDays")).intValue()),
            id);
      }
      default -> throw bad("कृती तपासा.");
    }
  }

  public List<Long> dueSubscriptions() {
    return db.queryForList(
        "SELECT id FROM trade_subscription WHERE status='ACTIVE' AND next_date<=? ORDER BY id LIMIT"
            + " 100",
        Long.class,
        today());
  }

  @Transactional
  public void runSubscription(Long id) {
    var r = one("SELECT * FROM trade_subscription WHERE id=? FOR UPDATE", id);
    LocalDate date = LocalDate.parse(r.get("nextDate").toString());
    if (!"ACTIVE".equals(r.get("status")) || date.isAfter(today())) return;
    if (db.queryForObject(
            "SELECT COUNT(*) FROM trade_occurrence WHERE subscription_id=? AND delivery_date=?",
            Integer.class,
            id,
            date)
        > 0) return;
    var l = listings.findByIdForUpdate(id(r, "listingId")).orElse(null);
    AppUser buyer = users.findById(id(r, "buyerId")).orElse(null);
    String error = null;
    if (date.isBefore(today())) error = "Missed while app unavailable; no late order created";
    else if (l == null || buyer == null || !"buyer".equals(buyer.getRole()))
      error = "Product or buyer unavailable";
    else if (!l.getUnit().equals(r.get("unit"))) error = "Product unit changed";
    else if (l.effectivePrice() == null || l.effectivePrice().compareTo(decimal(r, "maxPrice")) > 0)
      error = "Current price exceeds your limit";
    else {
      try {
        stock(l, decimal(r, "quantity"), buyer);
      } catch (ResponseStatusException e) {
        error = "Insufficient stock or unavailable product";
      }
    }
    Long orderId = null;
    if (error == null) {
      var o =
          reserve(
              l,
              buyer,
              r.get("buyerName").toString(),
              decimal(r, "quantity"),
              r.get("deliveryAddress").toString(),
              null);
      orderId = o.getId();
      notifyOrder(o, l);
    }
    db.update(
        "INSERT INTO"
            + " trade_occurrence(subscription_id,delivery_date,outcome,detail,order_id,created_at)"
            + " VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",
        id,
        date,
        error == null ? "ORDERED" : "FAILED",
        error,
        orderId);
    LocalDate next = date;
    int interval = ((Number) r.get("intervalDays")).intValue();
    do {
      next = next.plusDays(interval);
    } while (!next.isAfter(today()));
    db.update("UPDATE trade_subscription SET next_date=? WHERE id=?", next, id);
  }

  public record Bulk(
      String productName,
      BigDecimal quantity,
      String unit,
      BigDecimal budget,
      LocalDate requiredDate,
      String district,
      String note) {}

  @Transactional
  public long bulk(Bulk b, AppUser u) {
    role(u, "buyer");
    quantity(b.quantity());
    money(b.budget());
    unit(b.unit());
    if (b.requiredDate() == null
        || b.requiredDate().isBefore(today())
        || b.requiredDate().isAfter(today().plusDays(365))) throw bad("आवश्यक दिनांक तपासा.");
    return insert(
        "INSERT INTO"
            + " trade_bulk(buyer_id,product_name,quantity,unit,budget,required_date,district,note,status,created_at)"
            + " VALUES(?,?,?,?,?,?,?,?,'OPEN',CURRENT_TIMESTAMP)",
        u.getId(),
        text(b.productName(), 120),
        b.quantity(),
        b.unit(),
        b.budget(),
        b.requiredDate(),
        text(b.district(), 100),
        b.note() == null || b.note().isBlank() ? null : text(b.note(), 500));
  }

  static void unit(String u) {
    if (!Set.of("kg", "quintal", "ton", "dozen", "crate", "piece", "litre")
        .contains(u == null ? "" : u)) throw bad("माप निवडा.");
  }

  public List<Map<String, Object>> bulks(AppUser u) {
    role(u, "buyer", "seller", "farmer");
    if ("buyer".equals(u.getRole()))
      return rows(
          "SELECT * FROM trade_bulk WHERE buyer_id=? ORDER BY id DESC LIMIT 100", u.getId());
    return rows(
        "SELECT id,product_name,quantity,unit,budget,required_date,district,note,status FROM"
            + " trade_bulk WHERE status='OPEN' AND required_date>=? ORDER BY id DESC LIMIT 100",
        today());
  }

  public record Quote(Long listingId, BigDecimal unitPrice, LocalDate validUntil, String note) {}

  @Transactional
  public long quote(Long id, Quote q, AppUser u) {
    role(u, "seller", "farmer");
    var b = one("SELECT * FROM trade_bulk WHERE id=? FOR UPDATE", id);
    if (!"OPEN".equals(b.get("status"))
        || LocalDate.parse(b.get("requiredDate").toString()).isBefore(today()))
      throw conflict("ही मागणी बंद झाली आहे.");
    var l = own(q.listingId(), u);
    if (!l.getUnit().equals(b.get("unit"))
        || BigDecimal.valueOf(l.getQuantity()).compareTo(decimal(b, "quantity")) < 0
        || Boolean.FALSE.equals(l.getAvailable())) throw bad("माप आणि उपलब्ध साठा तपासा.");
    money(q.unitPrice());
    if (q.validUntil() == null
        || q.validUntil().isBefore(today())
        || q.validUntil().isAfter(LocalDate.parse(b.get("requiredDate").toString())))
      throw bad("दराची वैधता तपासा.");
    if (db.queryForObject(
            "SELECT COUNT(*) FROM trade_quote WHERE bulk_id=? AND seller_id=? AND listing_id=?",
            Integer.class,
            id,
            u.getId(),
            l.getId())
        > 0) throw conflict("या मालासाठी दर आधीच पाठवला आहे.");
    return insert(
        "INSERT INTO"
            + " trade_quote(bulk_id,seller_id,listing_id,unit_price,valid_until,note,status,created_at)"
            + " VALUES(?,?,?,?,?,?,'OPEN',CURRENT_TIMESTAMP)",
        id,
        u.getId(),
        l.getId(),
        q.unitPrice(),
        q.validUntil(),
        q.note() == null || q.note().isBlank() ? null : text(q.note(), 500));
  }

  public List<Map<String, Object>> quotes(Long id, AppUser u) {
    role(u, "buyer", "seller", "farmer");
    var b = one("SELECT buyer_id FROM trade_bulk WHERE id=?", id);
    if ("buyer".equals(u.getRole())) {
      if (id(b, "buyerId") != u.getId()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
      return rows(
          "SELECT q.*,l.product_name,l.seller_name FROM trade_quote q JOIN farm_product_listing l"
              + " ON l.id=q.listing_id WHERE q.bulk_id=? ORDER BY q.unit_price,q.id",
          id);
    }
    return rows(
        "SELECT * FROM trade_quote WHERE bulk_id=? AND seller_id=? ORDER BY id", id, u.getId());
  }

  @Transactional
  public Map<String, Object> acceptQuote(
      Long bulkId, Long quoteId, String buyerName, String deliveryAddress, AppUser u) {
    role(u, "buyer");
    var b = one("SELECT * FROM trade_bulk WHERE id=? AND buyer_id=? FOR UPDATE", bulkId, u.getId());
    if (!"OPEN".equals(b.get("status"))
        || LocalDate.parse(b.get("requiredDate").toString()).isBefore(today()))
      throw conflict("ही मागणी बंद आहे.");
    var q = one("SELECT * FROM trade_quote WHERE id=? AND bulk_id=? FOR UPDATE", quoteId, bulkId);
    if (!"OPEN".equals(q.get("status"))
        || LocalDate.parse(q.get("validUntil").toString()).isBefore(today()))
      throw conflict("हा दर कालबाह्य झाला आहे.");
    var l = listing(id(q, "listingId"));
    if (l.getOwner() == null
        || l.getOwner().getId() != id(q, "sellerId")
        || !l.getUnit().equals(b.get("unit"))) throw conflict("विक्रीची नोंद बदलली आहे.");
    var o =
        reserve(
            l,
            u,
            text(buyerName, 100),
            decimal(b, "quantity"),
            address(deliveryAddress),
            decimal(q, "unitPrice"));
    db.update(
        "UPDATE trade_bulk SET status='AWARDED',accepted_quote_id=? WHERE id=?", quoteId, bulkId);
    db.update("UPDATE trade_quote SET status='CLOSED' WHERE bulk_id=?", bulkId);
    db.update("UPDATE trade_quote SET status='ACCEPTED',order_id=? WHERE id=?", o.getId(), quoteId);
    notifyOrder(o, l);
    return Map.of("orderId", o.getId());
  }

  @Transactional
  public void closeBulk(Long id, AppUser u) {
    role(u, "buyer");
    int n =
        db.update(
            "UPDATE trade_bulk SET status='CLOSED' WHERE id=? AND buyer_id=? AND status='OPEN'",
            id,
            u.getId());
    if (n != 1) throw conflict("ही मागणी बंद करता आली नाही.");
  }

  public record ReturnInput(String remedy, String reason, List<String> evidence) {}

  @Transactional
  public long requestReturn(Long id, ReturnInput input, AppUser u) {
    role(u, "buyer");
    var o = lockedOrder(id);
    if (!u.getId().equals(o.getBuyerUserId()))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    if (o.getStatus() != MarketplacePurchaseRequest.Status.COMPLETED)
      throw conflict("वितरण पूर्ण झालेल्या ऑर्डरसाठी विनंती करता येते.");
    if (!Set.of("REFUND", "REPLACEMENT").contains(input.remedy() == null ? "" : input.remedy()))
      throw bad("परतावा किंवा बदल निवडा.");
    if (input.evidence() != null
        && (input.evidence().size() > 3
            || input.evidence().stream().anyMatch(p -> !p.matches("[a-f0-9-]{36}"))))
      throw bad("जास्तीत जास्त तीन फोटो जोडा.");
    if (input.evidence() != null)
      for (String photo : input.evidence())
        if (db.queryForObject(
                "SELECT COUNT(*) FROM farm_photo WHERE id=? AND owner_id=? AND"
                    + " marketplace_public=FALSE AND original_filename LIKE 'return-%'",
                Integer.class, photo, u.getId())
            != 1) throw bad("फोटो तुमच्या खात्याचा असावा.");
    var l =
        listings
            .findById(o.getListingId())
            .orElseThrow(
                () -> conflict("विक्रेत्याची नोंद उपलब्ध नाही; मदतीतून प्रशासकाशी संपर्क करा."));
    if (db.queryForObject("SELECT COUNT(*) FROM trade_return WHERE order_id=?", Integer.class, id)
        > 0) throw conflict("या ऑर्डरची विनंती आधीच नोंदवली आहे.");
    return insert(
        "INSERT INTO"
            + " trade_return(order_id,buyer_id,seller_id,remedy,reason,evidence,status,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,?,'REQUESTED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        id,
        u.getId(),
        l.getOwner().getId(),
        input.remedy(),
        text(input.reason(), 1000),
        input.evidence() == null ? "" : String.join(",", input.evidence()));
  }

  public List<Map<String, Object>> returns(AppUser u) {
    role(u, "buyer", "seller", "farmer", "admin");
    String sql =
        "SELECT r.*,o.product_name,o.buyer_name,o.seller_name"
            + ("admin".equals(u.getRole())
                ? ",b.mobile_number AS buyer_mobile,s.mobile_number AS seller_mobile"
                : "")
            + " FROM trade_return r JOIN marketplace_purchase_request o ON o.id=r.order_id"
            + ("admin".equals(u.getRole())
                ? " LEFT JOIN app_user b ON b.id=r.buyer_id LEFT JOIN app_user s ON"
                    + " s.id=r.seller_id"
                : "");
    if ("admin".equals(u.getRole())) return rows(sql + " ORDER BY r.id DESC LIMIT 200");
    return rows(
            sql
                + " WHERE r."
                + ("buyer".equals(u.getRole()) ? "buyer_id" : "seller_id")
                + "=? ORDER BY r.id DESC LIMIT 100",
            u.getId())
        .stream()
        .map(
            r -> {
              if (!"buyer".equals(u.getRole())) r.remove("evidence");
              return r;
            })
        .toList();
  }

  public record Resolution(String status, String note, BigDecimal refundAmount, String reference) {}

  @Transactional
  public void resolveReturn(Long id, Resolution input, AppUser u) {
    role(u, "admin");
    var r = one("SELECT * FROM trade_return WHERE id=? FOR UPDATE", id);
    String before = r.get("status").toString();
    String next = input.status();
    if (!("REQUESTED".equals(before) && Set.of("APPROVED", "REJECTED").contains(next)
        || "APPROVED".equals(before) && Set.of("REFUNDED", "REPLACED").contains(next)))
      throw conflict("ही स्थिती बदलता येत नाही.");
    if ("REFUNDED".equals(next)) {
      if (!"REFUND".equals(r.get("remedy"))) throw bad("ही बदलाची विनंती आहे.");
      money(input.refundAmount());
      var o = orders.findById(id(r, "orderId")).orElseThrow();
      if (o.getPricePerUnit() == null
          || input.refundAmount().compareTo(o.getPricePerUnit().multiply(o.getQuantity())) > 0)
        throw bad("परतावा ऑर्डरच्या रकमेपेक्षा जास्त असू शकत नाही.");
      text(input.reference(), 120);
    }
    if ("REPLACED".equals(next) && !"REPLACEMENT".equals(r.get("remedy")))
      throw bad("ही परताव्याची विनंती आहे.");
    db.update(
        "UPDATE trade_return SET"
            + " status=?,admin_note=?,refund_amount=?,resolution_reference=?,updated_at=CURRENT_TIMESTAMP"
            + " WHERE id=?",
        next,
        text(input.note(), 1000),
        "REFUNDED".equals(next) ? input.refundAmount() : null,
        "REFUNDED".equals(next) ? input.reference() : null,
        id);
  }
}
