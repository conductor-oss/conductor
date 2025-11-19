import { mount } from "cypress/react";
import WorkflowDAG from "../../components/diagram/WorkflowDAG";
import TimelineComponent from "./Timeline";

describe("<Timeline>", () => {
  it("Do_while containing switch - renders without crashing", () => {
    cy.fixture("doWhile/doWhileSwitch").then((data) => {
      const dag = new WorkflowDAG(data);
      const tasks = data.tasks;

      // This test verifies the fix for #534 - Timeline should not crash
      // when DO_WHILE contains SWITCH defaultCase
      mount(
        <TimelineComponent
          dag={dag}
          tasks={tasks}
          onClick={() => {}}
          selectedTask={null}
        />
      );

      // Verify Timeline renders without errors
      cy.get(".timeline-container").should("exist");
      cy.get(".vis-timeline").should("exist");

      // Verify timeline items are rendered
      cy.get(".vis-item").should("have.length.at.least", 1);
    });
  });

  it("Do_while containing switch - handles tasks with no dfParent", () => {
    cy.fixture("doWhile/doWhileSwitch").then((data) => {
      const dag = new WorkflowDAG(data);
      const tasks = data.tasks;

      mount(
        <TimelineComponent
          dag={dag}
          tasks={tasks}
          onClick={() => {}}
          selectedTask={null}
        />
      );

      // Verify the fix: Timeline should handle tasks where dfParent is undefined
      // without trying to access dfParent.ref (which would cause a crash)
      cy.get(".timeline-container").should("exist");

      // Verify groups are created correctly
      cy.get(".vis-labelset").should("exist");
    });
  });

  it("Dynamic Fork - renders timeline correctly", () => {
    cy.fixture("dynamicFork/success").then((data) => {
      const dag = new WorkflowDAG(data);
      const tasks = data.tasks;

      mount(
        <TimelineComponent
          dag={dag}
          tasks={tasks}
          onClick={() => {}}
          selectedTask={null}
        />
      );

      // Verify Timeline renders FORK_JOIN_DYNAMIC tasks without errors
      cy.get(".timeline-container").should("exist");
      cy.get(".vis-timeline").should("exist");
      cy.get(".vis-item").should("have.length.at.least", 1);
    });
  });

  it("Timeline zoom to fit button works", () => {
    cy.fixture("doWhile/doWhileSwitch").then((data) => {
      const dag = new WorkflowDAG(data);
      const tasks = data.tasks;

      mount(
        <TimelineComponent
          dag={dag}
          tasks={tasks}
          onClick={() => {}}
          selectedTask={null}
        />
      );

      // Test the zoom to fit functionality
      cy.get('button[aria-label="Zoom to Fit"]').should("exist").click();
    });
  });

  it("Timeline handles tasks with start/end times correctly", () => {
    cy.fixture("doWhile/doWhileSwitch").then((data) => {
      const dag = new WorkflowDAG(data);
      const tasks = data.tasks.filter((t) => t.startTime > 0 || t.endTime > 0);

      mount(
        <TimelineComponent
          dag={dag}
          tasks={tasks}
          onClick={() => {}}
          selectedTask={null}
        />
      );

      // Verify only tasks with valid timestamps are rendered in timeline
      cy.get(".vis-item").should("have.length", tasks.length);
    });
  });

  it("Timeline click handler is triggered", () => {
    const onClickSpy = cy.spy().as("onClickSpy");

    cy.fixture("doWhile/doWhileSwitch").then((data) => {
      const dag = new WorkflowDAG(data);
      const tasks = data.tasks;

      mount(
        <TimelineComponent
          dag={dag}
          tasks={tasks}
          onClick={onClickSpy}
          selectedTask={null}
        />
      );

      // Click on a timeline item
      cy.get(".vis-item").first().click();

      // Verify onClick handler was called
      cy.get("@onClickSpy").should("be.called");
    });
  });

  it("Do_while containing FORK_JOIN_DYNAMIC - renders without crashing", () => {
    cy.fixture("doWhile/doWhileForkJoinDynamic").then((data) => {
      const dag = new WorkflowDAG(data);
      const tasks = data.tasks;

      // This test verifies the fix for #534 - Timeline should not crash
      // when DO_WHILE contains FORK_JOIN_DYNAMIC tasks
      mount(
        <TimelineComponent
          dag={dag}
          tasks={tasks}
          onClick={() => {}}
          selectedTask={null}
        />
      );

      // Verify Timeline renders without errors
      cy.get(".timeline-container").should("exist");
      cy.get(".vis-timeline").should("exist");

      // Verify timeline items are rendered for multiple iterations
      cy.get(".vis-item").should("have.length.at.least", 1);
    });
  });

  it("Do_while containing FORK_JOIN_DYNAMIC - handles nested groups correctly", () => {
    cy.fixture("doWhile/doWhileForkJoinDynamic").then((data) => {
      const dag = new WorkflowDAG(data);
      const tasks = data.tasks;

      mount(
        <TimelineComponent
          dag={dag}
          tasks={tasks}
          onClick={() => {}}
          selectedTask={null}
        />
      );

      // Verify the fix: Timeline should handle FORK_JOIN_DYNAMIC tasks
      // inside DO_WHILE without trying to access undefined dfParent.ref
      cy.get(".timeline-container").should("exist");
      cy.get(".vis-labelset").should("exist");

      // Verify groups and items are created correctly
      cy.get(".vis-item").should("exist");
    });
  });
});
