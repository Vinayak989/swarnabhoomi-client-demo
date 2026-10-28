package in.swarnabhoomi.farmlog;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/materials")
public class MaterialController {
  final MaterialService service;

  public MaterialController(MaterialService service) {
    this.service = service;
  }

  @GetMapping
  Object list(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return service.list(u);
  }

  @PostMapping
  Object create(
      @RequestBody MaterialService.MaterialInput input,
      @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return Map.of("id", service.create(input, u));
  }

  @PostMapping("/{id}/adjust")
  void adjust(
      @PathVariable Long id,
      @RequestBody MaterialService.Adjustment input,
      @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    service.adjust(id, input, u);
  }

  @GetMapping("/{id}/history")
  Object history(
      @PathVariable Long id, @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    return service.history(id, u);
  }
}
