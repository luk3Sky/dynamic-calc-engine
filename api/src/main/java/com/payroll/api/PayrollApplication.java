package com.payroll.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the payroll calculation API. Scans {@code com.payroll} so the
 * rules-engine Spring beans (orchestrator, engines, config loader) are picked up.
 */
@SpringBootApplication(scanBasePackages = "com.payroll")
public class PayrollApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayrollApplication.class, args);
    }
}