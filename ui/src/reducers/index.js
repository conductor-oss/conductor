import { combineReducers } from 'redux'
import workflow from './workflow'
import global from './global'

const workflowApp = combineReducers({
  workflow, global
})

export default workflowApp
