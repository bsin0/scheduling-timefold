package org.churchband;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Spring Boot side of this app (database + future
 * REST API + future HTML frontend).
 *
 * @SpringBootApplication is shorthand for three annotations combined:
 *   - @Configuration:      this class can define Spring beans
 *   - @EnableAutoConfiguration: Spring Boot auto-configures things like
 *     the database connection and web server based on what's on the
 *     classpath and in application.properties — this is why we haven't
 *     had to manually wire up a DataSource or JPA EntityManager anywhere.
 *   - @ComponentScan:      Spring scans this package and sub-packages
 *     (org.churchband.*) for classes it should manage — repositories,
 *     controllers, services, etc. This is WHY our repository interfaces
 *     in org.churchband.persistence get picked up automatically: they're
 *     inside this class's package tree.
 *
 * IMPORTANT: this is a SEPARATE, NEW entry point from your existing
 * App.java. App.java (with its manual SolverFactory / Solver setup) still
 * works exactly as before if you run it directly — nothing here changes
 * that. Running THIS class instead starts the database + (eventually)
 * a web server, and is what you'll use once the REST API exists.
 *
 * To run this: `mvn spring-boot:run`, or run this class's main() method
 * directly from your IDE.
 */
@SpringBootApplication
public class ChurchBandApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChurchBandApplication.class, args);
    }
}