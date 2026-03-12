import AdminPanelSettingsOutlinedIcon from "@mui/icons-material/AdminPanelSettingsOutlined";
import CancelOutlinedIcon from "@mui/icons-material/CancelOutlined";
import ManageAccountsOutlinedIcon from "@mui/icons-material/ManageAccountsOutlined";
import PersonOutlineOutlinedIcon from "@mui/icons-material/PersonOutlineOutlined";
import VisibilityIcon from "@mui/icons-material/Visibility";
import { AutocompleteRenderGetTagProps, Chip } from "@mui/material";
import { SvgIconProps } from "@mui/material/SvgIcon";
import { roleLabel } from "utils/roles";
import { ComponentType } from "react";
import { greyText } from "theme/tokens/colors";

interface RoleConfig {
  [key: string]: {
    icon: ComponentType<SvgIconProps>;
    color: string;
    chipBackground: string;
  };
}

export const roleConfig: RoleConfig = {
  ADMIN: {
    icon: AdminPanelSettingsOutlinedIcon,
    color: "rgba(64, 186, 86, 1)",
    chipBackground: "rgba(159, 220, 170, 1)",
  },
  USER: {
    icon: PersonOutlineOutlinedIcon,
    color: "#9157FF",
    chipBackground: "rgba(200, 171, 255, 1)",
  },
  WORKFLOW_MANAGER: {
    icon: ManageAccountsOutlinedIcon,
    color: "rgba(27, 194, 244, 1)",
    chipBackground: "rgba(141, 224, 249, 1)",
  },
  METADATA_MANAGER: {
    icon: ManageAccountsOutlinedIcon,
    color: "rgba(251, 164, 4, 1)",
    chipBackground: "rgba(252, 209, 129, 1)",
  },
  USER_READ_ONLY: {
    icon: VisibilityIcon,
    color: greyText,
    chipBackground: "rgba(221, 221, 221, 1)",
  },
};

export const getOptionIcon = (roleName: string) => {
  const role = roleConfig[roleName];
  if (!role) {
    return null;
  }
  const { icon: IconComponent, color } = role;
  return <IconComponent sx={{ color }} />;
};

export const renderRoleTags = (
  value: string[],
  getTagProps: AutocompleteRenderGetTagProps,
) =>
  value.map((option, index) => {
    const { chipBackground } = roleConfig[option] || {};
    const { key, ...otherTagProps } = getTagProps({ index });
    return (
      <Chip
        key={key}
        label={roleLabel[option] || option}
        {...otherTagProps}
        deleteIcon={<CancelOutlinedIcon />}
        sx={{
          backgroundColor: chipBackground,
          color: "#000",
          borderRadius: "30px",
          "& .MuiSvgIcon-root": {
            background: "transparent",
            fill: "black",
          },
        }}
      />
    );
  });
