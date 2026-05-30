package com.example.databricksaccess.config;

import jakarta.validation.constraints.AssertTrue;
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

    private String token;

    private String clientId;

    private String clientSecret;

    private String schema = "demo";

    @AssertTrue(message = "Must configure either app.databricks.token (PAT) or both app.databricks.client-id and app.databricks.client-secret (OAuth M2M) — not both, not neither")
    public boolean isAuthConfigured() {
        boolean pat = token != null && !token.isBlank();
        boolean oauth = clientId != null && !clientId.isBlank()
                     && clientSecret != null && !clientSecret.isBlank();
        return pat ^ oauth;
    }

    public boolean isOAuthMode() {
        return clientId != null && !clientId.isBlank();
    }

    public String getJdbcUrl() {
        if (isOAuthMode()) {
            return String.format(
                    "jdbc:databricks://%s:443/default;httpPath=%s;AuthMech=11;Auth_Flow=1;OAuth2ClientId=%s;OAuth2Secret=%s;",
                    hostname, httpPath, clientId, clientSecret);
        }
        return String.format(
                "jdbc:databricks://%s:443/default;httpPath=%s;AuthMech=3;UID=token;PWD=%s;",
                hostname, httpPath, token);
    }
}
