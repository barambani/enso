package org.enso.interpreter.node.callable;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.callable.argument.ThunkExecutorNode;
import org.enso.interpreter.node.callable.argument.ThunkExecutorNodeGen;
import org.enso.interpreter.runtime.callable.argument.Thunk;
import org.enso.interpreter.runtime.callable.function.Function;

/** Node responsible for handling user-requested thunks forcing. */
@NodeChild(value = "target", type = ExpressionNode.class)
public abstract class ForceNode extends ExpressionNode {
  @Specialization
  Object passToExecutorNode(
      VirtualFrame frame,
      Thunk thunk,
      @Cached("build(isTail())") ThunkExecutorNode thunkExecutorNode) {
    return thunkExecutorNode.executeThunk(
        thunk, Function.ArgumentsHelper.getStateRef(frame.getArguments()));
  }
}
