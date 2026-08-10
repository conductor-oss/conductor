import {
  IconProps,
  ArrowCounterClockwise as Restart,
} from "@phosphor-icons/react";
import { useActor, useSelector } from "@xstate/react";
import RefreshIcon from "components/icons/RefreshIcon";
import isNil from "lodash/isNil";
import {
  COUNT_DOWN_TYPE,
  CountdownContext,
  CountdownEventTypes,
  CountdownEvents,
} from "pages/execution/state/types";
import {
  ForwardRefExoticComponent,
  FunctionComponent,
  RefAttributes,
  useState,
} from "react";
import { WorkflowExecutionStatus } from "types/Execution";
import { ActorRef, State } from "xstate";
import DropdownButton from "components/ui/buttons/DropdownButton";
import Button, { MuiButtonProps } from "components/ui/buttons/MuiButton";
import { SpinningIcon } from "components/ui/SpinningIcon";

interface AutoRefreshButtonProps {
  buttonProps: MuiButtonProps;
  countdownActor: ActorRef<CountdownEvents>;
}

const SpinningRefreshIcon = SpinningIcon(
  RefreshIcon as ForwardRefExoticComponent<
    IconProps & RefAttributes<SVGSVGElement>
  >,
);

const AutoRefreshButton: FunctionComponent<AutoRefreshButtonProps> = ({
  buttonProps,
  countdownActor,
}) => {
  const duration = useSelector(
    countdownActor,
    (state: State<CountdownContext>) => state.context.duration,
  );
  const elapsed = useSelector(countdownActor, (state) => state.context.elapsed);
  const isDisabled = useSelector(countdownActor, (state) =>
    state.matches("disabled"),
  );
  const [, send] = useActor(countdownActor);

  const disableCounter = () => send({ type: CountdownEventTypes.DISABLE });
  const enableCounter = () => send({ type: CountdownEventTypes.ENABLE });
  const forceRefresh = () => send({ type: CountdownEventTypes.FORCE_FINISH });
  const updateDuration = (
    duration: number,
    countdownType = COUNT_DOWN_TYPE.INFINITE,
  ) =>
    send({
      type: CountdownEventTypes.UPDATE_DURATION,
      duration,
      countdownType,
    });

  return (
    <>
      <DropdownButton
        options={[
          {
            label: "Refresh every 5s",
            handler: () => {
              updateDuration(5);
            },
            disabled: isDisabled,
          },
          {
            label: "Refresh every 10s",
            handler: () => {
              updateDuration(10);
            },
            disabled: isDisabled,
          },
          {
            label: "Refresh every 15s",
            handler: () => {
              updateDuration(15);
            },
            disabled: isDisabled,
          },
          {
            label: "Refresh every 30s",
            handler: () => {
              updateDuration(30);
            },
            disabled: isDisabled,
          },
          {
            label: "Refresh every 60s",
            handler: () => {
              updateDuration(60);
            },
            disabled: isDisabled,
          },
          {
            label: `${isDisabled ? "Enable" : "Disable"} Auto Refresh`,
            handler: isDisabled ? enableCounter : disableCounter,
          },
        ]}
        buttonProps={buttonProps}
      >
        Refresh {duration - elapsed}
      </DropdownButton>
      <Button
        id="refresh-icon-button"
        sx={{
          padding: 1,
          minWidth: "auto",
          minHeight: "auto",
          maxHeight: "28px",
        }}
        onClick={forceRefresh}
      >
        <Restart size={16} />
      </Button>
    </>
  );
};
interface MaybeAutoRefreshProps {
  buttonProps: MuiButtonProps;
  countdownActor: ActorRef<CountdownEvents>;
  refetch: () => void;
  execution: {
    status: WorkflowExecutionStatus;
  };
}
const MaybeAutoRefresh: FunctionComponent<MaybeAutoRefreshProps> = ({
  buttonProps,
  countdownActor,
  refetch,
  execution,
}) => {
  const [isAnimating, setIsAnimating] = useState(false);

  const handleRefetch = () => {
    setIsAnimating(true);
    refetch();
  };
  return isNil(countdownActor) ? (
    <Button
      variant="text"
      size="small"
      startIcon={
        <SpinningRefreshIcon
          loading={isAnimating}
          size={24}
          onAnimationEnd={() => setIsAnimating(false)}
        />
      }
      disabled={[
        WorkflowExecutionStatus.RUNNING,
        WorkflowExecutionStatus.PAUSED,
        WorkflowExecutionStatus.TIMED_OUT,
      ].includes(execution?.status)}
      onClick={handleRefetch}
    >
      Refresh
    </Button>
  ) : (
    <AutoRefreshButton
      buttonProps={buttonProps}
      countdownActor={countdownActor}
    />
  );
};

export default MaybeAutoRefresh;
