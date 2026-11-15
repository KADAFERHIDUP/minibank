package io.github.kadaferhidup.minibank.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Aplikasi harus menolak nyala dengan pesan yang jelas kalau JWT_SECRET kosong atau terlalu pendek. */
class BankPropertiesValidationTest {

    @Configuration
    @EnableConfigurationProperties(BankProperties.class)
    static class Props {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Props.class);

    @Test
    void emptySecretStopsStartupWithClearMessage() {
        runner.withPropertyValues("minibank.jwt.secret=").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("JWT_SECRET");
        });
    }

    @Test
    void shortSecretIsRejected() {
        runner.withPropertyValues("minibank.jwt.secret=pendek").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("minimal 32 karakter");
        });
    }

    @Test
    void longSecretIsAccepted() {
        runner.withPropertyValues("minibank.jwt.secret=rahasia-yang-panjangnya-lebih-dari-32-karakter")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }
}
