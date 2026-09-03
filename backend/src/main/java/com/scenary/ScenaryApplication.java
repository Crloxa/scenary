package com.scenary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScenaryApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScenaryApplication.class, args);
	}

}
