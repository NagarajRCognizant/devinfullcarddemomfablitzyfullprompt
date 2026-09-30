/** The WS-INFO-MSG and WS-RETURN-MSG lines of the maps. */
export function Messages({ info, error }: { info?: string | null; error?: string | null }) {
  return (
    <>
      {info ? (
        <p className="message message--info" role="status">
          {info}
        </p>
      ) : null}
      {error ? (
        <p className="message message--error" role="alert">
          {error}
        </p>
      ) : null}
    </>
  );
}
