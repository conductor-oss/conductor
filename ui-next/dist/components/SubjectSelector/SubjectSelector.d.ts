import { FunctionComponent } from "react";
import { SelectableOption } from "./types";
import { AccessGroup, User } from "types";
import { Application } from "types/Application";
type SubjectSelectorBaseParentProps = {
    label?: string;
    selectableUsers: User[];
    selectableGroups: AccessGroup[];
    selectableApplications: Application[];
    growPopper?: boolean;
};
type SubjectSelectorMultipleBaseProps = SubjectSelectorBaseParentProps & {
    multiple: true;
    onChange: (value: SelectableOption | SelectableOption[]) => void;
    selectedSubjectsValue: string[];
};
type SubjectSelectorSingleBaseProps = SubjectSelectorBaseParentProps & {
    multiple: false;
    onChange: (value: SelectableOption | SelectableOption[]) => void;
    selectedSubjectsValue?: string;
};
export declare const SubjectSelectorBase: FunctionComponent<SubjectSelectorMultipleBaseProps | SubjectSelectorSingleBaseProps>;
export {};
