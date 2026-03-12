import { FormTaskType } from "types/TaskType";
import { TaskType } from "types/common";

type TaskDescriptions = Partial<Record<FormTaskType, string>>;

export const taskDescriptions: TaskDescriptions = {
  // system
  [TaskType.EVENT]:
    "EVENT is a task used to publish an event into one of the supported eventing systems in Conductor.",
  [TaskType.HTTP]:
    "HTTP task allows you to make calls to remote services exposed over HTTP/HTTPS.",
  [TaskType.HTTP_POLL]:
    "The HTTP_POLL is a conductor task used to invoke HTTP API until the specified condition matches.",
  [TaskType.JSON_JQ_TRANSFORM]:
    "The JSON_JQ_TRANSFORM task is a System task that allows the processing of JSON data that is supplied to the task by using the popular JQ processing tool’s query expression language.",
  [TaskType.INLINE]:
    "The inline task helps execute necessary logic at the workflow run-time using an evaluator. The two supported evaluator types are javascript and graaljs.",
  [TaskType.BUSINESS_RULE]:
    "Business rule task helps evaluate business rules compiled in spreadsheets.",
  [TaskType.SENDGRID]: "Send email using sendgrid",
  [TaskType.START_WORKFLOW]:
    "Start Workflow is an operator task used to start another workflow from an existing workflow. Unlike a sub-workflow task, a start workflow task doesn’t create a relationship between the current workflow and the newly started workflow. That means it doesn’t wait for the started workflow to get completed.",
  [TaskType.WAIT_FOR_WEBHOOK]:
    "Webhook is an HTTP-based callback function that facilitates the communication between the Conductor and other third-party systems. It can be used to receive data from other applications to the Conductor.",
  [TaskType.UPDATE_SECRET]:
    "A system task to update the value of any secret, given the user has permission to update the secret.",
  [TaskType.QUERY_PROCESSOR]:
    "A system task for executing queries across different systems, tailored for purposes like alert generation.",
  [TaskType.UPDATE_TASK]: "A system task to update the status of other tasks.",

  // operator

  [TaskType.SWITCH]:
    "The switch task is used for creating branching logic. It is a representation of multiple if...then...else or switch...case statements in programming.",
  [TaskType.DO_WHILE]:
    "The Do While task sequentially executes a list of tasks as long as a condition is true. The list of tasks is executed first before the condition is checked, even for the first iteration, just like a regular do .. while task in programming languages.",
  [TaskType.FORK_JOIN_DYNAMIC]:
    "A Fork/Join task can be used when you need to run tasks in parallel. It contains two components, the fork, and the join part. A fork operation lets you run a specified list of tasks in parallel. A fork task is followed by a join operation that waits on the forked tasks to finish. The JOIN task also collects outputs from each of the forked tasks.",
  [TaskType.DYNAMIC]:
    "The dynamic task allows us to execute one of the registered tasks dynamically at run-time. This means that you can run a task not fixed at the time of the workflow’s execution. The task name could even be supplied as part of the workflow’s input and be mapped to the dynamic task input.",
  [TaskType.TERMINATE]:
    "The Terminate task is a task that can terminate the current workflow with a termination status and reason.",
  [TaskType.SET_VARIABLE]:
    "Set Variable allows us to set the workflow variables by creating or updating them with new values. Think of these as a temporary state, which you can set in any step and refer back to any steps that execute after setting the value.",
  [TaskType.SUB_WORKFLOW]:
    "Sub Workflow allows executing another workflow from within the current workflow.",
  [TaskType.JOIN]:
    "A JOIN task is used in conjunction with a FORK_JOIN or FORK_JOIN_DYNAMIC task to join all the tasks within the forks.",
  [TaskType.WAIT]:
    "The Wait task is used when the workflow needs to be paused for an external signal to continue. It is used when the workflow needs to wait and pause for external signals, such as a human intervention (like manual approval) or an event coming from an external source, such as Kafka or SQS.",
  [TaskType.TERMINATE_WORKFLOW]:
    "The Terminate Workflow task is used to terminate other workflows using their workflow IDs.",
  [TaskType.HUMAN]:
    "Human tasks are used when you need to wait your workflow for an interaction with a human. When your workflow reaches the human task, it waits for a manual interaction to proceed with the workflow. It can be leveraged when you need manual approval from a human, such as when a form needs to be approved within an application, such as approval workflows.",
  [TaskType.GET_WORKFLOW]:
    "Get Workflow task is used to retrieve detail of workflow using workflow ID.",

  // worker
  [TaskType.JDBC]:
    "A JDBC task is a system task used to execute or store information in MySQL.",
  [TaskType.SIMPLE]:
    "A Simple task is a Worker task that requires an external worker for polling. The Workers can be implemented in any language, and Conductor SDKs provide additional features such as metrics, server communication, and polling threads that make the worker creation process easier.",

  // alerting
  [TaskType.OPS_GENIE]:
    "A system task to send alerts to Opsgenie in the event of workflow failures. This task can be used in conjunction with the Query Processor task, which fetches metadata details to trigger alerts to Opsgenie as required.",

  // ai/llm

  [TaskType.LLM_TEXT_COMPLETE]:
    "A system task to predict or generate the next phrase or words in a given text based on the context provided.",
  [TaskType.LLM_GENERATE_EMBEDDINGS]:
    "A system task to generate embeddings from the input data provided. Embeddings are the processed input text converted into a sequence of vectors, which can then be stored in a vector database for retrieval later. You can use a model that was previously integrated to generate these embeddings.",
  [TaskType.LLM_GET_EMBEDDINGS]:
    "A system task to get the numerical vector representations of words, phrases, sentences, or documents that have been previously learned or generated by the model. Unlike the process of generating embeddings (LLM Generate Embeddings task), which involves creating vector representations from input data, this task deals with the retrieval of pre-existing embeddings and uses them to search for data in vector databases.",
  [TaskType.LLM_STORE_EMBEDDINGS]:
    "A system task responsible for storing the generated embeddings produced by the LLM Generate Embeddings task, into a vector database. The stored embeddings serve as a repository of information that can be later accessed by the LLM Get Embeddings task for efficient and quick retrieval of related data.",
  [TaskType.LLM_SEARCH_INDEX]:
    "A system task to search the vector database or repository of vector embeddings of already processed and indexed documents to get the closest match. You can input a query that typically refers to a question, statement, or request made in natural language that is used to search, retrieve, or manipulate data stored in a database.",
  [TaskType.LLM_INDEX_DOCUMENT]:
    "A system task to index the provided document into a vector database that can be efficiently searched, retrieved, and processed later.",
  [TaskType.GET_DOCUMENT]:
    "A system task to retrieve the content of the document provided and use it for further data processing using AI tasks.",
  [TaskType.LLM_INDEX_TEXT]:
    "A system task to index the provided text into a vector space that can be efficiently searched, retrieved, and processed later.",
  [TaskType.LLM_CHAT_COMPLETE]:
    "A system task to complete the chat query. It can be used to instruct the model's behavior accurately to prevent any deviation from the objective.",
};
