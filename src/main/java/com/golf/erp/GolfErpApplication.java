package com.golf.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GolfErpApplication {
	public static void main(String[] args) {
		SpringApplication.run(GolfErpApplication.class, args);
	}
}
