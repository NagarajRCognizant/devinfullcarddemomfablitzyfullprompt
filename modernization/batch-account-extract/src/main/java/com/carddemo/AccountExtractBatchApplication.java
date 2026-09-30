package com.carddemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The READACCT job as a standalone batch application: it is submitted like the JCL job was, runs the
 * single step and exits, rather than living inside the online service.
 */
@SpringBootApplication
public class AccountExtractBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountExtractBatchApplication.class, args);
    }
}
