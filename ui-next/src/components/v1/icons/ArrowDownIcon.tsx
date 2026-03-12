const ArrowDownIcon = ({ size = "20", color = "#000000" }) => {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 20 20"
      fill={color}
      xmlns="http://www.w3.org/2000/svg"
    >
      <path
        d="M5.29 7.71L6 7L10.15 11.15L14.29 7L15 7.71L10.5 12.21C10.3 12.41 9.99 12.41 9.79 12.21L5.29 7.71Z"
        fill={color}
      />
    </svg>
  );
};

export default ArrowDownIcon;
