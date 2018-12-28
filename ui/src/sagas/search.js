import get from "lodash/get";
import isEmpty from "lodash/isEmpty";
import join from "lodash/join";
import toString from "lodash/toString";

import { all, call, put, select, takeLatest } from 'redux-saga/effects';

import {FETCH_SEARCH_RESULTS} from '../actions/search';
import {failSearchResults, receiveSearchResults} from '../actions/search';
import http from '../core/HttpClient';

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const getHits = data => get(data, 'result.hits', []);
const getTotalHits = data => get(data, 'result.totalHits', 0);

function* doFetchSearchResults() {
  const {query, entirely, types, states, cutoff, start} = yield select(state => state.search);

  const queryParts = [];

  if (!isEmpty(types)) {
    queryParts.push(`workflowType IN (${encodeURIComponent(toString(types))})`);
  }

  if (!isEmpty(states)) {
    queryParts.push(`status IN (${toString(states)})`);
  }

  const fullQuery = join(queryParts, ' AND ');
  const freeText = (!isEmpty(query) && entirely) ? `"${query}"` : query;

  const searchUrl = `/api/wfe/?q=${fullQuery}&h=${cutoff}&freeText=${freeText}&start=${start}`;

  try {
    // this is possible a task ID
    if (query.match(UUID_RE)) {
      const taskSearchUrl = `/api/wfe/search-by-task/${query}?q=${fullQuery}&h=${cutoff}&start=${start}`;

      const [allResult, byTaskResult] = yield all([
        call(http.get, searchUrl),
        call(http.get, taskSearchUrl)
      ]);

      /// only use the task search results if we don't get any results searching everywhere
      if (getTotalHits(allResult) === 0) {
        yield put(receiveSearchResults(getHits(byTaskResult), getTotalHits(byTaskResult)));
      } else {
        yield put(receiveSearchResults(getHits(allResult), getTotalHits(allResult)));
      }
    } else {
      const result = yield call(http.get, searchUrl);

      yield put(receiveSearchResults(getHits(result), getTotalHits(result)));
    }
  } catch ({message}) {
    yield put(failSearchResults(message));
  }
}

export default [
  takeLatest(FETCH_SEARCH_RESULTS, doFetchSearchResults)
];
