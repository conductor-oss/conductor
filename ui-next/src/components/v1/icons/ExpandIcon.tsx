const ExpandIcon = ({ size = 21, color = "#ffffff" }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill={color}
    xmlns="http://www.w3.org/2000/svg"
  >
    <path
      d="M3 7V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H7"
      stroke="black"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M17 3H19C19.5304 3 20.0391 3.21071 20.4142 3.58579C20.7893 3.96086 21 4.46957 21 5V7"
      stroke="black"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M21 17V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H17"
      stroke="black"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M7 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V17"
      stroke="black"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M16 8H8C7.44772 8 7 8.44772 7 9V15C7 15.5523 7.44772 16 8 16H16C16.5523 16 17 15.5523 17 15V9C17 8.44772 16.5523 8 16 8Z"
      stroke="black"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export default ExpandIcon;
