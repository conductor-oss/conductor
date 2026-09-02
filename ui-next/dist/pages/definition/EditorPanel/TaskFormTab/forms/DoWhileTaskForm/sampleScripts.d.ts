import { DoWhileTaskDef } from "types/TaskType";
import { TaskDef } from "types/common";
export declare const genSampleScripts: (task: DoWhileTaskDef | undefined) => {
    fixed_number: {
        loopCondition: string;
        inputParameters: {
            number: number;
        };
        loopOver: never[];
    };
    iterate_over_array: {
        loopCondition: string;
        inputParameters: {
            myArray: ({
                name: string;
                year?: undefined;
            } | {
                year: number;
                name?: undefined;
            })[];
        };
        loopOver: TaskDef[];
    };
};
