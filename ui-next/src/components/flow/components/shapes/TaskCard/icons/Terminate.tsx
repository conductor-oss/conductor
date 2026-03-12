import type { CustomIconType } from "./types";
function TerminateIcon({ size, color = "#000000" }: CustomIconType) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 384 512"
      width={size}
      // Keep aspect
      height={Number(size) * (512 / 484)}
      fill={color}
    >
      <path d="M360 431.1H24c-13.25 0-24 10.76-24 24.02C0 469.2 10.75 480 24 480h336c13.25 0 24-10.76 24-24.02 0-13.28-10.7-24.88-24-24.88zm-57.5-223.4l-86.5 92V56.1c0-13.34-10.7-24.1-24-24.1s-24 10.76-24 24.02v243.6L81.47 207.7c-4.72-5.1-11.09-7.6-17.47-7.6-5.906 0-11.81 2.158-16.44 6.536-9.656 9.069-10.12 24.27-1.031 33.93l128 136.1c9.062 9.694 25.88 9.694 34.94 0l128-136.1c9.094-9.663 8.625-24.86-1.031-33.93C326.8 197.5 311.6 197.1 302.5 207.7z"></path>
    </svg>
  );
}

export default TerminateIcon;
