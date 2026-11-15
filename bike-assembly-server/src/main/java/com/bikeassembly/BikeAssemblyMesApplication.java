package com.bikeassembly;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.bikeassembly.mapper")
public class BikeAssemblyMesApplication {

    public static void main(String[] args) {
        SpringApplication.run(BikeAssemblyMesApplication.class, args);
    }
}
