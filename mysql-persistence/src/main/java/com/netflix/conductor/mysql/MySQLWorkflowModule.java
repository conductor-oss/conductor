package com.netflix.conductor.mysql;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.dao.mysql.MySQLExecutionDAO;
import com.netflix.conductor.dao.mysql.MySQLMetadataDAO;
import com.netflix.conductor.dao.mysql.MySQLQueueDAO;

import javax.sql.DataSource;

/**
 * @author mustafa
 */
public class MySQLWorkflowModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(MySQLConfiguration.class).to(SystemPropertiesMySQLConfiguration.class);
        bind(DataSource.class).toProvider(MySQLDataSourceProvider.class).in(Scopes.SINGLETON);
        bind(MetadataDAO.class).to(MySQLMetadataDAO.class);
        bind(ExecutionDAO.class).to(MySQLExecutionDAO.class);
        bind(QueueDAO.class).to(MySQLQueueDAO.class);
    }

}
