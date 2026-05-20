import { Breadcrumbs, BreadcrumbsProps, SxProps, Theme } from "@mui/material";
import Typography from "@mui/material/Typography";
import { styled } from "@mui/system";
import { Link } from "react-router";
import { blue15 } from "theme/tokens/colors";

type ConductorBreadcrumbsProps = BreadcrumbsProps & {
  items: any;
  color?: string;
};

const StyledLink = styled(Link)`
  text-decoration: none;
  color: ${(props) => (props.color ? props.color : blue15)};
  font-size: 12px;
  font-weight: 300;
  line-height: 16px;
  display: "flex",
  alignItems: "center",
`;

const typographyStyle: SxProps<Theme> = {
  fontSize: "12px",
  fontWeight: 300,
  color: (theme) => theme.palette.input.text,
  lineHeight: "16px",
  display: "flex",
  alignItems: "center",
};
const globalStyles: SxProps<Theme> = {
  ".MuiBreadcrumbs-separator": {
    color: "#161616",
    ".MuiSvgIcon-root": {
      fontSize: "28px",
    },
  },
};
const ConductorBreadcrumbs = ({
  items,
  color,
  ...rest
}: ConductorBreadcrumbsProps) => {
  return (
    <>
      <Breadcrumbs {...rest} sx={globalStyles}>
        {items &&
          items.map((item: any, index: number) =>
            index !== items.length - 1 ? (
              <StyledLink color={color} key={index} to={item.to}>
                {item.label}
                {item.icon}
              </StyledLink>
            ) : (
              <Typography key={index} sx={typographyStyle}>
                {item.label}
                {item.icon}
              </Typography>
            ),
          )}
      </Breadcrumbs>
    </>
  );
};

export type { ConductorBreadcrumbsProps };
export default ConductorBreadcrumbs;
