package org.enso.interpreter.node;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.state.StateRef;

public class InitializeStateNode extends ExpressionNode {
  private @Child ExpressionNode expr;

  public InitializeStateNode(ExpressionNode expr) {
    this.expr = expr;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    StateRef stateRef = new StateRef();
    CompilerDirectives.ensureVirtualized(stateRef);
    return expr.executeGeneric(
        Truffle.getRuntime()
            .createVirtualFrame(
                Function.ArgumentsHelper.buildArguments(stateRef, new Object[0]),
                frame.getFrameDescriptor()));
  }
}
