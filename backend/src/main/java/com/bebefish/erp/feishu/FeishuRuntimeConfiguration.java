package com.bebefish.erp.feishu;

import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class FeishuRuntimeConfiguration {
    @Bean
    public FeishuAvailability feishuAvailability(
            FeishuProperties properties,
            FeishuDirectoryClient directory,
            FeishuConfigurationValidator validator,
            Environment environment
    ) {
        return validator.validate(properties, Set.of(environment.getActiveProfiles()), directory);
    }
}
