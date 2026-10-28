package in.swarnabhoomi.farmlog;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profile/photo")
public class ProfilePhotoController {
    private final ProfilePhotoService photos;
    private final PhotoObjectStorage storage;
    public ProfilePhotoController(ProfilePhotoService photos, PhotoObjectStorage storage) { this.photos = photos; this.storage = storage; }

    @PostMapping
    public PhotoResponse upload(@RequestParam("file") MultipartFile file,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        photos.save(user, file);
        return new PhotoResponse("/api/profile/photo?updated=" + System.currentTimeMillis());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) { photos.remove(user); }

    @GetMapping
    public ResponseEntity<byte[]> ownPhoto(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) { return response(user.getId()); }

    @GetMapping("/{userId}")
    public ResponseEntity<byte[]> accountPhoto(@PathVariable Long userId,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser viewer) {
        if (!viewer.getId().equals(userId) && !"admin".equals(viewer.getRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return response(userId);
    }

    private ResponseEntity<byte[]> response(Long userId) {
        FarmPhoto photo = photos.find(userId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.noStore().cachePrivate()).header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + photo.getOriginalFilename() + "\"")
                .body(storage.load(photo));
    }
    public record PhotoResponse(String photoUrl) {}
}
