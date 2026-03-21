const ArrowUpIcon = ({ size = "20", color = "#000000" }) => {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 20 20"
      fill={color}
      xmlns="http://www.w3.org/2000/svg"
    >
      <path
        d="M14.71 12.29L14 13L9.85 8.85L5.71 13L5 12.29L9.5 7.79C9.7 7.59 10.01 7.59 10.21 7.79L14.71 12.29Z"
        fill={color}
      />
    </svg>
  );
};

export default ArrowUpIcon;
