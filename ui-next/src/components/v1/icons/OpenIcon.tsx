import { CustomIconType } from "components/flow/components/shapes/TaskCard/icons/types";

const OpenIcon = ({ size = 16, ...props }: CustomIconType) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 14 14"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    {...props}
  >
    <g clipPath="url(#clip0_2837_6480)">
      <path
        d="M13 8H14V12.2C14 13.19 13.19 14 12.2 14H1.8C0.81 14 0 13.19 0 12.2V1.8C0 0.81 0.81 0 1.8 0H6V1H1.8C1.36 1 1 1.36 1 1.8V12.2C1 12.64 1.36 13 1.8 13H12.2C12.64 13 13 12.64 13 12.2V8ZM7.79 0L10.19 2.4L3.29 9.3L4.7 10.71L11.6 3.81L14 6.21V0H7.79Z"
        fill="currentColor"
      />
    </g>
    <defs>
      <clipPath id="clip0_2837_6480">
        <rect width={size} height={size} fill="currentColor" />
      </clipPath>
    </defs>
  </svg>
);

export default OpenIcon;
