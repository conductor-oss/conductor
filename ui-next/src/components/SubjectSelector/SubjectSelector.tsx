import { FunctionComponent, useMemo } from "react";
import { SubjectMultiPicker } from "./SubjectMultiPicker";
import { SelectableOption, SelectableOptionType } from "./types";
import { AccessGroup, User } from "types";
import { Application } from "types/Application";
import { displayUserSubject } from "./helpers";

type SubjectSelectorBaseParentProps = {
  label?: string;
  selectableUsers: User[];
  selectableGroups: AccessGroup[];
  selectableApplications: Application[];
  growPopper?: boolean;
};

type SubjectSelectorMultipleBaseProps = SubjectSelectorBaseParentProps & {
  multiple: true;
  onChange: (value: SelectableOption | SelectableOption[]) => void;
  selectedSubjectsValue: string[];
};

type SubjectSelectorSingleBaseProps = SubjectSelectorBaseParentProps & {
  multiple: false;
  onChange: (value: SelectableOption | SelectableOption[]) => void;
  selectedSubjectsValue?: string;
};

export const SubjectSelectorBase: FunctionComponent<
  SubjectSelectorMultipleBaseProps | SubjectSelectorSingleBaseProps
> = ({
  label,
  selectableUsers,
  selectableGroups,
  selectableApplications,
  onChange,
  selectedSubjectsValue,
  multiple,
  growPopper,
}) => {
  const options = useMemo((): SelectableOption[] => {
    return selectableUsers
      .map(
        (user: User): SelectableOption => ({
          display: displayUserSubject(user),
          id: user.id,
          value: `${user.id}`,
          type: SelectableOptionType.USER,
        }),
      )
      .concat(
        selectableGroups.map(
          (group: AccessGroup): SelectableOption => ({
            display: group.id,
            id: group.id,
            value: `${group.id}`,
            type: SelectableOptionType.GROUP,
          }),
        ),
      )
      .concat(
        selectableApplications.map(
          (application: Application): SelectableOption => ({
            display: application.name,
            id: application.id,
            value: `USER:app:${application.id}`,
            type: SelectableOptionType.APPLICATION,
          }),
        ),
      );
  }, [selectableUsers, selectableGroups, selectableApplications]);

  const value = useMemo((): SelectableOption[] | SelectableOption => {
    if (multiple === false) {
      const foundElement = options.find(
        ({ value }) => value === selectedSubjectsValue,
      );
      if (foundElement) {
        return foundElement;
      }
      // Support for free solo
      return {
        value: selectedSubjectsValue,
        id: selectedSubjectsValue,
        display: selectedSubjectsValue,
      } as SelectableOption;
    }

    const [users, groups, applications] = Array.isArray(selectedSubjectsValue)
      ? selectedSubjectsValue.reduce(
          (
            acc: [string[], string[], string[]],
            c: string,
          ): [string[], string[], string[]] => {
            const [accUsers, accGroups, accApplications] = acc;
            if (c.includes("USER:app:")) {
              return [
                accUsers,
                accGroups,
                accApplications.concat(c.replace(/^USER:app:/, "")),
              ];
            }
            if (c.includes("CONDUCTOR_USER:")) {
              return [
                accUsers.concat(c.replace(/^CONDUCTOR_USER:/, "")),
                accGroups,
                accApplications,
              ];
            }
            if (c.includes("CONDUCTOR_GROUP:")) {
              return [
                accUsers,
                accGroups.concat(c.replace(/^CONDUCTOR_GROUP:/, "")),
                accApplications,
              ];
            }
            return acc;
          },
          [[], [], []],
        )
      : [[], [], []];

    return options.filter(({ id, type }) => {
      if (type === SelectableOptionType.USER) {
        return users.includes(id);
      }
      if (type === SelectableOptionType.GROUP) {
        return groups.includes(id);
      }
      if (type === SelectableOptionType.APPLICATION) {
        return applications.includes(id);
      }
      throw new Error("Unexpected type: ", type);
    });
  }, [options, selectedSubjectsValue, multiple]);

  return (
    <SubjectMultiPicker
      multiple={multiple}
      label={label ? label : ""}
      options={options}
      onChange={onChange}
      value={value}
      growPopper={growPopper}
    />
  );
};
