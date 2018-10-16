package com.netflix.conductor.dao.mysql;

import java.util.concurrent.atomic.AtomicBoolean;

import ch.vorburger.mariadb4j.DBConfiguration;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.vorburger.exec.ManagedProcessException;
import ch.vorburger.mariadb4j.DB;

public enum EmbeddedDatabase {
	INSTANCE;

	private final DB db;
	private final Logger logger = LoggerFactory.getLogger(EmbeddedDatabase.class);
	private static final AtomicBoolean hasBeenMigrated = new AtomicBoolean(false);

	public DB getDB() {
		return db;
	}

	private DB startEmbeddedDatabase() {
		try {
			DBConfiguration dbConfiguration = DBConfigurationBuilder.newBuilder()
					.setPort(33307)
					.addArg("--user=root")
					.build();
			DB db = DB.newEmbeddedDB(dbConfiguration);
			db.start();
			db.createDB("conductor");
			return db;
		} catch (ManagedProcessException e) {
			throw new RuntimeException(e);
		}
	}

	EmbeddedDatabase() {
		logger.info("Starting embedded database");
		db = startEmbeddedDatabase();
	}

	public static boolean hasBeenMigrated() {
		return hasBeenMigrated.get();
	}

	public static void setHasBeenMigrated() {
		hasBeenMigrated.getAndSet(true);
	}

}
