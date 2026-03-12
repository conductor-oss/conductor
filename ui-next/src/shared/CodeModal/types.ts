export type SupportedDisplayTypes = "javascript" | "java" | "curl" | "python";

export type ApiSearchModalProps = {
  dialogTitle?: string;
  dialogHeaderText?: string;
  code: string;
  handleClose: () => void;
  onTabChange: (selectedType: SupportedDisplayTypes) => void;
  displayLanguage: SupportedDisplayTypes;
  languages: SupportedDisplayTypes[];
};
