import { ChangeEvent, DragEvent } from "react";
export declare const useImportBPMWorkflow: ({ onClose }: {
    onClose: () => void;
}) => {
    readonly onUpload: () => Promise<void>;
    readonly onFileSelect: (e: ChangeEvent<HTMLInputElement>) => void;
    readonly onDragEnter: (e: DragEvent<HTMLDivElement>) => void;
    readonly onDragLeave: (e: DragEvent<HTMLDivElement>) => void;
    readonly onDragOver: (e: DragEvent<HTMLDivElement>) => void;
    readonly onDrop: (e: DragEvent<HTMLDivElement>) => void;
    readonly onChangeFileContent: import("react").Dispatch<import("react").SetStateAction<string>>;
    readonly onReset: () => void;
    readonly onWorkflowNameChange: (value: string) => void;
    readonly onOverWriteWorkflowToggle: () => void;
    readonly selectedFile: string;
    readonly workflowName: string;
    readonly fileContent: string;
    readonly isDragging: boolean;
    readonly isUploading: boolean;
    readonly uploadError: string | null;
    readonly workflowNameError: string | null;
    readonly overWriteWorkflow: boolean;
};
