import { Link as LinkIcon, Key as KeyIcon } from "@phosphor-icons/react";

const KAFKATask = ({ nodeData }) => {
  const { task } = nodeData;
  const request = task.inputParameters?.kafka_request;
  const requestKey = request?.key || {};

  return (
    <div style={{ marginTop: "20px" }}>
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          width: "100%",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            width: "100%",
          }}
        >
          <LinkIcon color="#aaaaaa" style={{ marginRight: "5px" }} />
          <div
            style={{
              padding: "0 8px",
              lineHeight: "2em",
              overflow: "hidden",
              textOverflow: "ellipsis",
              wordBreak: "keep-all",
              whiteSpace: "nowrap",
            }}
          >
            {request?.bootStrapServers}
          </div>
        </div>
        {Object.entries(requestKey)?.map(([key, value], index) =>
          index === 0 ? (
            <div
              key={index}
              style={{
                display: "flex",
                alignItems: "center",
                width: "100%",
              }}
            >
              <KeyIcon color="#aaaaaa" style={{ marginRight: "5px" }} />
              <div
                style={{
                  padding: "0 8px",
                  lineHeight: "2em",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  wordBreak: "keep-all",
                  whiteSpace: "nowrap",
                }}
              >
                {`${key}: ${value}`}
              </div>
            </div>
          ) : null,
        )}
      </div>
    </div>
  );
};

export default KAFKATask;
