package com.example.holdings.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.postgres")
public class PostgresProperties {

    @NotBlank
    private String url;

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
