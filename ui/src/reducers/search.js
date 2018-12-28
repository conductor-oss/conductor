import {CHANGE_SEARCH, FETCH_SEARCH_RESULTS, RECEIVE_SEARCH_RESULTS, FAIL_SEARCH_RESULTS} from "../actions/search";

const initialState = {
  query: "",
  entirely: false,
  types: [],
  states: [],
  cutoff: "",
  start: 0,
  isFetching: false,
  error: null,
  results: [],
  totalHits: 0
};

export default function search(state = initialState, action) {
  switch (action.type) {
    case CHANGE_SEARCH: {
      const {query = '', entirely = false, types = [], states = [], cutoff = '', start = 0} = action;

      return {...state, query, entirely, types, states, cutoff, start: Math.max(0, start)};
    }

    case FETCH_SEARCH_RESULTS:
      return {...state, isFetching: true, error: null};

    case RECEIVE_SEARCH_RESULTS: {
      const {results, totalHits} = action;

      return {...state, isFetching: false, error: null, results, totalHits};
    }

    case FAIL_SEARCH_RESULTS: {
      const {error} = action;

      return {...state, isFetching: false, error};
    }
  }

  return state;
}
