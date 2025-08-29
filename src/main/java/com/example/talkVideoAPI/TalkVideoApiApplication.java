package com.example.talkVideoAPI;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class TalkVideoApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TalkVideoApiApplication.class, args);
	}

}
