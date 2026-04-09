package com.example.databricksaccess.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public DataSource dataSource() {
        String jdbcUrl = databricksProperties.getJdbcUrl();
        String maskedUrl = jdbcUrl.replaceAll("PWD=.*?;", "PWD=***;");
        log.info("Configuring Databricks DataSource: {}", maskedUrl);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setDriverClassName("com.databricks.client.jdbc.Driver");
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
