/**
 * LogRocket - OSS Stub
 *
 * LogRocket is an enterprise-only feature.
 * This file provides no-op implementations for OSS builds.
 * The enterprise package has the full implementation with actual LogRocket integration.
 */

// LogRocket is never enabled in OSS
export const isLogRocketEnabled = () => false;

type LogRocketEvents =
  | "user_complete_task"
  | "user_claim_task"
  | "template_import"
  | "user_copy_install_script"
  | "user_toggle_show_description"
  | "user_created_access_key_in_metadata_banner"
  | "user_first_workflow_executed"
  | "blank_slate_docs_link_clicked"
  | "user_recreated_access_key_in_worker_manual_install_instructions"
  | "user_recreated_access_key_in_worker_orkes_cli_install_instructions";

// No-op: LogRocket tracking disabled in OSS
export const logrocketTrackIfEnabled = (
  _eventName: LogRocketEvents,
  _eventProperties?: any,
) => {
  // No-op in OSS
};

// No-op: LogRocket initialization disabled in OSS
export const useMaybeEnableLogRocket = () => {
  // No-op in OSS
};

type ICaptureOptions = {
  tags?: {
    [tagName: string]: string | number | boolean;
  };
  extra?: {
    [tagName: string]: string | number | boolean;
  };
};

// No-op: LogRocket error reporting disabled in OSS
export const reportErrorToLogRocket = (
  _error: Error | string,
  _metadata?: ICaptureOptions,
) => {
  // No-op in OSS
};

type SimpleUserInfo = {
  uuid?: string;
  user?: any;
  id?: string;
};

// No-op: LogRocket user identification disabled in OSS
export const useIdentifyUserInLogRocket = (
  _currentUserInfo?: SimpleUserInfo,
) => {
  // No-op in OSS
};
