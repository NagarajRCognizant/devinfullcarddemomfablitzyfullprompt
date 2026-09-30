package com.carddemo.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FUNCTION CURRENT-DATE is used by the date of birth edit and by the screen header, so the clock
 * is injected to keep those behaviours testable.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
