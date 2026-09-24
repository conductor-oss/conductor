import json
import unittest

from conductor.ai.agents.config_serializer import AgentConfigSerializer
from jev_agent import create_agent


class AgentTest(unittest.TestCase):
    def test_agent_uses_server_tool_without_worker_or_credentials(self):
        definition = create_agent("openai/configured-model")
        config = AgentConfigSerializer().serialize(definition)
        tool = definition.tools[0]
        self.assertIsNone(tool.func)
        self.assertEqual([], tool.credentials)
        self.assertEqual("decision_model", config["tools"][0]["toolType"])
        self.assertEqual("jev", tool.config["provider"])
        self.assertEqual({"state"}, set(tool.input_schema["properties"]))
        self.assertNotIn("apiKey", json.dumps(config))
        self.assertEqual("openai/configured-model", config["model"])


if __name__ == "__main__":
    unittest.main()
