package in.swarnabhoomi.farmlog;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class FarmEntryController {
    private final FarmEntryRepository repo;
    @Value("${app.upload-dir:./uploads}") private String uploadDirectory;
    public FarmEntryController(FarmEntryRepository repo) { this.repo = repo; }

    @GetMapping("/entries") public List<FarmEntry> entries() { return repo.findAllByOrderByEntryDateDescIdDesc(); }
    @PostMapping("/entries") public FarmEntry create(@RequestBody FarmEntry entry) {
        if (entry.getEntryDate() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "दिनांक आवश्यक आहे");
        return repo.save(entry);
    }
    @PutMapping("/entries/{id}") public FarmEntry update(@PathVariable Long id, @RequestBody FarmEntry entry) {
        if (!repo.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        entry.setId(id); return repo.save(entry);
    }
    @DeleteMapping("/entries/{id}") public void delete(@PathVariable Long id) { repo.deleteById(id); }
    @PostMapping("/photos") public Map<String,String> photo(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty() || file.getSize() > 10_000_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "फोटो १० MB पेक्षा लहान असावा");
        String type = file.getContentType() == null ? "" : file.getContentType();
        if (!type.startsWith("image/")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "फक्त फोटो फाइल स्वीकारली जाते");
        Path folder = Path.of(uploadDirectory).toAbsolutePath().normalize(); Files.createDirectories(folder);
        String name = UUID.randomUUID() + file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        Files.copy(file.getInputStream(), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        return Map.of("url", "/api/photos/" + name);
    }
    @GetMapping("/photos/{name}") public ResponseEntity<Resource> getPhoto(@PathVariable String name) throws Exception {
        Path folder = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Path photo = folder.resolve(name).normalize();
        if (!photo.startsWith(folder) || !Files.isRegularFile(photo)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Resource resource = new UrlResource(photo.toUri());
        MediaType type = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type).body(resource);
    }
}
