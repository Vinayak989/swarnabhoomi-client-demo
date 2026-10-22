package in.swarnabhoomi.farmlog;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "app.access.enabled=true",
    "app.access.username=consultant-test",
    "app.access.password=consultant-password",
    "spring.datasource.url=jdbc:h2:mem:consultants-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class OrganicConsultantControllerTest {
    private static final String VALID_CONSULTANT = """
            {
              "name":"सेंद्रिय शेती मार्गदर्शक",
              "phone":"+919876543210",
              "serviceArea":"सांगली जिल्हा",
              "expertise":"सेंद्रिय खते, माती परीक्षण",
              "experienceYears":8,
              "introduction":"शेतकऱ्यांना फोनवर मार्गदर्शन."
            }
            """;

    @Autowired private MockMvc mvc;
    @Autowired private OrganicConsultantRepository repository;

    @BeforeEach
    void clearConsultants() {
        repository.deleteAll();
    }

    @Test
    void consultantCanRegisterAndBeFoundWithoutAppLogin() throws Exception {
        mvc.perform(post("/api/consultants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CONSULTANT))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("सेंद्रिय शेती मार्गदर्शक"))
            .andExpect(jsonPath("$.phone").value("+919876543210"))
            .andExpect(jsonPath("$.expertise").value("सेंद्रिय खते, माती परीक्षण"));

        mvc.perform(get("/api/consultants"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].serviceArea").value("सांगली जिल्हा"));
    }

    @Test
    void invalidContactAndExperienceAreRejected() throws Exception {
        String invalid = VALID_CONSULTANT
                .replace("+919876543210", "1234")
                .replace("\"experienceYears\":8", "\"experienceYears\":-1");
        mvc.perform(post("/api/consultants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalid))
            .andExpect(status().isBadRequest());
    }
}
