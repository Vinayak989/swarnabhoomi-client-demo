package in.swarnabhoomi.farmlog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MandiPriceController {
    private final MandiPriceService prices;

    public MandiPriceController(MandiPriceService prices) {
        this.prices = prices;
    }

    @GetMapping("/api/market-prices")
    public MandiPriceService.Feed marketPrices(
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return refresh ? prices.refreshIfAllowed() : prices.currentFeed();
    }
}
