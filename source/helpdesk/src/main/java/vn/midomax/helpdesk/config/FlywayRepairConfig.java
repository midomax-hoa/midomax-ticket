package vn.midomax.helpdesk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Escape hatch for Flyway checksum mismatches.
 *
 * <p>Flyway records a checksum of every migration file it applies. Editing an already-applied
 * file changes that checksum, so validation fails on the next startup and the application
 * refuses to boot ("Migration checksum mismatch for migration version N").
 *
 * <p>When {@code app.flyway.repair-before-migrate} is {@code true}, Flyway realigns the recorded
 * checksums in {@code flyway_schema_history} with the files on the classpath, then migrates.
 * Repair only rewrites bookkeeping rows - it never touches application data or schema.
 *
 * <p>DISABLED BY DEFAULT ON PURPOSE. Turn it on for a single deployment to recover from a known
 * mismatch, then turn it off again. Leaving it on would silently accept any future edit to an
 * applied migration, which is exactly the mistake this guard is meant to surface.
 */
@Configuration
public class FlywayRepairConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayRepairConfig.class);

    @Bean
    @ConditionalOnProperty(name = "app.flyway.repair-before-migrate", havingValue = "true")
    public FlywayMigrationStrategy repairBeforeMigrate() {
        return flyway -> {
            log.warn("app.flyway.repair-before-migrate=true -> running Flyway repair before migrate. "
                    + "Turn this off again once the schema history is realigned.");
            flyway.repair();
            log.info("Flyway repair finished, continuing with migrate.");
            flyway.migrate();
        };
    }
}
