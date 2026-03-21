export enum SelectableOptionType {
  USER = "user",
  GROUP = "group",
  APPLICATION = "application",
}

export type SelectableOption = {
  display: string;
  id: string;
  value: string;
  type: SelectableOptionType;
};
