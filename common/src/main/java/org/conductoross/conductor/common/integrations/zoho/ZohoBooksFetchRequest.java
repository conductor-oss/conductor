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

public class ZohoBooksFetchRequest {

    private String connectionId;
    private String billNumber;
    private List<String> billNumbers = new ArrayList<>();
    private String invoiceNumber;
    private List<String> invoiceNumbers = new ArrayList<>();

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getBillNumber() {
        return billNumber;
    }

    public void setBillNumber(String billNumber) {
        this.billNumber = billNumber;
    }

    public List<String> getInvoiceNumbers() {
        return invoiceNumbers;
    }

    public void setInvoiceNumbers(List<String> invoiceNumbers) {
        this.invoiceNumbers = invoiceNumbers;
    }

    public List<String> getBillNumbers() {
        return billNumbers;
    }

    public void setBillNumbers(List<String> billNumbers) {
        this.billNumbers = billNumbers;
    }
}
