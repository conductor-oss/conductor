import { mount } from "cypress/react";
import WorkflowDAG from "./WorkflowDAG";
import WorkflowGraph from "./WorkflowGraph";

describe("<WorkflowGraph>", () => {
  it("Dynamic Fork - success", () => {
    const onClickSpy = cy.spy().as("onClickSpy");
    cy.fixture("dynamicFork/success").then((data) => {
      const dag = new WorkflowDAG(data);
      mount(
        <WorkflowGraph dag={dag} executionMode={true} onClick={onClickSpy} />
      );
      cy.get("#dynamic_tasks_DF_TASK_PLACEHOLDER")
        .should("contain", "3 of 3 tasks succeeded")
        .click();

      cy.get("@onClickSpy").should("be.calledWith", { ref: "first_task" });
    });
  });

  it("Dynamic Fork - one task failed", () => {
    const onClickSpy = cy.spy().as("onClickSpy");

    cy.fixture("dynamicFork/oneFailed").then((data) => {
      const dag = new WorkflowDAG(data);
      mount(
        <WorkflowGraph dag={dag} executionMode={true} onClick={onClickSpy} />
      );
      cy.get("#dynamic_tasks_DF_TASK_PLACEHOLDER")
        .should("contain", "2 of 3 tasks succeeded")
        .should("have.class", "status_FAILED")
        .click();

      cy.get("@onClickSpy").should("be.calledWith", { ref: "first_task" });
    });
  });

  it("Dynamic Fork - externalized input", () => {
    const onClickSpy = cy.spy().as("onClickSpy");

    cy.fixture("dynamicFork/externalizedInput").then((data) => {
      const dag = new WorkflowDAG(data);
      mount(
        <WorkflowGraph dag={dag} executionMode={true} onClick={onClickSpy} />
      );
      cy.get("#dynamic_tasks_DF_TASK_PLACEHOLDER")
        .should("contain", "3 of 3 tasks succeeded")
        .click();

      cy.get("@onClickSpy").should("be.calledWith", { ref: "first_task" });
    });
  });

  it("Dynamic Fork - not executed", () => {
    cy.fixture("dynamicFork/notExecuted").then((data) => {
      const dag = new WorkflowDAG(data);
      mount(<WorkflowGraph dag={dag} executionMode={true} />);
      cy.get("#dynamic_tasks_DF_EMPTY_PLACEHOLDER")
        .should("have.class", "dimmed")
        .should("contain", "Dynamically spawned tasks");
    });
  });

  it("Dynamic Fork - none spawned", () => {
    const onClickSpy = cy.spy().as("onClickSpy");

    cy.fixture("dynamicFork/noneSpawned").then((data) => {
      const dag = new WorkflowDAG(data);
      mount(
        <WorkflowGraph dag={dag} executionMode={true} onClick={onClickSpy} />
      );
      cy.get("#dynamic_tasks_DF_EMPTY_PLACEHOLDER")
        .should("contain", "No tasks spawned")
        .click();

      cy.get("@onClickSpy").should("not.be.called");
    });
  });

  it("Do_while containing switch (definition)", () => {
    const onClickSpy = cy.spy().as("onClickSpy");

    cy.fixture("doWhile/doWhileSwitch").then((data) => {
      const dag = new WorkflowDAG(null, data.workflowDefinition);
      mount(
        <WorkflowGraph dag={dag} executionMode={false} onClick={onClickSpy} />
      );

      cy.get(".edgePaths .edgePath.reverse").should("exist");
      cy.get(".edgePaths").find(".edgePath").should("have.length", 11);
      cy.get(".edgeLabels").should("contain", "LOOP");
    });
  });

  // Note: The addition of task 'inline_task_outside' tests prefix-based loop content detection.
  // Will succeed only when filtering via 'prefix + "__"';
  it("Do_while containing switch (execution)", () => {
    const onClickSpy = cy.spy().as("onClickSpy");

    cy.fixture("doWhile/doWhileSwitch").then((data) => {
      const dag = new WorkflowDAG(data);
      mount(
        <WorkflowGraph dag={dag} executionMode={true} onClick={onClickSpy} />
      );

      cy.get("#LoopTask_DF_TASK_PLACEHOLDER")
        .should("contain", "2 of 2 tasks succeeded")
        .click();

      cy.get("@onClickSpy").should("be.calledWith", { ref: "inline_task__1" });
      cy.get(".edgePaths").find(".edgePath").should("have.length", 6);
      cy.get(".edgePaths .edgePath.reverse").should("exist");
      cy.get(".edgeLabels").should("contain", "LOOP");
    });
  });
});
