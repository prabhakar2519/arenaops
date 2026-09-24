package com.arena.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ArenaCoreApplication {

  public static void main(String[] args) {
    SpringApplication.run(ArenaCoreApplication.class, args);
  }
}
