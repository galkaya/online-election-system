package edu.comp5100.election;

import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Online Election System, a simulated election with fictional users.
 */
@SpringBootApplication
public class ElectionApplication {

    public static void main(String[] args) {
        // NFR-05: framework-generated messages stay English on any operating system language.
        Locale.setDefault(Locale.US);
        SpringApplication.run(ElectionApplication.class, args);
    }
}
