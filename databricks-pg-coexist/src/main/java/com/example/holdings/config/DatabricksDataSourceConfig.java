package com.example.holdings.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DatabricksDataSourceConfig {

    private final DatabricksProperties props;

    @Bean
    @ConfigurationProperties("app.databricks.hikari")
    public HikariDataSource databricksDataSource() {
        String jdbcUrl = props.getJdbcUrl();
        String masked = jdbcUrl.replaceAll("PWD=[^;]*;", "PWD=***;");
        log.info("Databricks DataSource configured: {}", masked);

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        return ds;
    }

    @Bean
    public JdbcTemplate databricksJdbcTemplate(@Qualifier("databricksDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
