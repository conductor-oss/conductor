/*
 * Copyright 2021 Conductor Authors.
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
package com.netflix.conductor.sdk.workflow.testing;

public class Task1Input {

    private int mod;

    private int oddEven;

    public int getMod() {
        return mod;
    }

    public void setMod(int mod) {
        this.mod = mod;
    }

    public int getOddEven() {
        return oddEven;
    }

    public void setOddEven(int oddEven) {
        this.oddEven = oddEven;
    }

    @Override
    public String toString() {
        return "Task1Input{" + "mod=" + mod + ", oddEven=" + oddEven + '}';
    }
}
