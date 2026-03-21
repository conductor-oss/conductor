import { CustomIconType } from "components/flow/components/shapes/TaskCard/icons/types";

const PlayIcon = ({ size = 20, ...props }: CustomIconType) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 20 20"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    {...props}
  >
    <path
      d="M8.66 6.55C8.75 6.51 8.85 6.52 8.92 6.58L12.96 9.81C13.02 9.86 13.05 9.93 13.05 10.01C13.05 10.09 13.02 10.16 12.96 10.21L8.92 13.44C8.84 13.5 8.74 13.51 8.66 13.47C8.57 13.43 8.52 13.34 8.52 13.24L8.52 6.78C8.52 6.68 8.58 6.6 8.66 6.55ZM10 2C14.41 2 18 5.59 18 10C18 14.41 14.41 18 10 18C5.59 18 2 14.41 2 10C2 5.59 5.59 2 10 2ZM10 3C6.14 3 3 6.14 3 10C3 13.86 6.14 17 10 17C13.86 17 17 13.86 17 10C17 6.14 13.86 3 10 3Z"
      fill="currentColor"
    />
  </svg>
);

export default PlayIcon;
