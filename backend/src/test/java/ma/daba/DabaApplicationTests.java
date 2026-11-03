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

	@org.springframework.beans.factory.annotation.Autowired
	org.springframework.context.ApplicationContext contexte;

	/** CNDP : des téléphones passent dans les query strings des recherches ; aucune URL n'est journalisée. */
	@Test
	void cndp_aucuneUrlNiQueryStringJournalisee() {
		var env = contexte.getEnvironment();
		assertThat(env.getProperty("server.tomcat.accesslog.enabled", Boolean.class, false)).isFalse();
		assertThat(env.getProperty("spring.mvc.log-request-details", Boolean.class, false)).isFalse();
		assertThat(contexte.getBeansOfType(org.springframework.web.filter.AbstractRequestLoggingFilter.class)).isEmpty();
		for (var journal : new String[] { "org.springframework.web", "org.apache.catalina", "org.apache.coyote" }) {
			assertThat(org.slf4j.LoggerFactory.getLogger(journal).isDebugEnabled()).as(journal).isFalse();
		}
	}

	@Test
	void openApiIsServed() {
		assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk();
	}

}
