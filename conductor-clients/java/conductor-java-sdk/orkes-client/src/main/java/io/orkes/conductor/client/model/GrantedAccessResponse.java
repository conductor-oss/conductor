/*
 * Copyright 2022 Conductor Authors.
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
package io.orkes.conductor.client.model;

import java.util.ArrayList;
import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.ToString;


@EqualsAndHashCode
@ToString
public class GrantedAccessResponse {
    private List<GrantedAccess> grantedAccess = null;

    public GrantedAccessResponse grantedAccess(List<GrantedAccess> grantedAccess) {
        this.grantedAccess = grantedAccess;
        return this;
    }

    public GrantedAccessResponse addGrantedAccessItem(GrantedAccess grantedAccessItem) {
        if (this.grantedAccess == null) {
            this.grantedAccess = new ArrayList<>();
        }
        this.grantedAccess.add(grantedAccessItem);
        return this;
    }
    
    public List<GrantedAccess> getGrantedAccess() {
        return grantedAccess;
    }

    public void setGrantedAccess(List<GrantedAccess> grantedAccess) {
        this.grantedAccess = grantedAccess;
    }
}
