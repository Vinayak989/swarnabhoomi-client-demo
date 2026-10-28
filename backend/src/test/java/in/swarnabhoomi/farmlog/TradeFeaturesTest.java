package in.swarnabhoomi.farmlog;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.config.import=",
      "spring.datasource.url=jdbc:h2:mem:trade_features;DB_CLOSE_DELAY=-1;MODE=MySQL",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "app.reminders.scheduler-enabled=false",
      "app.trade.scheduler-enabled=false",
      "app.database.backup.enabled=false",
      "app.storage.s3.bucket="
    })
@AutoConfigureMockMvc
class TradeFeaturesTest {
  @Autowired TradeService trade;
  @Autowired MaterialService materials;
  @Autowired FarmEntryController diary;
  @Autowired FarmProductListingRepository listings;
  @Autowired MarketplacePurchaseRequestRepository orders;
  @Autowired FarmEntryRepository entries;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder passwords;
  @Autowired AuthService auth;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired javax.sql.DataSource dataSource;
  static final AtomicInteger sequence = new AtomicInteger(2000);
  AppUser buyer, seller, other, admin;
  FarmProductListing product;

  AppUser user(String role) {
    return users.save(
        new AppUser(
            "+91987654" + sequence.incrementAndGet(), role, passwords.encode("TradeTesting123!")));
  }

  String token(AppUser u) {
    return "Bearer " + auth.login(u.getMobileNumber(), "TradeTesting123!", u.getRole()).token();
  }

  @BeforeEach
  void setup() {
    buyer = user("buyer");
    seller = user("farmer");
    other = user("seller");
    admin = user("admin");
    product = new FarmProductListing();
    product.setOwner(seller);
    product.setSellerName("Test Farmer");
    product.setPhone(seller.getMobileNumber());
    product.setAddress("Private farm address");
    product.setLatitude(0);
    product.setLongitude(0);
    product.setProductName("Tomato");
    product.setCropType("Tomato");
    product.setCategory("Vegetables");
    product.setQuantity(40);
    product.setPricePerUnit(new BigDecimal("100.00"));
    product.setDiscountPercent(new BigDecimal("20.00"));
    product.setUnit("kg");
    product.setSellerTokenHash("unused");
    product = listings.save(product);
  }

  MarketplacePurchaseRequest order(boolean completed) {
    var o =
        FarmMarketplaceController.newPurchaseRequest(
            product.getId(),
            listings.findById(product.getId()).orElseThrow(),
            buyer,
            "Buyer",
            new BigDecimal("2"),
            MarketplacePurchaseRequest.Fulfillment.DELIVERY,
            null);
    o.setDeliveryAddress("House 5, Pune 411001");
    o.setPaymentStatus(MarketplacePurchaseRequest.PaymentStatus.PAID);
    o.setStatus(
        completed
            ? MarketplacePurchaseRequest.Status.COMPLETED
            : MarketplacePurchaseRequest.Status.ACCEPTED);
    o.setFulfillmentStage(
        completed
            ? MarketplacePurchaseRequest.FulfillmentStage.COMPLETED
            : MarketplacePurchaseRequest.FulfillmentStage.OUT_FOR_DELIVERY);
    return orders.save(o);
  }

