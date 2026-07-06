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
package org.conductoross.conductor.common.integrations.zoho;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ZohoBooksInvoicesResponse {

    private String connectionId;
    private int count;
    private List<Map<String, Object>> invoices = new ArrayList<>();

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<Map<String, Object>> getInvoices() {
        return invoices;
    }

    public void setInvoices(List<Map<String, Object>> invoices) {
        this.invoices = invoices;
        this.count = invoices == null ? 0 : invoices.size();
    }

    public List<Map<String, Object>> getBills() {
        return invoices;
    }

    public void setBills(List<Map<String, Object>> bills) {
        setInvoices(bills);
    }
}
