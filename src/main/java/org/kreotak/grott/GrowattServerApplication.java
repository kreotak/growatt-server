package org.kreotak.grott;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class GrowattServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(GrowattServerApplication.class, args);
    }
}
