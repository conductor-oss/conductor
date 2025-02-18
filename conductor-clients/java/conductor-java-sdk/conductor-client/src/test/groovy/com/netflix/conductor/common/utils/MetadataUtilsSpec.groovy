package com.netflix.conductor.common.utils


import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import spock.lang.Specification

class MetadataUtilsSpec extends Specification {

    def "computed checksum matches"() {
        given:
          def wf0 = getWorkflowDef("someone@mail.com")
          def wf1 = getWorkflowDef("someone_else@mail.com")

        when:
          def checksum0 = MetadataUtils.computeChecksum(wf0)
          def checksum1 = MetadataUtils.computeChecksum(wf1)
        then:
          checksum0 != null
          checksum0 == checksum1
          MetadataUtils.areChecksumsEqual(wf0, wf1)
    }

    def "computed checksum does not match"() {
        given:
          def wf0 = getWorkflowDef("someone@mail.com")
          def wf1 = getWorkflowDef("someone_else@mail.com")
          wf1.setDescription("A change in the description")
        when:
          def checksum0 = MetadataUtils.computeChecksum(wf0)
          def checksum1 = MetadataUtils.computeChecksum(wf1)
        then:
          checksum0 != null
          checksum0 != checksum1
          !MetadataUtils.areChecksumsEqual(wf0, wf1)
    }

    private static WorkflowDef getWorkflowDef(String owner) {
        def wfDef = new WorkflowDef()
        wfDef.setDescription("A sample workflow")
        wfDef.setName("test_workflow")
        wfDef.setVersion(1)
        wfDef.setOwnerEmail(owner)
        wfDef.setUpdateTime(System.currentTimeMillis())
        wfDef.setCreateTime(System.currentTimeMillis())

        var wfTask0 = new WorkflowTask()
        wfTask0.setName("task0")
        wfTask0.setTaskReferenceName("task0_ref")
        wfTask0.setDescription("Task 0")
        wfTask0.setType("SIMPLE")
        var taskDef = new TaskDef();
        taskDef.setName("task0")
        taskDef.setUpdateTime(System.currentTimeMillis())
        taskDef.setCreateTime(System.currentTimeMillis())
        taskDef.setOwnerEmail(owner)

        var wfTask1 = new WorkflowTask()
        wfTask0.setName("task1")
        wfTask0.setTaskReferenceName("task1_ref")
        wfTask0.setDescription("Task 1")
        wfTask0.setType("SIMPLE")

        wfDef.setTasks([wfTask0, wfTask1])
        return wfDef;
    }
}
