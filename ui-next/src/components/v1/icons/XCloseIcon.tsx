import { CustomIconType } from "components/flow/components/shapes/TaskCard/icons/types";

const XCloseIcon = ({ size = 20, ...props }: CustomIconType) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 20 20"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    {...props}
  >
    <path
      d="M13.85 7.57L11.42 10L13.85 12.43L12.44 13.84L10.01 11.41L7.58 13.84L6.17 12.43L8.6 10L6.17 7.57L7.58 6.16L10.01 8.59L12.44 6.16L13.85 7.57ZM18 10C18 14.41 14.41 18 10 18C5.59 18 2 14.41 2 10C2 5.59 5.59 2 10 2C14.41 2 18 5.59 18 10ZM17 10C17 6.14 13.86 3 10 3C6.14 3 3 6.14 3 10C3 13.86 6.14 17 10 17C13.86 17 17 13.86 17 10Z"
      fill="currentColor"
    />
  </svg>
);

export default XCloseIcon;
