import { ReactElement } from "react";
import { ActorRef } from "xstate";
import { AccessKey, CreateAndDisplayApplicationEvents } from "./state/types";
interface MetadataBannerStatelessProps {
    installScript?: string;
    readme?: string;
    isDisplayKeys: boolean;
    isErrorCreatingApp?: boolean;
    applicationAccessKey: AccessKey;
    onCopy: () => void;
    onClose?: () => void;
    KeysDisplayerComponent: (props: {
        onClose: () => void;
        accessKeys: AccessKey;
    }) => ReactElement;
    onGetAccessKey: () => void;
    onRecreateKeys: () => void;
    onCloseKeysDialog: () => void;
    errorCreatingAppMessage?: string;
}
export declare const MetadataBannerStateless: ({ installScript, isDisplayKeys, applicationAccessKey, readme, onCopy, onClose, onGetAccessKey, onRecreateKeys, onCloseKeysDialog, KeysDisplayerComponent, isErrorCreatingApp, errorCreatingAppMessage, }: MetadataBannerStatelessProps) => import("react").JSX.Element | null;
interface MetadataBannerProps {
    createAndDisplayAppActor: ActorRef<CreateAndDisplayApplicationEvents>;
    KeysDisplayerComponent: (props: {
        onClose: () => void;
        accessKeys: AccessKey;
    }) => ReactElement;
    onClose?: () => void;
    installScript?: string;
    readme?: string;
}
export declare const MetadataBanner: ({ createAndDisplayAppActor: metadataEditorActor, onClose, installScript, readme, KeysDisplayerComponent, }: MetadataBannerProps) => import("react").JSX.Element;
export {};
