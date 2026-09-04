package com.bebefish.erp.common.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class RuntimeProfileValidator {
    public RuntimeProfileValidator(Environment environment) {
        validate(new HashSet<>(Arrays.asList(environment.getActiveProfiles())));
    }

    static void validate(Set<String> activeProfiles) {
        if (activeProfiles.contains("prod")
                && (activeProfiles.contains("local") || activeProfiles.contains("test"))) {
            throw new IllegalStateException("prod Profile 不得与 local 或 test Profile 同时启用");
        }
    }
}
