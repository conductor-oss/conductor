import { DeletedWfNameType, DeletedWfVersionType } from "./types";
export declare const removeCopyFromStorage: (context: any) => Promise<boolean>;
export declare const extractKeyFromContext: ({ workflowName, currentVersion, isNewWorkflow, }: {
    workflowName: string;
    currentVersion?: number;
    isNewWorkflow?: boolean;
}) => string;
export declare const addLocalCopyTime: (wfKey: any) => void;
export declare const removeLocalCopyTime: (wfKey: any) => void;
export declare const getLocalCopyTime: (wfKey: any) => string | null;
export declare const removeCachedChangesFromWorkflow: (deletedWfName: DeletedWfNameType, deletedWfVersion?: DeletedWfVersionType, isNewWorkflow?: boolean, previousVersion?: DeletedWfVersionType) => void;
export declare const removeDeletedWorkflow: (deletedWfName: DeletedWfNameType, deletedWfVersion: DeletedWfVersionType, isNewWorkflow?: boolean) => void;
export declare function getTemplateFromInputParams(inputParamsArray: any): string;
