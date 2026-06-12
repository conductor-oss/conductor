import type { CustomIconType } from "./types";
function Icon({ size = "24", color = "#000000" }: CustomIconType) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      fill={color}
      viewBox="0 0 256 256"
    >
      <rect width="256" height="256" fill="none"></rect>
      <path
        d="M72,168v32a16,16,0,0,1-32,0"
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="16"
      ></path>
      <path
        d="M176,224h24a8,8,0,0,0,8-8V88L152,32H56a8,8,0,0,0-8,8v88"
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="16"
      ></path>
      <path
        d="M104,212a25.2,25.2,0,0,0,15,5c9,0,17-3,17-13,0-16-32-9-32-24,0-8,6-13,15-13a25.2,25.2,0,0,1,15,5"
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="16"
      ></path>
      <polyline
        points="152 32 152 88 208 88"
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="16"
      ></polyline>
    </svg>
  );
}

export default Icon;
