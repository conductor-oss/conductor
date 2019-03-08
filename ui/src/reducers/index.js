import { combineReducers } from 'redux'
import bulk from './bulk'
import metadata from './metadata'
import search from './search'
import workflow from './workflow'
import global from './global'

const workflowApp = combineReducers({
  bulk, metadata, workflow, global, search
});

export default workflowApp
