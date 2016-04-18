import inspect
import sys
def maybe_enrich_frame(frame):
    if not is_dag_frame(frame):
        return frame
    if isinstance(frame, DAGFrame):
        return frame
    dag_frame = DAGFrame(frame)
    return dag_frame







def is_dag_frame(frame):
    if frame and frame.f_locals:
        locals = frame.f_locals
        if '_NODE_' in locals:
            return True
    return False

class DAGFrame:
    def __init__(self, frame):
        self.f_code = frame.f_code
        self.f_lineno = frame.f_lineno
        self.f_back = frame.f_back
        self.f_globals = frame.f_globals
        self.f_locals = self.create_locals(frame)
        self.f_trace = None



    def _argNames(self, node):
        # TODO: wish there was a simpler way to get the argument names for a cellnode
        source = inspect.getsource(node.desc.onCalc)
        source = source[:source.index(')')]
        return [x.strip() for x in source.split(',')][1:]

    def _argInputValue(self, node, n):
         return NoInput() if len(node.args) <= n else node.args[n]

    def create_locals(self, frame):
        locals = frame.f_locals
        node = locals.get('_NODE_')
        try:
            if node:
                for n,arg in enumerate(self._argNames(node)):
                    locals[arg] = self._argInputValue(node, n)

                locals['self'] = node.obj
        except Exception as e:
            sys.stderr.write('Debugger: could not add argument values to locals: %s' % e)
        return locals

class NoInput(object):
    """
    A special marker type used in the Debug variables tab for missing argument inputs
    """
    def __repr__(self):
        return 'This cell function argument was optimized away and is now the direct input to another cell input'

