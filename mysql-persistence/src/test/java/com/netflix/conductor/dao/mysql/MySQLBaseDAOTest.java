package com.netflix.conductor.dao.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sql2o.Connection;
import org.sql2o.Sql2o;

import ch.vorburger.mariadb4j.DB;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.config.TestConfiguration;

class MySQLBaseDAOTest {

	protected final Logger logger = LoggerFactory.getLogger(getClass());
	protected final Sql2o testSql2o;
	protected final TestConfiguration testConfiguration = new TestConfiguration();
	protected final ObjectMapper objectMapper = createObjectMapper();
	protected final DB db = EmbeddedDatabase.INSTANCE.getDB();

	MySQLBaseDAOTest() {
		testConfiguration.setProperty("jdbc.url", "jdbc:mysql://localhost:33306/conductor");
		testConfiguration.setProperty("jdbc.username", "root");
		testConfiguration.setProperty("jdbc.password", "");
		testSql2o = new MySQLWorkflowModule().getSql2o(testConfiguration);
	}
	
	private ObjectMapper createObjectMapper() {
		ObjectMapper om = new ObjectMapper();
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		om.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
		om.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
		om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		om.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
		return om;
	}

	protected void resetAllData() {
		logger.info("Resetting data for test");
		try(Connection connection = testSql2o.open()) {
			connection.createQuery("SHOW TABLES").executeScalarList(String.class).stream()
					.filter(name -> !name.equalsIgnoreCase("schema_version"))
					.forEach(table -> connection.createQuery("TRUNCATE TABLE " + table).executeUpdate());
		}
	}
}
