/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.bootstrap;

import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearch;
import com.netflix.conductor.grpc.server.GRPCServer;
import org.apache.log4j.PropertyConfigurator;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class BootstrapUtil {
    public static void startEmbeddedElasticsearchServer(EmbeddedElasticSearch embeddedElasticsearchInstance) {

        final int EMBEDDED_ES_INIT_TIME = 5000;
        try {
            embeddedElasticsearchInstance.start();
            /*
             * Elasticsearch embedded instance does not notify when it is up and ready to accept incoming requests.
             * A possible solution for reading and writing into the index is to wait a specific amount of time.
             */
            Thread.sleep(EMBEDDED_ES_INIT_TIME);
        } catch (Exception ioe) {
            System.out.println("Error starting Embedded ElasticSearch");
            ioe.printStackTrace(System.err);
            System.exit(3);
        }
    }


    public static void setupIndex(IndexDAO indexDAO) {
        try {
            indexDAO.setup();
        } catch (Exception e) {
            System.out.println("Error setting up elasticsearch index");
            e.printStackTrace(System.err);
            System.exit(3);
        }
    }

    static void startGRPCServer(GRPCServer grpcServer) {
        try {
            grpcServer.start();
        } catch (IOException ioe) {
            System.out.println("Error starting GRPC server");
            ioe.printStackTrace(System.err);
            System.exit(3);
        }
    }

    public static void loadConfigFile(String propertyFile) throws IOException {
        if (propertyFile == null) return;
        System.out.println("Using config file: " + propertyFile);
        Properties props = new Properties(System.getProperties());
        props.load(new FileInputStream(propertyFile));
        System.setProperties(props);
    }

    public static void loadLog4jConfig(String log4jConfigFile) throws FileNotFoundException {
        if (log4jConfigFile != null) {
            PropertyConfigurator.configure(new FileInputStream(log4jConfigFile));
        }
    }

}
