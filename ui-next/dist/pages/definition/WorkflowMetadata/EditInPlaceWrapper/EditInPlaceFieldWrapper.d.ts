import { ActorRef } from "xstate";
import { CSSProperties } from "react";
import { EditInPlaceProps } from "components/ui/inputs/EditInPlace";
import { EditInPlaceMachineEvents } from "./state";
import { FunctionComponent } from "react";
interface EditorInPlaceFieldWrapperProps extends Omit<EditInPlaceProps, "isEditing" | "setEditing" | "text" | "childRef"> {
    editInPlaceActor: ActorRef<EditInPlaceMachineEvents>;
    inputStyles?: CSSProperties;
}
export declare const EditInPlaceFieldWrapper: FunctionComponent<EditorInPlaceFieldWrapperProps>;
export {};
