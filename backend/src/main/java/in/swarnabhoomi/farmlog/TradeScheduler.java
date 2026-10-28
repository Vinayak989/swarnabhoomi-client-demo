package in.swarnabhoomi.farmlog;

import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TradeScheduler {
  private final TradeService trade;

  @Value("${app.trade.scheduler-enabled:true}")
  boolean enabled;

  public TradeScheduler(TradeService trade) {
    this.trade = trade;
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 45000)
  public void run() {
    if (!enabled || ZonedDateTime.now(TradeService.INDIA).getHour() < 7) return;
    for (Long id : trade.dueSubscriptions())
      try {
        trade.runSubscription(id);
      } catch (Exception e) {
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("Recurring purchase {} could not run; it will retry.", id);
      }
  }
}
