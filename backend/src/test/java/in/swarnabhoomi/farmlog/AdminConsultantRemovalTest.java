package in.swarnabhoomi.farmlog;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.config.import=",
      "spring.datasource.url=jdbc:h2:mem:consultant_removal;DB_CLOSE_DELAY=-1",
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
class AdminConsultantRemovalTest {
  private static final AtomicInteger NEXT = new AtomicInteger(3000);
  private static final String PASSWORD = "ConsultantRemoval123!";
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired OrganicConsultantRepository consultants;
  @Autowired ConsultantContactPaymentRepository payments;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder passwords;
  @Autowired AuthService auth;
  @MockBean ConsultantContactPaymentService gateway;
  AppUser admin;
  OrganicConsultant consultant;

  AppUser account(String role) {
    return users.save(
        new AppUser("+91987654" + NEXT.incrementAndGet(), role, passwords.encode(PASSWORD)));
  }

  String token(AppUser user) {
    return "Bearer " + auth.login(user.getMobileNumber(), PASSWORD, user.getRole()).token();
  }

  @BeforeEach
  void setup() {
    payments.deleteAll();
    consultants.deleteAll();
    admin = account("admin");
    consultant = new OrganicConsultant();
    consultant.setOwner(admin);
    consultant.setName("Test advisor");
    consultant.setPhone("+919876543210");
    consultant.setServiceArea("Pune");
    consultant.setExpertise("Soil health");
    consultant.setExperienceYears(8);
    consultant = consultants.save(consultant);
  }

  void remove() throws Exception {
    mvc.perform(
            delete("/api/admin/consultants/" + consultant.getId())
                .header("Authorization", token(admin)))
        .andExpect(status().isNoContent());
  }

  @Test
  void adminRemovesProfileFromDirectoryListAndCountsAndRepeatIsSafe() throws Exception {
    remove();
    Instant removed = consultants.findById(consultant.getId()).orElseThrow().getDeletedAt();
    assertThat(removed).isNotNull();
    mvc.perform(get("/api/admin/consultants").header("Authorization", token(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content", hasSize(0)));
    mvc.perform(get("/api/admin/overview").header("Authorization", token(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.consultantProfiles").value(0));
    for (String role : new String[] {"farmer", "seller", "consultant", "admin"})
      mvc.perform(get("/api/consultants").header("Authorization", token(account(role))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(0)));
    remove();
    assertThat(consultants.findById(consultant.getId()).orElseThrow().getDeletedAt())
        .isEqualTo(removed);
  }

  @ParameterizedTest
  @ValueSource(strings = {"buyer", "farmer", "seller", "consultant"})
  void nonAdminCannotRemoveAnyConsultant(String role) throws Exception {
    mvc.perform(
            delete("/api/admin/consultants/" + consultant.getId())
                .header("Authorization", token(account(role))))
        .andExpect(status().isForbidden());
    assertThat(consultants.findById(consultant.getId()).orElseThrow().getDeletedAt()).isNull();
  }

  @Test
  void anonymousRemovalAndMissingRecordsAreHandled() throws Exception {
    mvc.perform(delete("/api/admin/consultants/" + consultant.getId()))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/admin/consultants/99999999").header("Authorization", token(admin)))
        .andExpect(status().isNotFound());
    assertThat(consultants.countByDeletedAtIsNull()).isEqualTo(1);
  }

  @Test
  void removedProfileCannotStartNewPaymentOrdersAndPaidHistorySurvives() throws Exception {
    AppUser seller = account("seller");
    ConsultantContactPayment payment = new ConsultantContactPayment();
    payment.setConsultant(consultant);
    payment.setSeller(seller);
    payment.setRazorpayOrderId("order_test_paid");
    payment.setRazorpayPaymentId("pay_test_paid");
    payment.setStatus(ConsultantContactPayment.Status.PAID);
    payment.setPaidAt(Instant.now());
    payment = payments.save(payment);
    remove();
    mvc.perform(
            post("/api/consultants/" + consultant.getId() + "/contact-payment/order")
                .header("Authorization", token(seller)))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/consultants/" + consultant.getId() + "/contact-payment/verify")
                .header("Authorization", token(seller))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"orderId\":\"order_test_paid\",\"paymentId\":\"pay_test_paid\",\"signature\":\"already-captured\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unlocked").value(false));
    assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
        .isEqualTo(ConsultantContactPayment.Status.PAID);
    assertThat(payments.count()).isEqualTo(1);
    verify(gateway, never()).createOrder(anyString(), anyLong(), anyLong());
    verify(gateway, never()).verifyCapturedPayment(anyString(), anyString(), anyString());
  }

  @Test
  void inFlightCapturedPaymentIsRecordedWithoutReopeningRemovedProfile() throws Exception {
    AppUser seller = account("seller");
    ConsultantContactPayment payment = new ConsultantContactPayment();
    payment.setConsultant(consultant);
    payment.setSeller(seller);
    payment.setRazorpayOrderId("order_test_pending");
    payment = payments.save(payment);
    remove();
    mvc.perform(
            post("/api/consultants/" + consultant.getId() + "/contact-payment/verify")
                .header("Authorization", token(seller))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"orderId\":\"order_test_pending\",\"paymentId\":\"pay_test_captured\",\"signature\":\"test-signature\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unlocked").value(false));
    var saved = payments.findById(payment.getId()).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(ConsultantContactPayment.Status.PAID);
    assertThat(saved.getRazorpayPaymentId()).isEqualTo("pay_test_captured");
    assertThat(saved.getPaidAt()).isNotNull();
    verify(gateway)
        .verifyCapturedPayment("order_test_pending", "pay_test_captured", "test-signature");
  }
}
