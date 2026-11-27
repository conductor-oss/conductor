/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.cassandra.config

import spock.lang.Specification
import spock.lang.Subject

class CassandraPropertiesSpec extends Specification {

    @Subject
    CassandraProperties subject

    def setup() {
        subject = new CassandraProperties()
    }

    def "credentials default to empty so auth is not applied"() {
        expect:
        subject.username == ""
        subject.password == ""
    }

    def "credentials can be configured for an authenticated cluster"() {
        when:
        subject.username = "cassandra"
        subject.password = "cassandra"

        then:
        subject.username == "cassandra"
        subject.password == "cassandra"
    }
}
