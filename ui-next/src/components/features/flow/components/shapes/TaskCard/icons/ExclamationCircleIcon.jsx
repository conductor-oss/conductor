// From phosphoricons
// rendering the svg directly for performance

function ExclamationCircleIcon({ size, color }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 256 256"
    >
      <path fill="none" d="M0 0H256V256H0z"></path>
      <circle
        cx="128"
        cy="128"
        r="96"
        fill="none"
        stroke={color}
        strokeMiterlimit="10"
        strokeWidth="16"
      ></circle>
      <path
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="16"
        d="M60.1 60.1L195.9 195.9"
      ></path>
    </svg>
  );
}

export default ExclamationCircleIcon;
