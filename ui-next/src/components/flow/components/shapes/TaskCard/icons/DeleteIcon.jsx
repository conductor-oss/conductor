// From phosphoricons
// rendering the svg directly for performance

function DeleteIcon({ size = 24, color = "#000" }) {
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
        d="M200 56L56 200"
      ></path>
      <path
        fill="none"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="24"
        d="M200 200L56 56"
      ></path>
    </svg>
  );
}

export default DeleteIcon;
