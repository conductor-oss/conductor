/*
 * Copyright 2022 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.postgres.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("conductor.external-payload-storage.postgres")
public class PostgresPayloadProperties {

    /** The PostgreSQL schema and table name where the payloads will be stored */
    private String tableName = "external.external_payload";

    /** Username for connecting to PostgreSQL database */
    private String username;

    /** Password for connecting to PostgreSQL database */
    private String password;

    /** URL for connecting to PostgreSQL database */
    private String url;

    /**
     * Maximum count of data rows in PostgreSQL database. After overcoming this limit, the oldest
     * data will be deleted.
     */
    private long maxDataRows = Long.MAX_VALUE;

    /**
     * Maximum count of days of data age in PostgreSQL database. After overcoming limit, the oldest
     * data will be deleted.
     */
    private int maxDataDays = 0;

    /**
     * Maximum count of months of data age in PostgreSQL database. After overcoming limit, the
     * oldest data will be deleted.
     */
    private int maxDataMonths = 0;

    /**
     * Maximum count of years of data age in PostgreSQL database. After overcoming limit, the oldest
     * data will be deleted.
     */
    private int maxDataYears = 1;

    /**
     * URL, that can be used to pull the json configurations, that will be downloaded from
     * PostgreSQL to the conductor server. For example: for local development it is
     * "http://localhost:8080"
     */
    private String conductorUrl = "";

    public String getTableName() {
        return tableName;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getUrl() {
        return url;
    }

    public String getConductorUrl() {
        return conductorUrl;
    }

    public long getMaxDataRows() {
        return maxDataRows;
    }

    public int getMaxDataDays() {
        return maxDataDays;
    }

    public int getMaxDataMonths() {
        return maxDataMonths;
    }

    public int getMaxDataYears() {
        return maxDataYears;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setConductorUrl(String conductorUrl) {
        this.conductorUrl = conductorUrl;
    }

    public void setMaxDataRows(long maxDataRows) {
        this.maxDataRows = maxDataRows;
    }

    public void setMaxDataDays(int maxDataDays) {
        this.maxDataDays = maxDataDays;
    }

    public void setMaxDataMonths(int maxDataMonths) {
        this.maxDataMonths = maxDataMonths;
    }

    public void setMaxDataYears(int maxDataYears) {
        this.maxDataYears = maxDataYears;
    }
}
