import { CountdownEvents } from "pages/execution/state/types";
import { FunctionComponent } from "react";
import { WorkflowExecutionStatus } from "types/Execution";
import { ActorRef } from "xstate";
import { MuiButtonProps } from "components/ui/buttons/MuiButton";
interface MaybeAutoRefreshProps {
    buttonProps: MuiButtonProps;
    countdownActor: ActorRef<CountdownEvents>;
    refetch: () => void;
    execution: {
        status: WorkflowExecutionStatus;
    };
}
declare const MaybeAutoRefresh: FunctionComponent<MaybeAutoRefreshProps>;
export default MaybeAutoRefresh;
