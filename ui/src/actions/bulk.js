export const PERFORM_BULK_OPERATION = "PERFORM_BULK_OPERATION";
export const RECEIVE_BULK_OPERATION_RESPONSE = "RECEIVE_BULK_OPERATION_RESPONSE";
export const FAIL_BULK_OPERATION = "FAIL_BULK_OPERATION";

export function performBulkOperation(operation, workflows) {
  return {type: PERFORM_BULK_OPERATION, operation, workflows};
}

export function receiveBulkOperationResponse(successfulResults, errorResults) {
  return {type: RECEIVE_BULK_OPERATION_RESPONSE, successfulResults, errorResults};
}

export function failBulkOperation(error) {
  return {type: FAIL_BULK_OPERATION, error};
}
