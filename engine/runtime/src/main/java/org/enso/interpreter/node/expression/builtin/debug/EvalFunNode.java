package org.enso.interpreter.node.expression.builtin.debug;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.EnsoRootNode;
import org.enso.interpreter.node.expression.builtin.BuiltinRootNode;
import org.enso.interpreter.node.expression.debug.EvalNode;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.callable.function.FunctionSchema;
import org.enso.interpreter.runtime.scope.LocalScope;
import org.enso.interpreter.runtime.scope.ModuleScope;
import org.enso.interpreter.runtime.state.Stateful;

public class EvalFunNode extends BuiltinRootNode {
  private @Child EvalNode evalNode = new EvalNode();

  public EvalFunNode(Language language) {
    super(language);
  }

  @Override
  public Stateful execute(VirtualFrame frame) {
    FrameInstance callerFrame = Truffle.getRuntime().getCallerFrame();
    EnsoRootNode callerRootNode = (EnsoRootNode) callerFrame.getCallNode().getRootNode();
    LocalScope localScope = callerRootNode.getLocalScope();
    ModuleScope moduleScope = callerRootNode.getModuleScope();
    return evalNode
        .execute(
            callerFrame.getFrame(FrameInstance.FrameAccess.MATERIALIZE).materialize(),
            Function.ArgumentsHelper.getState(frame.getArguments()),
            new LocalScope(localScope),
            moduleScope,
            (String) Function.ArgumentsHelper.getPositionalArguments(frame.getArguments())[1])
        .getResult();
  }

  public static Function makeFunction(Language language) {
    return Function.fromBuiltinRootNode(
        new EvalFunNode(language),
        FunctionSchema.CallStrategy.DIRECT_WHEN_TAIL,
        new ArgumentDefinition(0, "this", ArgumentDefinition.ExecutionMode.EXECUTE),
        new ArgumentDefinition(1, "expression", ArgumentDefinition.ExecutionMode.EXECUTE));
  }
}