  @Test
  void scheduledDiscountIsEnforcedInPublicCatalogAndCheckoutSnapshots() throws Exception {
    trade.offer(
        product.getId(),
        new TradeService.Offer(Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200)),
        seller);
    var future = listings.findById(product.getId()).orElseThrow();
    assertThat(future.effectivePrice()).isEqualByComparingTo("100.00");
    var o = order(false);
    assertThat(o.getPricePerUnit()).isEqualByComparingTo("100.00");
    mvc.perform(get("/api/marketplace/listings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + product.getId() + ")].discountPercent", contains(0)));
    trade.offer(
        product.getId(),
        new TradeService.Offer(Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600)),
        seller);
    assertThat(listings.findById(product.getId()).orElseThrow().effectivePrice())
        .isEqualByComparingTo("100.00");
    assertThatThrownBy(
            () ->
                trade.offer(
                    product.getId(),
                    new TradeService.Offer(Instant.now(), Instant.now().minusSeconds(1)),
                    seller))
        .hasMessageContaining("400");
  }

  @Test
  void deliveryCodesAreBuyerOnlyPersistFailedAttemptsAndCloseOrder() throws Exception {
    var o = order(false);
    String sellerToken = "DeliverySellerTestToken";
    product.setSellerTokenHash(
        java.util.HexFormat.of()
            .formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(sellerToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    listings.save(product);
    mvc.perform(
            patch(
                    "/api/marketplace/seller/listings/"
                        + product.getId()
                        + "/purchase-requests/"
                        + o.getId())
                .header("Authorization", token(seller))
                .header("X-Seller-Token", sellerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fulfillmentStage\":\"COMPLETED\"}"))
        .andExpect(status().isConflict());
    var code = trade.deliveryCode(o.getId(), false, buyer).get("code").toString();
    assertThatThrownBy(() -> trade.deliveryCode(o.getId(), false, seller))
        .hasMessageContaining("403");
    assertThatThrownBy(() -> trade.confirmDelivery(o.getId(), code, other))
        .hasMessageContaining("404");
    for (int i = 0; i < 5; i++)
      assertThat(trade.confirmDelivery(o.getId(), "invalid", seller))
          .containsEntry("verified", false);
    assertThat(
            db.queryForObject(
                "SELECT attempts FROM trade_delivery WHERE order_id=?", Integer.class, o.getId()))
        .isEqualTo(5);
    assertThatThrownBy(() -> trade.confirmDelivery(o.getId(), code, seller))
        .hasMessageContaining("409");
    var fresh = trade.deliveryCode(o.getId(), true, buyer);
    assertThat(trade.confirmDelivery(o.getId(), fresh.get("code").toString(), seller))
        .containsEntry("verified", true);
    assertThat(orders.findById(o.getId()).orElseThrow().getStatus())
        .isEqualTo(MarketplacePurchaseRequest.Status.COMPLETED);
    mvc.perform(get("/api/trade/orders/" + o.getId() + "/delivery-code"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deliveryRequiresRecordedPaymentAndReadyState() {
    var o = order(false);
    o.setPaymentStatus(MarketplacePurchaseRequest.PaymentStatus.PENDING);
    orders.save(o);
    String code = trade.deliveryCode(o.getId(), false, buyer).get("code").toString();
    assertThatThrownBy(() -> trade.confirmDelivery(o.getId(), code, seller))
        .hasMessageContaining("409");
    o.setFulfillmentStage(MarketplacePurchaseRequest.FulfillmentStage.PREPARING);
    orders.save(o);
    assertThatThrownBy(() -> trade.deliveryCode(o.getId(), true, buyer))
        .hasMessageContaining("409");
  }

  @Test
  void simultaneousRecurringRunsCreateOneReservedOrder() throws Exception {
    long id =
        trade.subscribe(
            new TradeService.Subscription(
                product.getId(),
                new BigDecimal("2"),
                7,
                TradeService.today(),
                new BigDecimal("100"),
                "Buyer",
                "House 5, Pune 411001"),
            buyer);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      var tasks =
          List.<Callable<Void>>of(
              () -> {
                trade.runSubscription(id);
                return null;
              },
              () -> {
                trade.runSubscription(id);
                return null;
              });
      for (Future<Void> f : pool.invokeAll(tasks)) f.get(10, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM trade_occurrence WHERE subscription_id=?", Integer.class, id))
        .isEqualTo(1);
    assertThat(listings.findById(product.getId()).orElseThrow().getQuantity()).isEqualTo(38);
    assertThat(trade.occurrences(id, buyer).get(0)).containsEntry("outcome", "ORDERED");
    trade.subscriptionAction(id, "PAUSE", buyer);
    assertThat(trade.subscriptions(buyer).get(0)).containsEntry("status", "PAUSED");
    trade.subscriptionAction(id, "SKIP", buyer);
    assertThat(trade.occurrences(id, buyer)).hasSize(2);
    assertThatThrownBy(() -> trade.subscriptionAction(id, "RESUME", other))
        .hasMessageContaining("403");
  }

  @Test
  void recurringPriceGuardDoesNotReserveStockOrPlaceOrder() {
    long id =
        trade.subscribe(
            new TradeService.Subscription(
                product.getId(),
                new BigDecimal("2"),
                1,
                TradeService.today(),
                new BigDecimal("100"),
                "Buyer",
                "House 5, Pune 411001"),
            buyer);
    product.setPricePerUnit(new BigDecimal("200"));
    listings.save(product);
    trade.runSubscription(id);
    trade.runSubscription(id);
    assertThat(trade.occurrences(id, buyer)).hasSize(1);
    assertThat(trade.occurrences(id, buyer).get(0))
        .containsEntry("outcome", "FAILED")
        .containsEntry("orderId", null);
    assertThat(listings.findById(product.getId()).orElseThrow().getQuantity()).isEqualTo(40);
  }

  @Test
  void bulkQuotesRemainPrivateAndOnlyOneCanBecomeAnOrder() throws Exception {
    long b =
        trade.bulk(
            new TradeService.Bulk(
                "Tomato",
                new BigDecimal("10"),
                "kg",
                new BigDecimal("90"),
                TradeService.today().plusDays(3),
                "Pune",
                null),
            buyer);
    long q =
        trade.quote(
            b,
            new TradeService.Quote(
                product.getId(), new BigDecimal("75"), TradeService.today().plusDays(2), null),
            seller);
    assertThat(trade.bulks(seller).get(0))
        .doesNotContainKeys("buyerId", "deliveryAddress", "phone");
    var result = trade.acceptQuote(b, q, "Buyer", "House 5, Pune 411001", buyer);
    var order = orders.findById(((Number) result.get("orderId")).longValue()).orElseThrow();
    assertThat(order.getPricePerUnit()).isEqualByComparingTo("75");
    assertThat(order.getQuantity()).isEqualByComparingTo("10");
    assertThat(listings.findById(product.getId()).orElseThrow().getQuantity()).isEqualTo(30);
    assertThatThrownBy(() -> trade.acceptQuote(b, q, "Buyer", "House 5, Pune 411001", buyer))
        .hasMessageContaining("409");
    mvc.perform(get("/api/trade/bulk").header("Authorization", token(seller)))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString(buyer.getMobileNumber()))));
  }

  @Test
  void inventoryConsumptionEditingDeletingAndOfflineRetryAreAtomic() {
    long id =
        materials.create(
            new MaterialService.MaterialInput(
                "Seed", "Seeds", "kg", new BigDecimal("10"), BigDecimal.ONE, new BigDecimal("20")),
            seller);
    FarmEntry entry = new FarmEntry();
    entry.setEntryDate(TradeService.today());
    entry.setClientRequestId(UUID.randomUUID().toString());
    entry.setMaterialUsages(List.of(new MaterialService.Usage(id, new BigDecimal("3"))));
    var saved = diary.create(entry, seller);
    assertThat(saved.getMaterialUsageSummary()).isEqualTo("Seed: 3 kg");
    assertThat(new BigDecimal(materials.list(seller).get(0).get("quantity").toString()))
        .isEqualByComparingTo("7");
    FarmEntry retry = new FarmEntry();
    retry.setEntryDate(TradeService.today());
    retry.setClientRequestId(entry.getClientRequestId());
    retry.setMaterialUsages(entry.getMaterialUsages());
    assertThat(diary.create(retry, seller).getId()).isEqualTo(saved.getId());
    FarmEntry edit = new FarmEntry();
    edit.setEntryDate(TradeService.today());
    edit.setMaterialUsages(List.of(new MaterialService.Usage(id, BigDecimal.ONE)));
    diary.update(saved.getId(), edit, seller);
    assertThat(new BigDecimal(materials.list(seller).get(0).get("quantity").toString()))
        .isEqualByComparingTo("9");
    diary.delete(saved.getId(), seller);
    assertThat(new BigDecimal(materials.list(seller).get(0).get("quantity").toString()))
        .isEqualByComparingTo("10");
    assertThat(materials.history(id, seller)).hasSize(4);
  }

  @Test
  void evidencePhotosStayPrivateAndBelongToTheRequestingBuyer() throws Exception {
    var image = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "png", bytes);
    var file =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "produce.png", "image/png", bytes.toByteArray());
    String response =
        mvc.perform(
                multipart("/api/trade/evidence").file(file).header("Authorization", token(buyer)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String photo = json.readTree(response).get("id").asText();
    mvc.perform(get("/api/trade/evidence/" + photo).header("Authorization", token(buyer)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/trade/evidence/" + photo).header("Authorization", token(admin)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/trade/evidence/" + photo).header("Authorization", token(seller)))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/marketplace/photos/" + photo)).andExpect(status().isNotFound());
    var o = order(true);
    trade.requestReturn(
        o.getId(),
        new TradeService.ReturnInput("REPLACEMENT", "Damaged produce", List.of(photo)),
        buyer);
    assertThat(trade.returns(seller).get(0)).doesNotContainKey("evidence");
    AppUser another = user("buyer");
    mvc.perform(get("/api/trade/evidence/" + photo).header("Authorization", token(another)))
        .andExpect(status().isNotFound());
    mvc.perform(multipart("/api/trade/evidence").file(file).header("Authorization", token(seller)))
        .andExpect(status().isForbidden());
  }

  @Test
  void featureTablesAndIndexesCanBeInitializedTwice() {
    new PlatformTables(dataSource).afterPropertiesSet();
    new PlatformTables(dataSource).afterPropertiesSet();
  }

  @Test
  void insufficientMaterialRollsBackDiaryAndForeignOwnershipIsDenied() {
    long id =
        materials.create(
            new MaterialService.MaterialInput(
                "Fertilizer", "Fertilizer", "kg", BigDecimal.ONE, BigDecimal.ZERO, null),
            seller);
    FarmEntry entry = new FarmEntry();
    entry.setEntryDate(TradeService.today());
    entry.setClientRequestId(UUID.randomUUID().toString());
    entry.setMaterialUsages(List.of(new MaterialService.Usage(id, new BigDecimal("5"))));
    assertThatThrownBy(() -> diary.create(entry, seller)).hasMessageContaining("409");
    assertThat(
            entries.findFirstByOwnerIdAndClientRequestId(
                seller.getId(), entry.getClientRequestId()))
        .isEmpty();
    assertThatThrownBy(() -> materials.history(id, other)).hasMessageContaining("404");
    var adjustment =
        new MaterialService.Adjustment(BigDecimal.ONE, "Purchase", UUID.randomUUID().toString());
    materials.adjust(id, adjustment, seller);
    materials.adjust(id, adjustment, seller);
    assertThat(new BigDecimal(materials.list(seller).get(0).get("quantity").toString()))
        .isEqualByComparingTo("2");
  }

  @Test
  void returnResolutionIsAdminOnlyAndCannotExceedOrderValue() throws Exception {
    var o = order(true);
    long id =
        trade.requestReturn(
            o.getId(),
            new TradeService.ReturnInput("REFUND", "Damaged tomatoes", List.of()),
            buyer);
    assertThatThrownBy(
            () ->
                trade.resolveReturn(
                    id, new TradeService.Resolution("APPROVED", "Checked", null, null), seller))
        .hasMessageContaining("403");
    trade.resolveReturn(id, new TradeService.Resolution("APPROVED", "Checked", null, null), admin);
    assertThatThrownBy(
            () ->
                trade.resolveReturn(
                    id,
                    new TradeService.Resolution(
                        "REFUNDED", "Paid", new BigDecimal("500"), "Receipt-A"),
                    admin))
        .hasMessageContaining("400");
    trade.resolveReturn(
        id,
        new TradeService.Resolution("REFUNDED", "Paid", new BigDecimal("100"), "Receipt-A"),
        admin);
    assertThat(trade.returns(buyer).get(0))
        .containsEntry("status", "REFUNDED")
        .doesNotContainKeys("buyerMobile", "sellerMobile");
    mvc.perform(get("/api/trade/returns").header("Authorization", token(admin)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(seller.getMobileNumber())));
    assertThat(orders.findById(o.getId()).orElseThrow().getStatus())
        .isEqualTo(MarketplacePurchaseRequest.Status.COMPLETED);
  }

  @Test
  void batchPageIsOptInAndContainsNoContactOrLocation() throws Exception {
    var settings =
        trade.batch(
            product.getId(),
            new TradeService.Batch(TradeService.today(), "BATCH-A", "Fresh harvest", true),
            seller);
    String token = settings.get("token").toString();
    mvc.perform(get("/api/marketplace/batches/" + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.batchNumber").value("BATCH-A"))
        .andExpect(content().string(not(containsString(seller.getMobileNumber()))))
        .andExpect(content().string(not(containsString("Private farm address"))));
    trade.batch(
        product.getId(),
        new TradeService.Batch(TradeService.today(), "BATCH-A", null, false),
        seller);
    mvc.perform(get("/api/marketplace/batches/" + token)).andExpect(status().isNotFound());
    assertThatThrownBy(
            () ->
                trade.batch(
                    product.getId(),
                    new TradeService.Batch(
                        TradeService.today(), "BATCH-A", "Call 9876543210", true),
                    seller))
        .hasMessageContaining("400");
    mvc.perform(get("/api/platform-admin/backups").header("Authorization", token(buyer)))
        .andExpect(status().isForbidden());
  }
}
