#!/usr/bin/env python3
"""Regression tests for repository-root snippet expansion."""

from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("generate-llm-context.py")
SPEC = importlib.util.spec_from_file_location("generate_llm_context", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class SnippetExpansionTest(unittest.TestCase):
    def test_expands_nested_repository_root_includes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "nested.txt").write_text("nested\n", encoding="utf-8")
            (root / "fixture.txt").write_text(
                'before\n--8<-- "nested.txt"\nafter\n', encoding="utf-8"
            )
            source = root / "page.md"
            source.write_text('', encoding="utf-8")
            actual = MODULE.expand_snippets(
                '--8<-- "fixture.txt"\n', source, root=root
            )
            self.assertEqual("before\nnested\nafter", actual)

    def test_rejects_missing_include(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "page.md"
            source.write_text('', encoding="utf-8")
            with self.assertRaises(FileNotFoundError):
                MODULE.expand_snippets('--8<-- "missing.json"\n', source, root=root)

    def test_rejects_out_of_root_include(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "page.md"
            source.write_text('', encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "escapes repository root"):
                MODULE.expand_snippets('--8<-- "../outside.json"\n', source, root=root)

    def test_rejects_cycles(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = root / "first.md"
            second = root / "second.md"
            first.write_text('--8<-- "second.md"\n', encoding="utf-8")
            second.write_text('--8<-- "first.md"\n', encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "cyclic snippet include"):
                MODULE.expand_snippets(first.read_text(), first, root=root)


if __name__ == "__main__":
    unittest.main()
