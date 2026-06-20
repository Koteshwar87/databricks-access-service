package com.example.databricksstarter.autoconfigure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.databricks")
public class DatabricksProperties {

    private static final String USER_AGENT = "databricks-access-starter";

    /**
     * Master switch for the starter. When false (default), no beans are registered
     * and the starter is completely dormant — safe to have the dependency on the
     * classpath without activating any Databricks behavior.
     */
    private boolean enabled = false;

    @NotBlank
    private String hostname;

    @NotBlank
    private String httpPath;

    @NotBlank
    private String catalog;

    @NotBlank
    private String schema;

    @Positive
    private int queryTimeoutSeconds = 60;

    @NotBlank
    private String clientId;

    @NotBlank
    private String clientSecret;

    private final HikariSettings hikari = new HikariSettings();

    public String getJdbcUrl() {
        return String.format(
                "jdbc:databricks://%s:443;httpPath=%s;ConnCatalog=%s;ConnSchema=%s;"
                    + "EnableArrow=1;UseNativeQuery=1;UserAgentEntry=%s;"
                    + "AuthMech=11;Auth_Flow=1;OAuth2ClientId=%s;OAuth2Secret=%s;",
                hostname, httpPath, catalog, schema, USER_AGENT, clientId, clientSecret);
    }

    @Data
    public static class HikariSettings {
        private int maximumPoolSize = 5;
        private int minimumIdle = 1;
        private long connectionTimeout = 30_000;
        private long idleTimeout = 600_000;
        private long maxLifetime = 1_800_000;
    }
}
