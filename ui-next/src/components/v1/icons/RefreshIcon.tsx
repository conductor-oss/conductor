import { CustomIconType } from "components/flow/components/shapes/TaskCard/icons/types";

const RefreshIcon = ({ size = 20, ...props }: CustomIconType) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 20 20"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    {...props}
  >
    <g clipPath="url(#clip0_2363_4366)">
      <path
        d="M3 10.01H2C2 5.6 5.59 2.01 10 2.01C11.9 2.01 13.69 2.67 15.13 3.87L17 2V6.5H12.5L14.42 4.58C13.19 3.57 11.64 3.01 10 3.01C6.14 3.01 3 6.15 3 10.01ZM16.99 10.01C16.99 13.87 13.85 17.01 9.99 17.01C8.35 17.01 6.8 16.45 5.57 15.44L7.49 13.52H3V18.02L4.87 16.15C6.31 17.35 8.1 18.01 10 18.01C14.41 18.01 18 14.42 18 10.01H17H16.99Z"
        fill="currentColor"
      />
    </g>
    <defs>
      <clipPath id="clip0_2363_4366">
        <rect
          width="15.99"
          height="16.01"
          fill="white"
          transform="translate(2 2)"
        />
      </clipPath>
    </defs>
  </svg>
);

export default RefreshIcon;
