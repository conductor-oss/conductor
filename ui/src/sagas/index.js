import {all} from 'redux-saga/effects'

import bulkSagas from './bulk';
import searchSagas from './search';

export default function* rootSaga() {
  yield all([
      ...bulkSagas,
      ...searchSagas
  ]);
}
