import { FormTaskType } from "types/TaskType";
import { MaybeVariable } from "./MaybeVariable";
import { FunctionComponent } from "react";

type CommonProps = {
  label?: string;
  taskType: FormTaskType;
  path: string;
  onChange?: (val: any) => void;
  value?: any;
  onChangeHeaders?: (headers: any) => void;
};

function maybeVariable<T>(
  WrappedComponent: FunctionComponent<T>,
): FunctionComponent<T & CommonProps> {
  return function WrapperComponent(props: T & CommonProps) {
    const handleChange = (e: string) => {
      if (props.onChange) {
        return props.onChange(e);
      } else if (props.onChangeHeaders) {
        return props.onChangeHeaders(e);
      } else {
        return () => {};
      }
    };
    if (props.taskType && props.path) {
      return (
        <MaybeVariable
          value={props.value}
          onChange={handleChange}
          taskType={props.taskType}
          path={props?.path}
        >
          <WrappedComponent {...props} />
        </MaybeVariable>
      );
    } else return null;
  };
}
export default maybeVariable;
