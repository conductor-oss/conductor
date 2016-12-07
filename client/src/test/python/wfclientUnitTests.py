import unittest
import wfclient
import time

class WFClientTests(unittest.TestCase):
    
    def testSimpleWorkflow(self):
        wfcMgr = wfclient.WFClientMgr()
        wc = wfcMgr.workflowClient
        tc = wfcMgr.taskClient
        
                    
        inputData = {}
        inputData['i1'] = "input1"
        inputData['i2'] = "input2"
        wfid = wc.startWorkflow("integ_test_wf_1", inputData)
        self.assertTrue(wfid is not None)
        
        wf = wc.getWorkflow(wfid, False)
        self.assertEquals(wf['status'], 'RUNNING')
    
        wc.pauseWorkflow(wfid)
        wf = wc.getWorkflow(wfid, False)
        self.assertEquals(wf['status'], 'PAUSED')
    
        wc.resumeWorkflow(wfid)
        wf = wc.getWorkflow(wfid, False)
        self.assertEquals(wf['status'], 'RUNNING')
        
        time.sleep(1)
        # Get task and complete
        task = tc.pollForTask("integ_test_task_1", "workerid1")
        self.assertTrue(tc.ackTask(task['taskId'], 'workerid1'), "Ack Failed!!")
        inputData = task['inputData']
        self.assertEquals(inputData['i1'], 'input1')
        self.assertEquals(inputData['i2'], 'input2')
        
        outputData = {"o1":"task1_output_1"}
        task['outputData'] = outputData
        task['status'] = "COMPLETED"
        tc.updateTask(task)
        task = tc.getTask(task['taskId'])
        self.assertEquals(task['status'], 'COMPLETED')
        
        
        time.sleep(2)
        # Get task and complete
        task = tc.pollForTask("integ_test_task_2", "workerid1")
        self.assertTrue(tc.ackTask(task['taskId'], 'workerid1'), "Ack Failed!!")
        inputData = task['inputData']
        self.assertEquals(inputData['i1'], 'task1_output_1')
        
        outputData = {"o1":"task2_output_1"}
        task['outputData'] = outputData
        task['status'] = "COMPLETED"
        tc.updateTask(task)
        task = tc.getTask(task['taskId'])
        self.assertEquals(task['status'], 'COMPLETED')
        
        time.sleep(2)
        wf = wc.getWorkflow(wfid, False)
        self.assertEquals(wf['status'], 'COMPLETED')

if __name__ == '__main__':
    unittest.main()