"""A minimal but real A2A agent on the official a2a-sdk.

AGENT_MODE=task (default) returns a Task with an artifact; AGENT_MODE=message returns a
direct message. Run: AGENT_MODE=task python echo_agent.py
"""
import os
import uvicorn
from a2a.server.apps import A2AStarletteApplication
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.tasks import InMemoryTaskStore, TaskUpdater
from a2a.server.agent_execution import AgentExecutor, RequestContext
from a2a.server.events import EventQueue
from a2a.types import AgentCapabilities, AgentCard, AgentSkill, Part, TextPart
from a2a.utils import new_agent_text_message, new_task

MODE = os.environ.get("AGENT_MODE", "task")
PORT = int(os.environ.get("A2A_AGENT_PORT", "9999"))


class EchoAgentExecutor(AgentExecutor):
    async def execute(self, context: RequestContext, event_queue: EventQueue) -> None:
        user_text = context.get_user_input() or ""
        if MODE == "message":
            await event_queue.enqueue_event(new_agent_text_message(f"echo: {user_text}"))
            return
        task = context.current_task
        if task is None:
            task = new_task(context.message)
            await event_queue.enqueue_event(task)
        updater = TaskUpdater(event_queue, task.id, task.context_id)
        await updater.start_work()
        await updater.add_artifact(
            [Part(root=TextPart(text=f"echo-task: {user_text}"))], name="echo"
        )
        await updater.complete()

    async def cancel(self, context: RequestContext, event_queue: EventQueue) -> None:
        raise Exception("cancel not supported")


def build_app():
    skill = AgentSkill(
        id="echo", name="Echo", description="Echoes the user's input",
        tags=["test"], examples=["hello"],
    )
    card = AgentCard(
        name="Echo Agent", description="A tiny real A2A echo agent",
        url=f"http://localhost:{PORT}/", version="1.0.0",
        defaultInputModes=["text"], defaultOutputModes=["text"],
        capabilities=AgentCapabilities(streaming=True), skills=[skill],
    )
    handler = DefaultRequestHandler(
        agent_executor=EchoAgentExecutor(), task_store=InMemoryTaskStore()
    )
    return A2AStarletteApplication(agent_card=card, http_handler=handler)


if __name__ == "__main__":
    uvicorn.run(build_app().build(), host="127.0.0.1", port=PORT, log_level="warning")
