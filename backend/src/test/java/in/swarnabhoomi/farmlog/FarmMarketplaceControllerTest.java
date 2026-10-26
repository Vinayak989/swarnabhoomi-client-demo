package in.swarnabhoomi.farmlog;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "app.access.enabled=true",
    "app.access.username=market-test",
    "app.access.password=market-password",
    "spring.datasource.url=jdbc:h2:mem:marketplace-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class FarmMarketplaceControllerTest {
    private static final String VALID_LISTING = """
            {
              "sellerName":"शेतकरी",
              "phone":"+919876543210",
              "address":"इस्लामपूर, सांगली",
              "latitude":16.8524,
              "longitude":74.5815,
              "productName":"हळद",
              "cropType":"सेंद्रिय हळद",
              "category":"मसाले",
              "quantity":50,
              "unit":"kg",
              "description":"ताजी काढणी"
            }
            """;

    @Autowired private MockMvc mvc;
    @Autowired private FarmProductListingRepository repository;

    @BeforeEach
    void clearListings() {
        repository.deleteAll();
    }

    @Test
    void publicListingsHideSellerContactAndLocationAndContactEndpointRejectsNonAdmin() throws Exception {
        mvc.perform(post("/api/marketplace/listings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_LISTING))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.productName").value("हळद"))
            .andExpect(jsonPath("$.address").value("इस्लामपूर, सांगली"))
            .andExpect(jsonPath("$.phone").doesNotExist());

        long id = repository.findTop200ByOrderByCreatedAtDesc().get(0).getId();
        mvc.perform(get("/api/marketplace/listings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].phone").doesNotExist())
            .andExpect(jsonPath("$[0].address").doesNotExist())
            .andExpect(jsonPath("$[0].latitude").doesNotExist())
            .andExpect(jsonPath("$[0].longitude").doesNotExist());

        mvc.perform(get("/api/marketplace/listings/{id}/contact", id))
            .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/marketplace/listings/{id}/contact", id)
                .header("Authorization", authorization()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listingCreationDoesNotRequireTheSharedApplicationCredentialsEvenWhenAppLoginIsEnabled() throws Exception {
        mvc.perform(post("/api/marketplace/listings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_LISTING))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.productName").value("हळद"));
    }

    @Test
    void sellerCanUploadAndRetrieveAProductPhotoWithoutSigningIn() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "file", "crop.jpg", "image/jpeg", new byte[]{1, 2, 3});
        String photoPath = mvc.perform(multipart("/api/marketplace/photos").file(image))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String photoUrl = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(photoPath).get("url").asText();

        mvc.perform(get(photoUrl))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                    .contentType(MediaType.IMAGE_JPEG))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                    .bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void invalidPhoneAndCoordinatesAreRejected() throws Exception {
        String invalid = VALID_LISTING.replace("+919876543210", "1234").replace("16.8524", "100");
        mvc.perform(post("/api/marketplace/listings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalid))
            .andExpect(status().isBadRequest());
    }

    private String authorization() {
        String value = Base64.getEncoder().encodeToString(
                "market-test:market-password".getBytes(StandardCharsets.UTF_8));
        return "Basic " + value;
    }
}
