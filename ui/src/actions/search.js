export const CHANGE_SEARCH = "UPDATE_SEARCH";
export const FETCH_SEARCH_RESULTS = "FETCH_SEARCH_RESULTS";
export const RECEIVE_SEARCH_RESULTS = "RECEIVE_SEARCH_RESULTS";
export const FAIL_SEARCH_RESULTS = "FAIL_SEARCH_RESULTS";

export function changeSearch({query, entirely, types, states, cutoff, start}) {
  return {type: CHANGE_SEARCH, query, entirely, types, states, cutoff, start};
}

export function fetchSearchResults() {
  return {type: FETCH_SEARCH_RESULTS};
}

export function receiveSearchResults(results, totalHits) {
  return {type: RECEIVE_SEARCH_RESULTS, results, totalHits};
}

export function failSearchResults(error) {
  return {type: FAIL_SEARCH_RESULTS, error};
}

export function updateSearchAndFetch(props) {
  return dispatch => {
    dispatch(changeSearch(props));
    dispatch(fetchSearchResults());
  }
}
