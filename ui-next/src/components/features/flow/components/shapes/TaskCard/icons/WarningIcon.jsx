function Icon({ size, color }) {
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
        strokeWidth="24"
        d="M128 104L128 144"
      ></path>
      <path
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="24"
        d="M114.2 40l-88 152A16 16 0 0040 216h176a16 16 0 0013.8-24l-88-152a15.9 15.9 0 00-27.6 0z"
      ></path>
      <circle cx="128" fill={color} cy="180" r="12"></circle>
    </svg>
  );
}

export default Icon;
