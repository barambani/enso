package org.enso.interpreter.node.callable.dispatch;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import org.enso.interpreter.node.callable.ExecuteCallNode;
import org.enso.interpreter.node.callable.ExecuteCallNodeGen;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.control.TailCallException;
import org.enso.interpreter.runtime.type.TypesGen;

/**
 * A version of {@link CallOptimiserNode} that is fully prepared to handle tail calls. Tail calls
 * are handled through exceptions – whenever a tail-recursive call would be executed, an exception
 * containing the next unevaluated call and arguments is thrown instead.
 *
 * <p>This node executes the function in a loop, following all the continuations, until obtaining
 * the actual return value.
 *
 * @see TailCallException
 */
public class LoopingCallOptimiserNode extends CallOptimiserNode {
  private final FrameDescriptor loopFrameDescriptor = new FrameDescriptor();
  @Child private LoopNode loopNode;

  /** Creates a new node for computing tail-call-optimised functions. */
  public LoopingCallOptimiserNode() {
    loopNode = Truffle.getRuntime().createLoopNode(new RepeatedCallNode(loopFrameDescriptor));
  }

  /**
   * Calls the provided {@code callable} using the provided {@code arguments}.
   *
   * @param callable the callable to execute
   * @param arguments the arguments to {@code callable}
   * @return the result of executing {@code callable} using {@code arguments}
   */
  @Override
  public Object executeDispatch(Function callable, Object[] arguments) {
    VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(null, loopFrameDescriptor);
    ((RepeatedCallNode) loopNode.getRepeatingNode())
        .setNextCall(frame, new TailCallException(callable, arguments));
    loopNode.executeLoop(frame);

    return ((RepeatedCallNode) loopNode.getRepeatingNode()).getResult(frame);
  }

  /**
   * This node handles the actually looping computation computed in a tail-recursive function. It
   * will execute the computation repeatedly and is intended to be used in the context of a Truffle
   * {@link RepeatingNode}.
   */
  public static final class RepeatedCallNode extends Node implements RepeatingNode {
    private final FrameSlot resultSlot;
    private final FrameSlot functionSlot;
    @Child private ExecuteCallNode dispatchNode;

    /**
     * Creates a new node used for repeating a call.
     *
     * @param descriptor a handle to the slots of the current stack frame
     */
    public RepeatedCallNode(FrameDescriptor descriptor) {
      functionSlot = descriptor.findOrAddFrameSlot("<TCO Function>", FrameSlotKind.Object);
      resultSlot = descriptor.findOrAddFrameSlot("<TCO Result>", FrameSlotKind.Object);
      dispatchNode = ExecuteCallNodeGen.create();
    }

    /**
     * Sets the call target for the next call made using {@code frame}.
     *
     * @param frame the stack frame for execution
     * @param function the function to execute in {@code frame}
     * @param arguments the arguments to execute {@code function} with
     */
    public void setNextCall(VirtualFrame frame, TailCallException e) {
      frame.setObject(functionSlot, e);
    }

    /**
     * Obtains the result of looping execution.
     *
     * @param frame the stack frame for execution
     * @return the result of execution in {@code frame}
     */
    public Object getResult(VirtualFrame frame) {
      return FrameUtil.getObjectSafe(frame, resultSlot);
    }

    /**
     * Generates the next call to be made during looping execution.
     *
     * @param frame the stack frame for execution
     * @return the function to be executed next in the loop
     */
    public TailCallException getNextFunction(VirtualFrame frame) {
      Object result = FrameUtil.getObjectSafe(frame, functionSlot);
      //      CompilerDirectives.ensureVirtualized(result);
      frame.setObject(functionSlot, null);
      return CompilerDirectives.castExact(result, TailCallException.class);
    }


    /**
     * Executes the node in a repeating fashion until the call is complete.
     *
     * @param frame the stack frame for execution
     * @return {@code true} if execution is continuing, {@code false} otherwise
     */
    @Override
    public boolean executeRepeating(VirtualFrame frame) {
      try {
        TailCallException function = getNextFunction(frame);
        //        Object[] arguments = getNextArgs(frame);
        frame.setObject(
            resultSlot, dispatchNode.executeCall(function.getFunction(), function.getArguments()));
        return false;
      } catch (TailCallException e) {
        setNextCall(frame, e);
        return true;
      }
    }
  }
}
