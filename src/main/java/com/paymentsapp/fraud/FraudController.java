package com.paymentsapp.fraud;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Заглушка антифрода (настоящий делает другая команда). Решение случайное.
// Для ручной проверки: копейки .66 → REJECTED, .77 → ответ дольше таймаута payment.
// Копейки .88 и .99 — сценарии процессинга, их всегда пропускаем.
@RestController
@RequestMapping("/api/fraud")
public class FraudController {

    private static final Logger log = LoggerFactory.getLogger(FraudController.class);
    private static final List<String> REJECT_REASONS = List.of(
            "Подозрительная активность по счёту", "Получатель в стоп-листе", "Нетипичная сумма для клиента");

    private static final int KOPECKS = 2;

    private final FraudProperties props;

    public FraudController(FraudProperties props) {
        this.props = props;
    }

    @PostMapping("/check")
    public CheckResponse check(@Valid @RequestBody CheckRequest req) throws InterruptedException {
        int kopecks = req.amount().remainder(BigDecimal.ONE).movePointRight(KOPECKS).intValue();
        double roll = ThreadLocalRandom.current().nextDouble();

        if (props.passKopecks().contains(kopecks)) {
            log.info("payment {}: APPROVED (тестовая сумма процессинга)", req.paymentId());
            return new CheckResponse(req.paymentId(), Decision.APPROVED, null);
        }
        if (kopecks == props.slowKopecks() || (kopecks != props.rejectedKopecks() && roll < props.timeoutRate())) {
            log.info("payment {}: зависаем на {}", req.paymentId(), props.slowDelay());
            Thread.sleep(props.slowDelay());
        } else if (kopecks == props.rejectedKopecks() || roll < props.timeoutRate() + props.rejectRate()) {
            String reason = REJECT_REASONS.get(ThreadLocalRandom.current().nextInt(REJECT_REASONS.size()));
            log.info("payment {}: REJECTED ({})", req.paymentId(), reason);
            return new CheckResponse(req.paymentId(), Decision.REJECTED, reason);
        }
        log.info("payment {}: APPROVED", req.paymentId());
        return new CheckResponse(req.paymentId(), Decision.APPROVED, null);
    }

    public enum Decision { APPROVED, REJECTED }

    public record CheckRequest(@NotNull UUID paymentId, @NotNull UUID userId, @NotNull String type,
                               @NotNull BigDecimal amount) {
    }

    public record CheckResponse(UUID paymentId, Decision decision, String reason) {
    }
}
