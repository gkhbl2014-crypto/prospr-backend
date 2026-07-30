package com.prospr.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseConnectionVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnectionVerifier.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseConnectionVerifier(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            log.info("Database connectivity check passed (connected to Supabase PostgreSQL, SELECT 1 = {}).", result);
        } catch (DataAccessException ex) {
            log.error("Failed to connect to the Supabase PostgreSQL database on startup.", ex);
            throw ex;
        }
    }
}
