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
	"conductor"
	"conductor/task/sample"
)

func main() {
	c := conductor.NewConductorWorker("http://localhost:8080/api", 1, 10000)

	c.Start("task_1", sample.Task_1_Execution_Function, false)
	c.Start("task_2", sample.Task_2_Execution_Function, true)
}
