package in.swarnabhoomi.farmlog;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.Arrays;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class FarmPhotoRepositoryTest {
    @Autowired
    private FarmPhotoRepository photos;

    @Autowired
    private EntityManager entityManager;

    @Test
    void storesFullSizeImageInNewDatabase() {
        byte[] data = new byte[2_000_000];
        Arrays.fill(data, (byte) 42);
        FarmPhoto photo = new FarmPhoto();
        photo.setId(UUID.randomUUID().toString());
        photo.setContentType("image/jpeg");
        photo.setOriginalFilename("farm.jpg");
        photo.setData(data);

        photos.saveAndFlush(photo);
        entityManager.clear();

        assertArrayEquals(data, photos.findById(photo.getId()).orElseThrow().getData());
    }
}
