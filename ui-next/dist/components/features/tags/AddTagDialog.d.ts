import { TagDto } from "types/Tag";
export type TagDialogProps = {
    open: boolean;
    itemName?: string | null;
    itemType?: string | null;
    /** Called after tags are successfully saved. Receives the new tag list. */
    onSuccess: (tags: TagDto[]) => void;
    onClose: () => void;
    tags: TagDto[];
    apiPath?: string;
};
export default function AddTagDialog({ open, itemName, itemType, onSuccess, onClose, tags, apiPath, }: TagDialogProps): import("react").JSX.Element;
