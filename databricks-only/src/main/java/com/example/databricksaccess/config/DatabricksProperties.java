package com.example.databricksaccess.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.databricks")
public class DatabricksProperties {

    private static final String USER_AGENT = "databricks-access-service";

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

    private String token;

    private String clientId;

    private String clientSecret;

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
        String baseAndCommon = String.format(
                "jdbc:databricks://%s:443;httpPath=%s;ConnCatalog=%s;ConnSchema=%s;EnableArrow=1;UseNativeQuery=1;UserAgentEntry=%s;",
                hostname, httpPath, catalog, schema, USER_AGENT);
        if (isOAuthMode()) {
            return baseAndCommon + String.format(
                    "AuthMech=11;Auth_Flow=1;OAuth2ClientId=%s;OAuth2Secret=%s;",
                    clientId, clientSecret);
        }
        return baseAndCommon + String.format(
                "AuthMech=3;UID=token;PWD=%s;",
                token);
    }
}
