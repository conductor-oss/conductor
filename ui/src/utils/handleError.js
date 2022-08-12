export default function handleError(res){
  return Promise.all([res, res.text()])
  .then(([res, text]) => {
    if (!res.ok) {
      // get error message from body or default to response status
      const error = text || res.status;
      return Promise.reject(error);
    } else if (!text || text.length === 0) {
      return null;
    } else {
      try {
        return JSON.parse(text);
      } catch (e) {
        return text;
      }
    }
  });
}