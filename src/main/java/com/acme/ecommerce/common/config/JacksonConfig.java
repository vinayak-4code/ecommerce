package com.acme.ecommerce.common.config;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Additional Jackson customisation applied on top of Spring Boot auto-config.
 *
 * <p>Core settings (write-dates-as-timestamps, default-property-inclusion,
 * etc.) are declared in application.yml under {@code spring.jackson.*} and
 * are automatically applied by Spring Boot's JacksonAutoConfiguration.</p>
 */
@Configuration
public class JacksonConfig {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder.findModulesViaServiceLoader(true);
    }
}
