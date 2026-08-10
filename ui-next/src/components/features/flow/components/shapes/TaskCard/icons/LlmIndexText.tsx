function Icon({ size = "24", color = "currentColor" }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 18 16"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      <path d="M18 0H0V1H18V0Z" fill={color} />
      <path d="M5.69 3H0V4H4.54C4.87 3.62 5.27 3.28 5.69 3Z" fill={color} />
      <path
        d="M12.3096 3C12.7296 3.28 13.1296 3.62 13.4596 4H17.9996V3H12.3096Z"
        fill={color}
      />
      <path d="M0 7H3.09C3.14 6.65 3.23 6.32 3.35 6H0V7Z" fill={color} />
      <path
        d="M14.9104 7H18.0004V6H14.6504C14.7704 6.32 14.8604 6.65 14.9104 7Z"
        fill={color}
      />
      <path d="M0 10H3.35C3.23 9.68 3.14 9.35 3.09 9H0V10Z" fill={color} />
      <path
        d="M14.6504 10H18.0004V9H14.9104C14.8604 9.35 14.7704 9.68 14.6504 10Z"
        fill={color}
      />
      <path d="M0 13H5.69C5.27 12.73 4.87 12.39 4.54 12H0V13Z" fill={color} />
      <path
        d="M12.3096 13H17.9996V12H13.4596C13.1296 12.39 12.7296 12.73 12.3096 13Z"
        fill={color}
      />
      <path d="M15 15H0V16H15V15Z" fill={color} />
      <path d="M10 8H8V11H10V8Z" fill={color} />
      <path
        d="M8.99969 7.33995C9.58969 7.33995 10.0697 6.85995 10.0697 6.26995C10.0697 5.67995 9.58969 5.19995 8.99969 5.19995C8.40969 5.19995 7.92969 5.67995 7.92969 6.26995C7.92969 6.85995 8.40969 7.33995 8.99969 7.33995Z"
        fill={color}
      />
      <path
        d="M14 8C14 5.24 11.76 3 9 3C6.24 3 4 5.24 4 8C4 10.76 6.24 13 9 13C11.76 13 14 10.76 14 8ZM5 8C5 5.79 6.79 4 9 4C11.21 4 13 5.79 13 8C13 10.21 11.21 12 9 12C6.79 12 5 10.21 5 8Z"
        fill={color}
      />
    </svg>
  );
}

export default Icon;
