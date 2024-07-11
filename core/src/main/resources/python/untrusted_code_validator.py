### This script contains code to check if the code to run in inline_task
### is safe and not vulnerable
### This is being executed by @PythonEvaluator.java
### Trusted code must not have import statements and some restricted built_in functions

import ast

class ConductorBuiltInRestrictor(ast.NodeVisitor):
    restricted_builtins = {
        'eval', 'exec', 'compile', 'execfile', 'del' ,'open', 'close', 'read', 'write', 'close', 'readlines', 'input', 'raw_input', 'open', '__import__', '__file__','__package__'
        ,'__path__','__spec__','__doc__','__module__','__loader__','__annotations__','__builtins__','__cached__','__build_class__', 'getattr',
        'setattr', 'delattr', 'globals', 'locals', 'vars', 'dir', 'type', 'id',
        'help', 'super', 'object', 'staticmethod', 'classmethod', 'property',
        'basestring', 'bytearray', 'bytes', 'callable', 'classmethod', 'complex',
        'delattr', 'dict', 'enumerate', 'eval', 'filter', 'frozenset', 'getattr',
        'globals', 'hasattr', 'hash', 'help', 'id', 'input', 'isinstance',
        'issubclass', 'iter', 'locals', 'map', 'memoryview',
        'next', 'object', 'property', 'repr', 'reversed',
        'setattr', 'sorted', 'staticmethod', 'vars', 'zip', 'reload', 'exit', 'quit'
    }

    def visit_Import(self, node):
        raise ImportError("Import statements are not allowed.")

    def visit_ImportFrom(self, node):
        raise ImportError("Import statements are not allowed.")

    def visit_Call(self, node):
        if isinstance(node.func, ast.Name) and node.func.id in self.restricted_builtins:
            raise ImportError("Usage of '%s' is not allowed." % node.func.id)
        self.generic_visit(node)

    def isCodeTrusted(self, code):
        try:
            tree = ast.parse(code)
            self.visit(tree)
            return True
        except ImportError as e:
           return False

restrictor = ConductorBuiltInRestrictor()
codeTrusted = restrictor.isCodeTrusted(${code})  # Should raise an ImportError
codeTrusted