package in.swarnabhoomi.farmlog;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfilePhotoService {
    private static final Set<String> ROLES = Set.of("buyer", "seller", "farmer");
    private final FarmPhotoRepository photos;
    private final PhotoObjectStorage storage;

    public ProfilePhotoService(FarmPhotoRepository photos, PhotoObjectStorage storage) { this.photos = photos; this.storage = storage; }

    static String photoId(Long userId) {
        return UUID.nameUUIDFromBytes(("swarnabhoomi:profile-photo:" + userId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Transactional(readOnly = true)
    public boolean exists(Long userId) { return photos.existsByIdAndOwnerId(photoId(userId), userId); }

    @Transactional(readOnly = true)
    public Set<String> existingIds(Collection<Long> userIds) {
        return userIds.isEmpty() ? Set.of() : Set.copyOf(photos.findExistingIds(userIds.stream().map(ProfilePhotoService::photoId).toList()));
    }

    @Transactional
    public void save(AppUser user, MultipartFile file) {
        requireRole(user);
        if (file == null || file.isEmpty() || file.getSize() > 1_048_576)
            throw badPhoto("प्रोफाइल फोटो १ MB पेक्षा लहान असावा.");
        String format;
        byte[] data;
        // Bound decoded dimensions and strip metadata before storing the image.
        try (var raw = file.getInputStream(); var input = ImageIO.createImageInputStream(raw)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw badPhoto("योग्य JPG किंवा PNG फोटो निवडा.");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("jpeg", "jpg", "png").contains(format) || reader.getWidth(0) < 1 || reader.getHeight(0) < 1
                        || reader.getWidth(0) > 1024 || reader.getHeight(0) > 1024)
                    throw badPhoto("JPG किंवा PNG फोटो निवडा; आकार १०२४ × १०२४ पेक्षा कमी असावा.");
                var image = reader.read(0);
                var output = new ByteArrayOutputStream();
                if (image == null || !ImageIO.write(image, format.equals("png") ? "png" : "jpeg", output))
                    throw badPhoto("फोटो वाचता आला नाही.");
                data = output.toByteArray();
                if (data.length > 1_048_576) throw badPhoto("प्रोफाइल फोटो १ MB पेक्षा लहान असावा.");
            } finally { reader.dispose(); }
        } catch (ResponseStatusException exception) { throw exception; }
        catch (Exception exception) { throw badPhoto("फोटो वाचता आला नाही. दुसरा फोटो निवडा."); }
        FarmPhoto photo = new FarmPhoto(); photo.setId(photoId(user.getId())); photo.setOwner(user);
        photo.setMarketplacePublic(false); photo.setData(data);
        photo.setContentType(format.equals("png") ? "image/png" : "image/jpeg");
        photo.setOriginalFilename(format.equals("png") ? "profile.png" : "profile.jpg");
        storage.store(photo); photos.save(photo);
    }

    @Transactional(readOnly = true)
    public FarmPhoto find(Long userId) {
        return photos.findByIdAndOwnerId(photoId(userId), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "प्रोफाइल फोटो नाही."));
    }

    @Transactional
    public void remove(AppUser user) {
        requireRole(user);
        photos.findByIdAndOwnerId(photoId(user.getId()), user.getId()).ifPresent(photo -> {
            storage.delete(photo); photos.delete(photo);
        });
    }

    private static void requireRole(AppUser user) {
        if (user == null || !ROLES.contains(user.getRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    private static ResponseStatusException badPhoto(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
