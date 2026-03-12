import type { CustomIconType } from "./types";
// From phosphoricons
// rendering the svg directly for performance

function LoopIcon({ color = "#000000", size = "24" }: CustomIconType) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 256 256"
    >
      <path fill="none" d="M0 0H256V256H0z"></path>
      <path
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="12"
        d="M176.2 99.7L224.2 99.7 224.2 51.7"
      ></path>
      <path
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="12"
        d="M65.8 65.8a87.9 87.9 0 01124.4 0l34 33.9"
      ></path>
      <path
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="12"
        d="M79.8 156.3L31.8 156.3 31.8 204.3"
      ></path>
      <path
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="12"
        d="M190.2 190.2a87.9 87.9 0 01-124.4 0l-34-33.9"
      ></path>
    </svg>
  );
}

export default LoopIcon;
