/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.common.integrations.gdrive;

public class GDriveConnectionRequest {

    private String connectionId;
    private String accountName;
    private String oauthTokenJson;
    private String oauthClientJson;

    public GDriveConnectionRequest() {}

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getOauthTokenJson() {
        return oauthTokenJson;
    }

    public void setOauthTokenJson(String oauthTokenJson) {
        this.oauthTokenJson = oauthTokenJson;
    }

    public String getOauthClientJson() {
        return oauthClientJson;
    }

    public void setOauthClientJson(String oauthClientJson) {
        this.oauthClientJson = oauthClientJson;
    }
}
