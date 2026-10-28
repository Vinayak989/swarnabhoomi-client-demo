package in.swarnabhoomi.farmlog;

import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/trade")
public class TradeController {
  private final TradeService service;
  private final FarmPhotoRepository photos;
  private final PhotoObjectStorage storage;

  public TradeController(
      TradeService service, FarmPhotoRepository photos, PhotoObjectStorage storage) {
    this.service = service;
    this.photos = photos;
    this.storage = storage;
  }

  @GetMapping("/seller-settings")
  Object settings(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return service.sellerSettings(u);
  }

  @GetMapping("/listings/{id}/settings")
  Object settings(
      @PathVariable Long id, @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return service.settings(id, u);
  }

  @PutMapping("/listings/{id}/offer")
  Object offer(
      @PathVariable Long id,
      @RequestBody TradeService.Offer input,
      @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return service.offer(id, input, u);
  }

  @PutMapping("/listings/{id}/batch")
  Object batch(
      @PathVariable Long id,
      @RequestBody TradeService.Batch input,
      @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return service.batch(id, input, u);
  }

  public record CodeRequest(boolean reset) {}

  @PostMapping("/orders/{id}/delivery-code")
  Object code(
      @PathVariable Long id,
      @RequestBody CodeRequest input,
      @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return service.deliveryCode(id, input.reset(), u);
  }

  public record Confirm(String code) {}

  @PostMapping("/orders/{id}/confirm-delivery")
  Object confirm(
      @PathVariable Long id,
      @RequestBody Confirm input,
      @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return service.confirmDelivery(id, input.code(), u);
  }

  @GetMapping("/subscriptions")
  Object subscriptions(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return service.subscriptions(u);
  }

  @PostMapping("/subscriptions")
  Object subscribe(
      @RequestBody TradeService.Subscription input,
      @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return Map.of("id", service.subscribe(input, u));
  }

  @GetMapping("/subscriptions/{id}/history")
  Object history(
      @PathVariable Long id, @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return service.occurrences(id, u);
  }

  public record Action(String action) {}

  @PostMapping("/subscriptions/{id}/action")
  void action(
      @PathVariable Long id,
      @RequestBody Action input,
      @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    service.subscriptionAction(id, input.action(), u);
  }

  @GetMapping("/bulk")
  Object bulks(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return service.bulks(u);
  }

  @PostMapping("/bulk")
  Object bulk(
      @RequestBody TradeService.Bulk input,
      @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return Map.of("id", service.bulk(input, u));
  }

  @DeleteMapping("/bulk/{id}")
  void close(@PathVariable Long id, @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    service.closeBulk(id, u);
  }

  @GetMapping("/bulk/{id}/quotes")
  Object quotes(@PathVariable Long id, @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return service.quotes(id, u);
  }

  @PostMapping("/bulk/{id}/quotes")
  Object quote(
      @PathVariable Long id,
      @RequestBody TradeService.Quote input,
      @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return Map.of("id", service.quote(id, input, u));
  }

  public record Accept(String buyerName, String deliveryAddress) {}

  @PostMapping("/bulk/{id}/quotes/{quoteId}/accept")
  Object accept(
      @PathVariable Long id,
      @PathVariable Long quoteId,
      @RequestBody Accept input,
      @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return service.acceptQuote(id, quoteId, input.buyerName(), input.deliveryAddress(), u);
  }

  @GetMapping("/returns")
  Object returns(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return service.returns(u);
  }

  @PostMapping("/orders/{id}/returns")
  Object requestReturn(
      @PathVariable Long id,
      @RequestBody TradeService.ReturnInput input,
      @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return Map.of("id", service.requestReturn(id, input, u));
  }

  @PatchMapping("/returns/{id}")
  void resolve(
      @PathVariable Long id,
      @RequestBody TradeService.Resolution input,
      @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    service.resolveReturn(id, input, u);
  }

  @PostMapping("/evidence")
  Object evidence(
      @RequestParam("file") MultipartFile file,
      @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    TradeService.role(u, "buyer");
    if (file.isEmpty() || file.getSize() > 2_000_000)
      throw TradeService.bad("२ MB पेक्षा लहान JPG/PNG फोटो निवडा.");
    try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw TradeService.bad("योग्य फोटो निवडा.");
      var reader = readers.next();
      try {
        reader.setInput(input, true, true);
        if (!Set.of("jpeg", "jpg", "png").contains(reader.getFormatName().toLowerCase(Locale.ROOT))
            || reader.getWidth(0) > 1600
            || reader.getHeight(0) > 1600)
          throw TradeService.bad("१६०० × १६०० पेक्षा लहान JPG/PNG निवडा.");
        var image = reader.read(0);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        if (output.size() > 4_000_000) throw TradeService.bad("फोटोचा आकार कमी करा.");
        var photo = new FarmPhoto();
        photo.setId(UUID.randomUUID().toString());
        photo.setOwner(u);
        photo.setData(output.toByteArray());
        photo.setContentType("image/png");
        photo.setOriginalFilename("return-" + photo.getId() + ".png");
        photo.setMarketplacePublic(false);
        storage.store(photo);
        photos.save(photo);
        return Map.of("id", photo.getId());
      } finally {
        reader.dispose();
      }
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw TradeService.bad("फोटो वाचता आला नाही.");
    }
  }

  @GetMapping("/evidence/{id}")
  Object evidencePhoto(
      @PathVariable String id, @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    var p =
        photos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!p.getOriginalFilename().startsWith("return-")
        || p.getOwner() == null
        || (!"admin".equals(u.getRole()) && !p.getOwner().getId().equals(u.getId())))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(storage.load(p));
  }
}
