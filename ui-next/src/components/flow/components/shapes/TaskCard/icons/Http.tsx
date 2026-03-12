import type { CustomIconType } from "./types";

function Icon({ size = "24", color = "" }: CustomIconType) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      fill={color}
      viewBox="0 0 256 256"
    >
      <rect width="256" height="256" fill="none"></rect>
      <circle
        cx="128"
        cy="128"
        r="96"
        fill="none"
        stroke={color}
        strokeMiterlimit="10"
        strokeWidth="16"
      ></circle>
      <line
        x1="37.5"
        y1="96"
        x2="218.5"
        y2="96"
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="16"
      ></line>
      <line
        x1="37.5"
        y1="160"
        x2="218.5"
        y2="160"
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="16"
      ></line>
      <ellipse
        cx="128"
        cy="128"
        rx="40"
        ry="93.4"
        fill="none"
        stroke={color}
        strokeMiterlimit="10"
        strokeWidth="16"
      ></ellipse>
    </svg>
  );
}

export default Icon;
