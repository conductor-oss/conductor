import iteratee from "lodash/iteratee";
import map from "lodash/map";
import uniq from "lodash/uniq";
import sortBy from "lodash/sortBy";

import {FETCH_WORKFLOW_METADATA, RECEIVE_WORKFLOW_METADATA, FAIL_WORKFLOW_METADATA } from "../actions/metadata";

const initialState = {
  isFetching: false,
  workflows: [],
  error: null
};

export default function metadata(state = initialState, action) {
  switch (action.type) {
    case FETCH_WORKFLOW_METADATA:
      return {...state, isFetching: true, error: null};
    case RECEIVE_WORKFLOW_METADATA: {
      const {workflows} = action;
      const workflowTypes = sortBy(uniq(map(workflows, iteratee('name'))));

      return {...state, isFetching: false, error: null, workflows: workflowTypes};
    }
    case FAIL_WORKFLOW_METADATA: {
      const {error} = action;

      return {...state, isFetching: false, error};
    }
    default:
      return state;

  }
}
