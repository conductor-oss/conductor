interface DialogData {
    name: string;
}
export interface CloneDialogProps {
    name: string;
    namesList: string[];
    onClose: () => void;
    onSuccess: (data: DialogData) => void;
    isFetching?: boolean;
    title?: string;
    id?: string;
    label?: string;
}
declare const CloneDialog: ({ name, onClose, onSuccess, namesList, isFetching, title, id, label, }: CloneDialogProps) => import("react").JSX.Element;
export default CloneDialog;
