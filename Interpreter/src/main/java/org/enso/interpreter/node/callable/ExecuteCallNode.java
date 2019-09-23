package org.enso.interpreter.node.callable;

import cats.data.Func;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.runtime.callable.function.Function;

/**
 * This node is responsible for optimising function calls.
 *
 * <p>Where possible, it will make the call as a 'direct' call, one with no lookup needed, but will
 * fall back to performing a lookup if necessary.
 */
public abstract class ExecuteCallNode extends Node {
  private @Child DirectCallNode firstDirect;
  private @CompilerDirectives.CompilationFinal int argCount;
  private @Child DirectCallNode secondDirect;
  private @Child IndirectCallNode indirect = Truffle.getRuntime().createIndirectCallNode();

  @Specialization
  protected Object call(Function function, Object[] argumentsX) {
    MaterializedFrame scope = function.getScope();
    CallTarget ct = function.getCallTarget();
    if (firstDirect == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      argCount = argumentsX.length;
    }

    argumentsX = cpArgs(argumentsX);

    if (firstDirect == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      argCount = argumentsX.length;
      firstDirect = Truffle.getRuntime().createDirectCallNode(ct);
      firstDirect.forceInlining();
      Object[] arguments = Function.ArgumentsHelper.buildArguments(scope, (argumentsX));

      return callFirst(arguments);
    } else if (firstDirect.getCallTarget() == ct) {
      argumentsX = cpArgs(argumentsX);
      Object[] arguments = Function.ArgumentsHelper.buildArguments(scope, (argumentsX));

      return callFirst(arguments);
    } else if (secondDirect == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      secondDirect = Truffle.getRuntime().createDirectCallNode(ct);
      secondDirect.forceInlining();
      Object[] arguments = Function.ArgumentsHelper.buildArguments(scope, (argumentsX));

      return callSecond(arguments);
    } else if (secondDirect.getCallTarget() == ct) {
      Object[] arguments = Function.ArgumentsHelper.buildArguments(scope, (argumentsX));

      return callSecond(arguments);
    } else {
      Object[] arguments = Function.ArgumentsHelper.buildArguments(scope, (argumentsX));

      //      throw new RuntimeException("I GIVE UP");
      return indirect.call(ct, arguments);
    }
  }

  private Object callFirst(Object[] arguments) {
    return firstDirect.call(arguments);
  }

  private Object callSecond(Object[] arguments) {
    return secondDirect.call(arguments);
  }

  @ExplodeLoop
  public Object[] cpArgs(Object[] args) {
    Object[] res = new Object[argCount];
    for (int i = 0; i < argCount; i++) {
      res[i] = args[i];
    }
    return res;
  }

  /**
   * Calls the function directly.
   *
   * <p>This specialisation comes into play where the call target for the provided function is
   * already cached. THis means that the call can be made quickly.
   *
   * @param function the function to execute
   * @param arguments the arguments passed to {@code function} in the expected positional order
   * @param cachedTarget the cached call target for {@code function}
   * @param callNode the cached call node for {@code cachedTarget}
   * @return the result of executing {@code function} on {@code arguments} //
   */
  //  @Specialization(guards = "function.getCallTarget() == cachedTarget", limit = "2")
  //  protected Object callDirect(
  //      Function function,
  //      Object[] arguments,
  //      @Cached("function.getCallTarget()") RootCallTarget cachedTarget,
  //      @Cached("create(cachedTarget)") DirectCallNode callNode) {
  //    return callNode.call(Function.ArgumentsHelper.buildArguments(function, arguments));
  //  }

  /**
   * Calls the function with a lookup.
   *
   * <p>This specialisation is used in the case where there is no cached call target for the
   * provided function. This is much slower and should, in general, be avoided.
   *
   * @param function the function to execute
   * @param arguments the arguments passed to {@code function} in the expected positional order
   * @param callNode the cached call node for making indirect calls
   * @return the result of executing {@code function} on {@code arguments}
   */
  //  @Specialization(replaces = "callDirect")
  //  protected Object callIndirect(
  //      Function function, Object[] arguments, @Cached IndirectCallNode callNode) {
  //    return callNode.call(
  //        function.getCallTarget(), Function.ArgumentsHelper.buildArguments(function, arguments));
  //  }

  /**
   * Executes the function call.
   *
   * @param function the function to execute
   * @param arguments the arguments to be passed to {@code function}
   * @return the result of executing {@code function} on {@code arguments}
   */
  public abstract Object executeCall(Function function, Object[] arguments);
}
