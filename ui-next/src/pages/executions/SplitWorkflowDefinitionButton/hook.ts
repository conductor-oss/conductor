import { fetchWithContext } from "plugins/fetch";
import { ChangeEvent, DragEvent, useState } from "react";
import { WORKFLOW_NAME_ERROR_MESSAGE } from "utils/constants/common";
import { WORKFLOW_NAME_REGEX } from "utils/constants/regex";
import { WORKFLOW_DEFINITION_URL } from "utils/constants/route";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { useAuthHeaders } from "utils/query";

const stripBPMNExtension = (fileName: string): string => {
  return fileName.replace(/\.bpmn$/i, "");
};

export const useImportBPMWorkflow = ({ onClose }: { onClose: () => void }) => {
  const authHeaders = useAuthHeaders();
  const pushHistory = usePushHistory();
  const [selectedFile, setSelectedFile] = useState<string>("");
  const [workflowName, setWorkflowName] = useState<string>("");
  const [workflowNameError, setWorkflowNameError] = useState<string | null>(
    null,
  );
  const [overWriteWorkflow, setOverWriteWorkflow] = useState<boolean>(true);
  const [fileContent, onChangeFileContent] = useState<string>("");
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const onReset = () => {
    setSelectedFile("");
    setWorkflowName("");
    onChangeFileContent("");
    setUploadError(null);
  };

  const onUpload = async () => {
    setIsUploading(true);
    setUploadError(null);

    // Validate XML first
    try {
      const parser = new DOMParser();
      const xmlDoc = parser.parseFromString(fileContent, "text/xml");
      const parseError = xmlDoc.getElementsByTagName("parsererror").length > 0;

      if (parseError) {
        setUploadError("Invalid XML format");
        setIsUploading(false);
        return;
      }
    } catch {
      setUploadError("Invalid XML format");
      setIsUploading(false);
      return;
    }

    try {
      const fileName = workflowName.endsWith(".bpmn")
        ? workflowName
        : `${workflowName}.bpmn`;
      const importedWorkflows = await fetchWithContext(
        `/metadata/workflow-importer/import-bpm?overwrite=${overWriteWorkflow}`,
        {},
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...authHeaders,
          },
          body: JSON.stringify({
            fileName,
            fileContent,
          }),
        },
      );

      if (importedWorkflows?.length === 0) {
        setUploadError(
          "A workflow with the same name already exists. Please rename the workflow or enable the 'Overwrite workflow' option to proceed.",
        );
      } else {
        onClose();

        setSelectedFile("");
        setWorkflowName("");
        onChangeFileContent("");
        const firstWorkflow = importedWorkflows[0];
        if (firstWorkflow) {
          pushHistory(WORKFLOW_DEFINITION_URL.BASE + "/" + firstWorkflow.name);
        }
      }
    } catch (err: unknown) {
      if (err instanceof Response) {
        const errorAsJson = await err.json();
        setUploadError(errorAsJson.message || "Upload failed");
      } else if (err instanceof Error) {
        setUploadError(err.message);
      } else {
        setUploadError("Upload failed");
      }
    } finally {
      setIsUploading(false);
    }
  };

  const onFileSelect = (e: ChangeEvent<HTMLInputElement>) => {
    setUploadError(null);
    const file = e.target.files?.[0];
    if (file) {
      setSelectedFile(file.name);
      setWorkflowName(stripBPMNExtension(file.name));
      const reader = new FileReader();
      reader.onload = (e) => {
        const content = e.target?.result as string;
        onChangeFileContent(content);
      };
      reader.readAsText(file);
    }
  };

  const onDragEnter = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  };

  const onDragLeave = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  };

  const onDragOver = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
  };

  const onDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
    setUploadError(null);

    const files = Array.from(e.dataTransfer.files);
    const bpmnFile = files.find((file) => file.name.endsWith(".bpmn"));

    if (bpmnFile) {
      setSelectedFile(bpmnFile.name);
      setWorkflowName(stripBPMNExtension(bpmnFile.name));
      const reader = new FileReader();
      reader.onload = (e) => {
        const content = e.target?.result as string;
        onChangeFileContent(content);
      };
      reader.readAsText(bpmnFile);
    }
  };

  const onWorkflowNameChange = (value: string) => {
    setWorkflowName(value);
    setWorkflowNameError(
      WORKFLOW_NAME_REGEX.test(value) ? null : WORKFLOW_NAME_ERROR_MESSAGE,
    );
  };

  const onOverWriteWorkflowToggle = () => {
    setOverWriteWorkflow(!overWriteWorkflow);
  };

  return {
    onUpload,
    onFileSelect,
    onDragEnter,
    onDragLeave,
    onDragOver,
    onDrop,
    onChangeFileContent,
    onReset,
    onWorkflowNameChange,
    onOverWriteWorkflowToggle,
    selectedFile,
    workflowName,
    fileContent,
    isDragging,
    isUploading,
    uploadError,
    workflowNameError,
    overWriteWorkflow,
  } as const;
};
