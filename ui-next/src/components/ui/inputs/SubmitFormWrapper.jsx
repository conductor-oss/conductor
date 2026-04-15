export default function SubmitFormWrapper({ onSubmit, children }) {
  return (
    <form
      onSubmit={(event) => {
        onSubmit();
        event.preventDefault();
        return false;
      }}
    >
      {children}
    </form>
  );
}
