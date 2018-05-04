package com.netflix.conductor.dao.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.vorburger.exec.ManagedProcessException;
import ch.vorburger.mariadb4j.DB;

public enum EmbeddedDatabase {
	INSTANCE;

	private final DB db;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public DB getDB() {
		return db;
	}

	private DB startEmbeddedDatabase() {
		try {
			DB db = DB.newEmbeddedDB(33307);
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
}
