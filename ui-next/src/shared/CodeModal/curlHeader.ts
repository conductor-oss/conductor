export const curlHeaders = (accessToken: string) => ({
  Accept: "*/*",
  "X-Authorization": accessToken,
});
