package in.swarnabhoomi.farmlog;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:support_chat_test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.reminders.scheduler-enabled=false"
})
@AutoConfigureMockMvc
class SupportChatControllerTest {
    private static final String PASSWORD = "SupportPass123!";

    @Autowired private MockMvc mvc;
    @Autowired private AuthService auth;
    @Autowired private AppUserRepository users;
    @Autowired private AccountProfileRepository profiles;
    @Autowired private SupportThreadRepository threads;
    @Autowired private PasswordEncoder passwords;

    @Test
    void farmerAndSellerConversationsStayPrivateWhileAdminCanReply() throws Exception {
        AppUser farmer = users.save(new AppUser("+919876543210", "farmer", passwords.encode(PASSWORD)));
        users.save(new AppUser("+919876543211", "seller", passwords.encode(PASSWORD)));
        users.save(new AppUser("+919876543212", "buyer", passwords.encode(PASSWORD)));
        users.save(new AppUser("+919876543213", "admin", passwords.encode(PASSWORD)));
        AccountProfile farmerProfile = new AccountProfile(farmer.getId());
        farmerProfile.setFullName("शेतकरी मदत चाचणी");
        profiles.save(farmerProfile);

        String farmerToken = auth.login("+919876543210", PASSWORD, "farmer").token();
        String sellerToken = auth.login("+919876543211", PASSWORD, "seller").token();
        String buyerToken = auth.login("+919876543212", PASSWORD, "buyer").token();
        String adminToken = auth.login("+919876543213", PASSWORD, "admin").token();

        mvc.perform(get("/api/support/my")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/support/my").header("Authorization", bearer(buyerToken)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/support/my/messages").header("Authorization", bearer(farmerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"फोटो जोडता येत नाही\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderRole").value("farmer"));
        mvc.perform(post("/api/support/my/messages").header("Authorization", bearer(sellerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"विक्री नोंद अडली\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderRole").value("seller"));

        Long farmerThreadId = threads.findByParticipantUserId(farmer.getId()).orElseThrow().getId();
        mvc.perform(get("/api/support/my").header("Authorization", bearer(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].body").value("विक्री नोंद अडली"));
        mvc.perform(get("/api/support/admin/threads").header("Authorization", bearer(farmerToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/support/admin/threads").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
        mvc.perform(get("/api/support/unread").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.count").value(2));
        mvc.perform(get("/api/support/admin/threads/{id}", farmerThreadId)
                .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].body").value("फोटो जोडता येत नाही"));
        mvc.perform(get("/api/support/unread").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.count").value(1));

        mvc.perform(post("/api/support/admin/threads/{id}/messages", farmerThreadId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"नवीन फोटो निवडून पुन्हा प्रयत्न करा\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderRole").value("admin"));
        mvc.perform(get("/api/support/unread").header("Authorization", bearer(farmerToken)))
                .andExpect(jsonPath("$.count").value(1));
        mvc.perform(get("/api/support/my").header("Authorization", bearer(farmerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[1].body").value("नवीन फोटो निवडून पुन्हा प्रयत्न करा"));
        mvc.perform(get("/api/support/unread").header("Authorization", bearer(farmerToken)))
                .andExpect(jsonPath("$.count").value(0));
        mvc.perform(get("/api/support/unread").header("Authorization", bearer(sellerToken)))
                .andExpect(jsonPath("$.count").value(0));
        mvc.perform(post("/api/support/admin/threads/{id}/messages", farmerThreadId)
                .header("Authorization", bearer(sellerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"मी प्रशासक नाही\"}"))
                .andExpect(status().isForbidden());
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
