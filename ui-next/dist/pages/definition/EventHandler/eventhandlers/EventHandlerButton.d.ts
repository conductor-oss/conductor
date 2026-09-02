type Props = {
    isConfirmSave?: boolean;
    isConfirmReset?: boolean;
    isSaving?: boolean;
    handleConfirmSaveRequest?: () => void;
    handleCancelRequest?: () => void;
    handleSaveRequest?: () => void;
    handleResetRequest?: () => void;
    isNewEventHandler?: boolean;
    handleDeleteRequest?: () => void;
    service: any;
    disableDeleteBtn: boolean;
};
declare const EventHandlerButton: ({ isConfirmSave, isSaving, handleConfirmSaveRequest, handleCancelRequest, handleSaveRequest, handleResetRequest, handleDeleteRequest, isNewEventHandler, service, disableDeleteBtn, }: Props) => import("react").JSX.Element;
export default EventHandlerButton;
