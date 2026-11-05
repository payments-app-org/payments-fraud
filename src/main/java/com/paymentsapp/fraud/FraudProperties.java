package com.paymentsapp.fraud;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Настройки заглушки антифрода (fraud.* в application.yml)
@ConfigurationProperties("fraud")
public record FraudProperties(
        // Доли исходов; остальное — APPROVED
        double rejectRate,
        double timeoutRate,
        // «Зависание» — дольше тайм-аута payment
        Duration slowDelay,
        // Суммы для ручной проверки: копейки → гарантированный отказ / зависание / всегда пропустить (сценарии процессинга)
        int rejectedKopecks,
        int slowKopecks,
        List<Integer> passKopecks
) {
}
