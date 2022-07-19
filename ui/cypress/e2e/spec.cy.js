describe("Landing Page", () => {
  beforeEach(() => {
    cy.intercept("/api/workflow/search?**", { fixture: "workflowSearch.json" });
    cy.intercept("/api/tasks/search?**", { fixture: "taskSearch.json" });
    cy.intercept("/api/metadata/workflow", {
      fixture: "metadataWorkflow.json",
    });
    cy.intercept("/api/metadata/taskdefs", { fixture: "metadataTasks.json" });
  });

  it("Homepage preloads with default query", () => {
    cy.visit("/");
    cy.contains("Search Execution");
    cy.contains("Page 1 of 5");
    cy.get(".rdt_TableCell").contains("feature_value_compute_workflow");
  });

  it("Workflow name dropdown", () => {
    cy.get(".MuiAutocomplete-inputRoot input").first().click();
    cy.get("li.MuiAutocomplete-option")
      .contains("Do_While_Workflow_Iteration_Fix")
      .click();
    cy.get(".MuiAutocomplete-tag").contains("Do_While_Workflow_Iteration_Fix");
  });

  it("Switch to Task Tab - No results", () => {
    cy.get("a.MuiTab-root").contains("Tasks").click();
    cy.contains("Task Name");
    cy.contains("There are no records to display");
  });

  it("Task Name Dropdown", () => {
    cy.get(".MuiAutocomplete-inputRoot input").first().click();
    cy.get("li.MuiAutocomplete-option").contains("example_task_2").click();
    cy.get(".MuiAutocomplete-tag").contains("example_task_2");
  });

  it("Execute Task Search", () => {
    cy.get("button").contains("Search").click();
    cy.contains("Page 1 of 1");
    cy.get(".rdt_TableCell").contains("36d24c5c-9c26-46cf-9709-e1bc6963b8a5");
  });
});
