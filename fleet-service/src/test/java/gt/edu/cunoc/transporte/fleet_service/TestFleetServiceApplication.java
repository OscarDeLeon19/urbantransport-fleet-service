package gt.edu.cunoc.transporte.fleet_service;

import org.springframework.boot.SpringApplication;

public class TestFleetServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(FleetServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
