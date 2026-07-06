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

public class ZohoBooksFetchResponse {

    private String connectionId;
    private String invoiceNumber;
    private String invoiceId;
    private List<String> invoiceNumbers = new ArrayList<>();
    private List<String> invoiceIds = new ArrayList<>();
    private Map<String, Object> bill;
    private List<Map<String, Object>> bills = new ArrayList<>();
    private List<Map<String, Object>> grnList = new ArrayList<>();
    private List<Map<String, Object>> podList = new ArrayList<>();

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
        return invoiceNumber;
    }

    public void setBillNumber(String billNumber) {
        this.invoiceNumber = billNumber;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getBillId() {
        return invoiceId;
    }

    public void setBillId(String billId) {
        this.invoiceId = billId;
    }

    public List<String> getInvoiceNumbers() {
        return invoiceNumbers;
    }

    public void setInvoiceNumbers(List<String> invoiceNumbers) {
        this.invoiceNumbers = invoiceNumbers;
    }

    public List<String> getBillNumbers() {
        return invoiceNumbers;
    }

    public void setBillNumbers(List<String> billNumbers) {
        this.invoiceNumbers = billNumbers;
    }

    public List<String> getInvoiceIds() {
        return invoiceIds;
    }

    public void setInvoiceIds(List<String> invoiceIds) {
        this.invoiceIds = invoiceIds;
    }

    public List<String> getBillIds() {
        return invoiceIds;
    }

    public void setBillIds(List<String> billIds) {
        this.invoiceIds = billIds;
    }

    public Map<String, Object> getBill() {
        return bill;
    }

    public void setBill(Map<String, Object> bill) {
        this.bill = bill;
    }

    public Map<String, Object> getInvoice() {
        return bill;
    }

    public void setInvoice(Map<String, Object> invoice) {
        this.bill = invoice;
    }

    public List<Map<String, Object>> getBills() {
        return bills;
    }

    public void setBills(List<Map<String, Object>> bills) {
        this.bills = bills;
    }

    public List<Map<String, Object>> getInvoices() {
        return bills;
    }

    public void setInvoices(List<Map<String, Object>> invoices) {
        this.bills = invoices;
    }

    public List<Map<String, Object>> getGrnList() {
        return grnList;
    }

    public void setGrnList(List<Map<String, Object>> grnList) {
        this.grnList = grnList;
    }

    public List<Map<String, Object>> getPodList() {
        return podList;
    }

    public void setPodList(List<Map<String, Object>> podList) {
        this.podList = podList;
    }
}
