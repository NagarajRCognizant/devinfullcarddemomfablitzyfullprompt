package com.carddemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Job CBPAUP0J as a standalone batch application: it is submitted like the JCL job was, runs its
 * single step and exits. It is a BMP against the authorization database in the source and a client
 * of the authorization schema here, so it reads the entities of the shared authorization-domain
 * module and leaves the schema itself to the owning service's migrations.
 */
@SpringBootApplication
@EntityScan("com.carddemo.persistence.entity")
@EnableJpaRepositories("com.carddemo.persistence.repository")
public class AuthPurgeBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthPurgeBatchApplication.class, args);
    }
}
