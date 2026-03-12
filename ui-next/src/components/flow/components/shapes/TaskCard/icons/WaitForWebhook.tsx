import type { CustomIconType } from "./types";
function Icon({ size, color = "#000000" }: CustomIconType) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      fill={color}
      viewBox="0 0 256 256"
    >
      <rect width="256" height="256" fill="none"></rect>
      <line
        x1="128"
        y1="80"
        x2="128"
        y2="128"
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="16"
      ></line>
      <line
        x1="169.6"
        y1="152"
        x2="128"
        y2="128"
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="16"
      ></line>
      <polyline
        points="184.2 99.7 224.2 99.7 224.2 59.7"
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="16"
      ></polyline>
      <path
        d="M190.2,190.2a88,88,0,1,1,0-124.4l34,33.9"
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="16"
      ></path>
    </svg>
  );
}

export default Icon;
