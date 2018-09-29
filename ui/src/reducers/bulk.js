import {PERFORM_BULK_OPERATION, RECEIVE_BULK_OPERATION_RESPONSE, FAIL_BULK_OPERATION} from '../actions/bulk';

const initialState = {
  isFetching: false,
  error: null,
  successfulResults: [],
  errorResults: {}
};

export default function bulk(state = initialState, action) {
  switch (action.type) {
    case PERFORM_BULK_OPERATION:
      return {...state, isFetching: true, error: null};
    case RECEIVE_BULK_OPERATION_RESPONSE: {
      const {successfulResults = [], errorResults = {}} = action;

      return {...state, isFetching: false, error: null, successfulResults, errorResults};
    }
    case FAIL_BULK_OPERATION: {
      const {error} = action;

      return {...state, isFetching: false, error};
    }
  }

  return state;
}
