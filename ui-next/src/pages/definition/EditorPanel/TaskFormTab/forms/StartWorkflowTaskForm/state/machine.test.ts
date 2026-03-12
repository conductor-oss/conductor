import { interpret } from "xstate";
import { startSubWfNameVersionMachine } from "./machine";
import {
  StartSubWfNameVersionTypes,
  StartSubWfNameVersionStates,
} from "./types";
import * as actions from "./actions";
// Mocking services

const workflowNameVersionMap = new Map([
  ["workflow1", [1, 2]],
  ["English_Lesson", [1]],
  ["workflow13", [3, 4]],
]);
const mockMachine = startSubWfNameVersionMachine.withConfig({
  services: {
    fetchWfNamesAndVersions: async () =>
      await Promise.resolve(workflowNameVersionMap),
    handleSelect: async () => {},
  },
  actions: actions as any,
});

const service = interpret(mockMachine);

beforeAll(() => {
  // Start the service
  service.start();
});

afterAll(() => {
  // Stop the service when you are no longer using it.
  service.stop();
});
describe("StartSubWfVersion machine tests", () => {
  it(`should reach ${StartSubWfNameVersionStates.IDLE} state after handling`, () => {
    const newFieldName = "English_Lesson";

    return new Promise<void>((resolve, reject) => {
      service.onTransition((state) => {
        if (
          state.matches([StartSubWfNameVersionStates.IDLE]) &&
          !state.context.availableVersions
        ) {
          // Send events
          service.send({
            type: StartSubWfNameVersionTypes.SELECT_WORKFLOW_NAME,
            name: newFieldName,
          });
        }

        try {
          // When SELECT_WORKFLOW_NAME event occurs, state should be in HANDLE_SELECT_WORKFLOW_NAME
          expect(
            state.event.type !==
              StartSubWfNameVersionTypes.SELECT_WORKFLOW_NAME ||
              state.matches([
                StartSubWfNameVersionStates.HANDLE_SELECT_WORKFLOW_NAME,
              ]),
          ).toBeTruthy();

          // Check if we've reached the final state
          const isIdleWithVersions =
            state.matches([StartSubWfNameVersionStates.IDLE]) &&
            state.context.availableVersions;

          // Assert context values are correct when in final state
          expect(
            !isIdleWithVersions || state.context.workflowName === newFieldName,
          ).toBeTruthy();
          expect(
            !isIdleWithVersions ||
              JSON.stringify(state.context.availableVersions) ===
                JSON.stringify([1]),
          ).toBeTruthy();

          if (isIdleWithVersions) {
            resolve();
          }
        } catch (error) {
          reject(error);
        }
      });
    });
  });
});
