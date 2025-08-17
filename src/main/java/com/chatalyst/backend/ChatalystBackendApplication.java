package com.chatalyst.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChatalystBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatalystBackendApplication.class, args);
	}

}
