package com.alibaba.himarket.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Auto-repair checksum errors. Set app.flyway.auto-repair=false to disable.
 *
 * @author zh
 */
@Slf4j
@Configuration
public class FlywayConfig {

    @Value("${app.flyway.auto-repair:true}")
    private boolean autoRepair;

    @Bean
    @Primary
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .outOfOrder(false)
                .baselineVersion("1")
                .baselineDescription("Initial baseline")
                .load();

        log.info("Executing flyway migrate...");
        try {
            flyway.migrate();
        } catch (FlywayException e) {
            if (autoRepair && e.getMessage().contains("checksum")) {
                log.warn("Checksum mismatch detected, attempting repair and retry...");
                flyway.repair();
                flyway.migrate();
            } else {
                throw e;
            }
        }
        log.info("Flyway migrate completed");

        return flyway;
    }
}
