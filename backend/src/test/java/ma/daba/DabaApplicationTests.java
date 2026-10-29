package ma.daba;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DabaApplicationTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void healthIsUp() {
		assertThat(mvc.get().uri("/actuator/health")).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("UP");
	}

	@Test
	void flywayInstallsPostgis() {
		assertThat(jdbc.queryForObject("SELECT postgis_lib_version()", String.class)).startsWith("3.6");
	}

	@Test
	void openApiIsServed() {
		assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk();
	}

}
