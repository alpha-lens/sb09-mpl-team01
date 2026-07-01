package com.codeit.mpl;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
@EnableJpaAuditing
public class MplApplication {

  public static void main(String[] args) {
    Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    dotenv.entries().forEach(entry ->
            System.setProperty(entry.getKey(), entry.getValue())
    );

    SpringApplication.run(MplApplication.class, args);
  }
}