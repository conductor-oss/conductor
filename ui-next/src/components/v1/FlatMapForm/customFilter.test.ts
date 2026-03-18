import { customFilterOptions } from "./formOptions";

const options = [
  "GET",
  "POST",
  "${workflow.input}",
  "${workflow.secrets}",
  "${workflow.env}",
  "${workflow.output}",
  "${workflow.env.name}",
  "${workflow.env.testing}",
  "${workflow.env.golfClub}",
  "${workflow.env.company}",
];
const optionsWithDollar = [
  "${workflow.input}",
  "${workflow.secrets}",
  "${workflow.env}",
  "${workflow.output}",
  "${workflow.env.name}",
  "${workflow.env.testing}",
  "${workflow.env.golfClub}",
  "${workflow.env.company}",
];
const optionsWithInputText = [
  "${workflow.env}",
  "${workflow.env.name}",
  "${workflow.env.testing}",
  "${workflow.env.golfClub}",
  "${workflow.env.company}",
];
const inputvalueWithDollar = "GET$";
const inputvalueWithIncompleteVariable = "GET${workflow.env";

describe("customFilterOptions", () => {
  it("return all options start with $", () => {
    const result = customFilterOptions(options as any, inputvalueWithDollar);
    expect(result).toEqual(optionsWithDollar);
  });
  it("return all options start with ${workflow.env", () => {
    const result = customFilterOptions(
      options as any,
      inputvalueWithIncompleteVariable,
    );
    expect(result).toEqual(optionsWithInputText);
  });
});
