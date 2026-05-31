package com.example.holdings.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.databricks")
public class DatabricksProperties {

    private static final String USER_AGENT = "databricks-pg-coexist";

    @NotBlank
    private String hostname;

    @NotBlank
    private String httpPath;

    @NotBlank
    private String catalog;

    @NotBlank
    private String schema;

    @NotBlank
    private String token;

    public String getJdbcUrl() {
        return String.format(
                "jdbc:databricks://%s:443;httpPath=%s;ConnCatalog=%s;ConnSchema=%s;"
                        + "EnableArrow=1;UseNativeQuery=1;UserAgentEntry=%s;"
                        + "AuthMech=3;UID=token;PWD=%s;",
                hostname, httpPath, catalog, schema, USER_AGENT, token);
    }
}
