package in.swarnabhoomi.farmlog;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:profile_photos;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "app.reminders.scheduler-enabled=false",
        "app.database.backup.enabled=false", "app.storage.s3.bucket="
})
@AutoConfigureMockMvc
class ProfilePhotoControllerTest {
    private static final String PASSWORD = "ProfilePhotoTest123!";
    private static final AtomicInteger NEXT = new AtomicInteger(100);
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired FarmPhotoRepository photos;
    @Autowired AccountProfileRepository profiles;
    record Account(AppUser user, String token) {}

    private Account account(String role) {
        String mobile = "+91987654" + String.format("%04d", NEXT.incrementAndGet());
        AppUser user = users.save(new AppUser(mobile, role, passwords.encode(PASSWORD)));
        return new Account(user, "Bearer " + auth.login(mobile, PASSWORD, role).token());
    }
    private static MockMultipartFile image(int width, int height) throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("file", "portrait.png", "image/png", bytes.toByteArray());
    }

    @ParameterizedTest @ValueSource(strings = {"buyer", "seller", "farmer"})
    void allAccountRolesCanUploadReplaceAndRemovePhotoWithoutCreatingOrClearingProfile(String role) throws Exception {
        Account owner = account(role);
        mvc.perform(multipart("/api/profile/photo").file(image(48, 48)).header("Authorization", owner.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photoUrl", startsWith("/api/profile/photo?updated=")));
        assertThat(profiles.findByUserId(owner.user().getId())).isEmpty();
        mvc.perform(get("/api/profile").header("Authorization", owner.token()))
                .andExpect(jsonPath("$.complete").value(false)).andExpect(jsonPath("$.photoUrl").value("/api/profile/photo"));
        mvc.perform(put("/api/profile").header("Authorization", owner.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Photo Test\",\"village\":\"Pune\",\"district\":\"Pune\",\"state\":\"Maharashtra\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.complete").value(true));
        mvc.perform(multipart("/api/profile/photo").file(image(64, 64)).header("Authorization", owner.token())).andExpect(status().isOk());
        byte[] data = mvc.perform(get("/api/profile/photo").header("Authorization", owner.token()))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", containsString("no-store"))).andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(data)).getWidth()).isEqualTo(64);
        assertThat(photos.findByIdAndOwnerId(ProfilePhotoService.photoId(owner.user().getId()), owner.user().getId())).isPresent();
        mvc.perform(delete("/api/profile/photo").header("Authorization", owner.token())).andExpect(status().isNoContent());
        mvc.perform(get("/api/profile/photo").header("Authorization", owner.token())).andExpect(status().isNotFound());
        mvc.perform(get("/api/profile").header("Authorization", owner.token()))
                .andExpect(jsonPath("$.fullName").value("Photo Test")).andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.photoUrl").doesNotExist());
    }

    @Test void photosArePrivateToOwnerAndAdminAndExcludedFromMarketplace() throws Exception {
        Account owner = account("farmer"), another = account("buyer"), admin = account("admin");
        mvc.perform(multipart("/api/profile/photo").file(image(40, 40))).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/profile/photo").file(image(40, 40)).header("Authorization", owner.token())).andExpect(status().isOk());
        mvc.perform(get("/api/profile/photo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/profile/photo/{id}", owner.user().getId()).header("Authorization", another.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/profile/photo/{id}", owner.user().getId()).header("Authorization", admin.token())).andExpect(status().isOk());
        mvc.perform(get("/api/marketplace/photos/{id}", ProfilePhotoService.photoId(owner.user().getId()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/users").param("role", "farmer").header("Authorization", admin.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id == %s)].photoUrl", owner.user().getId())
                        .value(hasItem("/api/profile/photo/" + owner.user().getId())));
    }

    @Test void invalidOversizedAndExcessiveDimensionImagesAreRejected() throws Exception {
        Account owner = account("buyer");
        for (var file : new MockMultipartFile[] {
                new MockMultipartFile("file", "fake.jpg", "image/jpeg", "not an image".getBytes()),
                new MockMultipartFile("file", "large.jpg", "image/jpeg", new byte[1_048_577]), image(1025, 1) }) {
            mvc.perform(multipart("/api/profile/photo").file(file).header("Authorization", owner.token())).andExpect(status().isBadRequest());
        }
        assertThat(photos.existsByIdAndOwnerId(ProfilePhotoService.photoId(owner.user().getId()), owner.user().getId())).isFalse();
    }
}
