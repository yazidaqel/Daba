package ma.daba;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DabaApplicationTests extends TestIntegration {

	@Test
	void healthIsUp() {
		assertThat(mvc.get().uri("/actuator/health")).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("UP");
	}

	@Test
	void flywayInstallsPostgis() {
		assertThat(jdbc.sql("SELECT postgis_lib_version()").query(String.class).single()).startsWith("3.6");
	}

	@Test
	void openApiIsServed() {
		assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk();
	}

}
