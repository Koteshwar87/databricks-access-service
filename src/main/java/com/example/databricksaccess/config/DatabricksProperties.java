package com.example.databricksaccess.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.databricks")
public class DatabricksProperties {

    @NotBlank
    private String hostname;

    @NotBlank
    private String httpPath;

    @NotBlank
    private String token;

    private String schema = "demo";

    public String getJdbcUrl() {
        return String.format(
                "jdbc:databricks://%s:443/default;httpPath=%s;AuthMech=3;UID=token;PWD=%s;",
                hostname, httpPath, token
        );
    }
}
