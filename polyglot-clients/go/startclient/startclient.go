// Copyright 2017 Netflix, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package main

import (
	conductor "github.com/netflix/conductor/client/go"
	"github.com/netflix/conductor/client/go/task/sample"
	log "github.com/sirupsen/logrus"
	"os"
)

//Example init function that shows how to configure logging
//Using json formatter and changing level to Debug
func init() {

	// Log as JSON instead of the default ASCII formatter.
	log.SetFormatter(&log.JSONFormatter{})

	//Stdout, change to a file for production use case
	log.SetOutput(os.Stdout)

	// Set to debug for demonstration.  Change to Info for production use cases.
	log.SetLevel(log.DebugLevel)
}
func main() {
	c := conductor.NewConductorWorker("http://localhost:8080/api", 1, 1)

	c.Start("task_15", "", sample.Task_1_Execution_Function, true)
	//c.Start("task_2", "mydomain", sample.Task_2_Execution_Function, true)
}
