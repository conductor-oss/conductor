import { isEmptyIterable } from "./helpers";

class Path {
  constructor(pathname) {
    this.search = new URLSearchParams();
    this.pathname = pathname;
  }

  toString() {
    return (
      this.pathname +
      (isEmptyIterable(this.search) ? "" : `?${this.search.toString()}`)
    );
  }
}

export default Path;
