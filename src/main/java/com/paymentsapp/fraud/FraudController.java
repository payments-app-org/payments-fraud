package com.paymentsapp.fraud;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private final double rejectRate;
    private final double timeoutRate;
    private final Duration slowDelay;

    public FraudController(@Value("${fraud.reject-rate}") double rejectRate,
                           @Value("${fraud.timeout-rate}") double timeoutRate,
                           @Value("${fraud.slow-delay}") Duration slowDelay) {
        this.rejectRate = rejectRate;
        this.timeoutRate = timeoutRate;
        this.slowDelay = slowDelay;
    }

    @PostMapping("/check")
    public CheckResponse check(@Valid @RequestBody CheckRequest req) throws InterruptedException {
        int kopecks = req.amount().remainder(BigDecimal.ONE).movePointRight(2).intValue();
        double roll = ThreadLocalRandom.current().nextDouble();

        if (kopecks == 88 || kopecks == 99) {
            log.info("payment {}: APPROVED (тестовая сумма процессинга)", req.paymentId());
            return new CheckResponse(req.paymentId(), Decision.APPROVED, null);
        }
        if (kopecks == 77 || (kopecks != 66 && roll < timeoutRate)) {
            log.info("payment {}: зависаем на {}", req.paymentId(), slowDelay);
            Thread.sleep(slowDelay);
        } else if (kopecks == 66 || roll < timeoutRate + rejectRate) {
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
