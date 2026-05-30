package com.example.databricksaccess.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSourceConfig {

    private final DatabricksProperties databricksProperties;

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties props) {
        String jdbcUrl = databricksProperties.getJdbcUrl();
        String maskedUrl = jdbcUrl
                .replaceAll("PWD=[^;]*;", "PWD=***;")
                .replaceAll("OAuth2Secret=[^;]*;", "OAuth2Secret=***;");
        log.info("Databricks auth mode: {}", databricksProperties.isOAuthMode() ? "OAuth M2M" : "PAT");
        log.info("Configuring Databricks DataSource: {}", maskedUrl);

        return props.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .url(jdbcUrl)
                .build();
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
