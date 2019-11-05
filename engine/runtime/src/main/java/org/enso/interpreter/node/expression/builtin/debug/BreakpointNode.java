package org.enso.interpreter.node.expression.builtin.debug;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.expression.builtin.BuiltinRootNode;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.callable.function.FunctionSchema;
import org.enso.interpreter.runtime.state.Stateful;

public abstract class BreakpointNode extends BuiltinRootNode {

  @GenerateWrapper
  public static class BreakpointInstrumentableNode extends Node implements InstrumentableNode {

    @Override
    public boolean isInstrumentable() {
      return true;
    }

    public Stateful execute(VirtualFrame frame) {
      return null;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
      return tag == DebuggerTags.AlwaysHalt.class;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probeNode) {
      return new BreakpointInstrumentableNodeWrapper(this, probeNode);
    }

    @Override
    public SourceSection getSourceSection() {
      return getRootNode().getSourceSection();
    }
  }

  private @Child BreakpointInstrumentableNode instrumentableNode =
      new BreakpointInstrumentableNode();

  public BreakpointNode(Language language) {
    super(language);
  }

  @Specialization
  public Stateful doNothing(VirtualFrame frame, @CachedContext(Language.class) Context context) {
    Object state = Function.ArgumentsHelper.getState(frame.getArguments());
    Stateful res =
        instrumentableNode.execute(
            Truffle.getRuntime()
                .getCallerFrame()
                .getFrame(FrameInstance.FrameAccess.MATERIALIZE)
                .materialize());
    return res;
  }

  public static Function makeFunction(Language language) {
    return Function.fromBuiltinRootNode(
        BreakpointNodeGen.create(language),
        FunctionSchema.CallStrategy.ALWAYS_DIRECT,
        new ArgumentDefinition(0, "this", ArgumentDefinition.ExecutionMode.EXECUTE));
  }
}
