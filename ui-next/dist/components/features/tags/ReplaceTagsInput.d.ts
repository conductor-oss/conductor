import { ReactNode } from "react";
import { TagDto } from "types/Tag";
type ReplaceTagsInputProps = {
    onChange?: (tags: string[]) => void | null;
    onInputChange?: (value: string) => void;
    label?: ReactNode;
    tags: TagDto[];
    options: TagDto[];
};
declare const ReplaceTagsInput: ({ label, onChange, onInputChange, tags, options, }: ReplaceTagsInputProps) => import("react").JSX.Element;
export default ReplaceTagsInput;
