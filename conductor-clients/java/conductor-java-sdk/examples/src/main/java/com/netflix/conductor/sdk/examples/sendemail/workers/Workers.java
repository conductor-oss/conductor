/*
 * Copyright 2024 Orkes, Inc.
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
package com.netflix.conductor.sdk.examples.sendemail.workers;

import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import lombok.Data;

public class Workers {

    @WorkerTask("send_email")
    public void sendEmail(@InputParam("email") String email) {
        System.out.println("Sending email to " + email);
    }

    @WorkerTask("get_user_info")
    public UserInfo getUserInfo(@InputParam("userId") String userId) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(userId);
        userInfo.setName("User X");
        userInfo.setEmail(userId + "@example.com");
        userInfo.setPhoneNumber("555-555-5555");
        return userInfo;
    }

    @Data
    public static class UserInfo {
        private String name;
        private String id;
        private String email;
        private String phoneNumber;
    }
}
