package in.swarnabhoomi.farmlog;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api")
public class FarmEntryController {
    private final FarmEntryRepository repo;
    private final FarmPhotoRepository photoRepo;
    private final PhotoObjectStorage photoStorage;
    @Value("${app.upload-dir:./uploads}") private String uploadDirectory;
    public FarmEntryController(FarmEntryRepository repo, FarmPhotoRepository photoRepo,
            PhotoObjectStorage photoStorage) {
        this.repo = repo;
        this.photoRepo = photoRepo;
        this.photoStorage = photoStorage;
    }

    @GetMapping("/entries") public List<FarmEntry> entries(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireFarmer(user);
        return repo.findAllByOwnerIdOrderByEntryDateDescIdDesc(user.getId());
    }
    @PostMapping("/entries") public FarmEntry create(@RequestBody FarmEntry entry,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireFarmer(user);
        if (entry.getEntryDate() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "दिनांक आवश्यक आहे");
        if (entry.getClientRequestId() != null && !entry.getClientRequestId().isBlank()) {
            FarmEntry existing = repo.findFirstByOwnerIdAndClientRequestId(user.getId(), entry.getClientRequestId()).orElse(null);
            if (existing != null) return existing;
        }
        entry.setOwner(user);
        return repo.save(entry);
    }
    @PutMapping("/entries/{id}") public FarmEntry update(@PathVariable Long id, @RequestBody FarmEntry entry,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireFarmer(user);
        repo.findByIdAndOwnerId(id, user.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        entry.setOwner(user);
        entry.setId(id); return repo.save(entry);
    }
    @DeleteMapping("/entries/{id}") public void delete(@PathVariable Long id,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireFarmer(user);
        FarmEntry entry = repo.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        repo.delete(entry);
    }
    @PostMapping("/photos") public Map<String,String> photo(@RequestParam("file") MultipartFile file,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) throws Exception {
        requireFarmer(user);
        if (file.isEmpty() || file.getSize() > 10_000_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String type = file.getContentType() == null ? "" : file.getContentType();
        if (!type.startsWith("image/")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);

        String originalFilename = Optional.ofNullable(file.getOriginalFilename())
                .filter(name -> !name.isBlank())
                .orElse("farm-photo");
        originalFilename = originalFilename.replace('\\', '/');
        originalFilename = originalFilename.substring(originalFilename.lastIndexOf('/') + 1)
                .replaceAll("[\\r\\n]", "_");
        if (originalFilename.length() > 255) originalFilename = originalFilename.substring(originalFilename.length() - 255);

        FarmPhoto storedPhoto = new FarmPhoto();
        storedPhoto.setId(photoStorage.newPhotoId());
        storedPhoto.setData(file.getBytes());
        storedPhoto.setContentType(type);
        storedPhoto.setOriginalFilename(originalFilename);
        storedPhoto.setOwner(user);
        photoStorage.store(storedPhoto);
        photoRepo.save(storedPhoto);
        return Map.of("url", "/api/photos/" + storedPhoto.getId());
    }

    @GetMapping("/photos/{name}") public ResponseEntity<?> getPhoto(@PathVariable String name,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireFarmer(user);
        FarmPhoto storedPhoto = photoRepo.findByIdAndOwnerId(name, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return storedPhotoResponse(storedPhoto, false);
    }

    @GetMapping("/photos/{name}/download") public ResponseEntity<?> downloadPhoto(@PathVariable String name,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireFarmer(user);
        FarmPhoto storedPhoto = photoRepo.findByIdAndOwnerId(name, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return storedPhotoResponse(storedPhoto, true);
    }

    private ResponseEntity<byte[]> storedPhotoResponse(FarmPhoto photo, boolean download) {
        MediaType type = MediaType.parseMediaType(photo.getContentType());
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(photo.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(photoStorage.load(photo));
    }

    private ResponseEntity<Resource> legacyPhotoResponse(String name, boolean download) throws Exception {
        Path folder = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Path photo = folder.resolve(name).normalize();
        if (!photo.startsWith(folder) || !Files.isRegularFile(photo)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Resource resource = new UrlResource(photo.toUri());
        MediaType type = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(photo.getFileName().toString(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    private static void requireFarmer(AppUser user) {
        if (!"farmer".equals(user.getRole()) && !"seller".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "हे वैशिष्ट्य शेतकरी खात्यासाठी आहे.");
        }
    }

}
