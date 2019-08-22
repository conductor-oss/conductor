/* eslint-disable no-undef */
import assert from 'assert';
import workflows from '../../src/reducers/workflow';

describe('WorkflowReducer', () => {
  describe('REQUEST_ERROR', () => {
    it('should return the appropriate values for an error', () => {
      const mockError = new Error('error');
      const requestErrorAction = { type: 'REQUEST_ERROR', e: mockError };
      const nextState = workflows(null, requestErrorAction);
      assert.equal(nextState.error, true);
      assert.equal(nextState.exception, mockError);
      assert.equal(nextState.fetching, false);
      assert.equal(nextState.restarting, false);
      assert.equal(nextState.terminating, false);
      assert.equal(nextState.retrying, false);
      assert.equal(nextState.pausing, false);
      assert.equal(nextState.resuming, false);
      assert.equal(nextState.bulkProcessInFlight, false);
      assert.equal(nextState.bulkProcessSuccess, false);
    });
  });
});
