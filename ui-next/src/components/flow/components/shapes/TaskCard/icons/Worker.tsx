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
      <circle
        cx="152"
        cy="56"
        r="24"
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="16"
      ></circle>
      <path
        d="M56,101.6s32-29.6,80,8c50.5,39.4,80,24,80,24"
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="16"
      ></path>
      <path
        d="M135.1,108.8C130.7,129.2,101.6,207,32,200"
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="16"
      ></path>
      <path
        d="M110.6,161.2C128.5,165,176,180,176,232"
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
