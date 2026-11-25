// The diamond is painted as a single path with an explicit fill, and the drop shadow
// comes from a CSS filter function. Both are required for "export to image":
// dom-to-image inlines each element's computed style onto its clone, so a path that
// relied on inheriting `fill` from a <use> would be pinned to the initial value
// (black) and the whole node would export as a black diamond.
function StarShape() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="100%"
      height="100%"
      viewBox="0 0 278 276"
      preserveAspectRatio="none"
      style={{
        overflow: "visible",
        filter: "drop-shadow(0 0 4px rgba(0, 0, 0, .5))",
      }}
    >
      <path
        fill="#FFFFFF"
        transform="translate(-521 -302)"
        d="M674.079 313.089l113.137 113.137c4.668 4.588 7.118 9.272 7.35 14.053.231 4.78-2.219 9.525-7.351 14.232L674.077 567.648c-5.185 4.12-9.7 6.34-13.543 6.662-3.843.322-8.756-1.898-14.74-6.662L532.655 454.511c-4.73-6.095-7.14-11.2-7.237-15.313-.09-4.114 2.321-8.437 7.237-12.97L645.793 313.09c5.348-4.67 10.073-7.14 14.173-7.41 4.1-.271 8.804 2.198 14.113 7.409z"
      ></path>
    </svg>
  );
}

export default StarShape;
