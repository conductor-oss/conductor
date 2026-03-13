export function parseApiMethod(str: string) {
  const match = str?.match(/^\[(\w+)](.+)/);
  if (match) {
    const [_, methodType, methodName] = match;
    return { methodName, methodType };
  }
  return { methodName: "", methodType: "" }; // Return empty object if the string doesn't match the format
}
