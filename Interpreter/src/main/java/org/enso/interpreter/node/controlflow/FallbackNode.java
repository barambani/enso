package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.callable.ExecuteCallNode;
import org.enso.interpreter.node.callable.ExecuteCallNodeGen;
import org.enso.interpreter.runtime.callable.atom.Atom;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.state.StateRef;

/**
 * This node represents an explicit catch-call case in a pattern match, as provided by the user. It
 * executes the catch-all case code.
 */
public class FallbackNode extends CaseNode {
  @Child private ExpressionNode functionNode;
  @Child private ExecuteCallNode executeCallNode = ExecuteCallNodeGen.create();

  /**
   * Creates a node to handle the case catch-call.
   *
   * @param functionNode the function to execute in this case
   */
  public FallbackNode(ExpressionNode functionNode) {
    this.functionNode = functionNode;
  }

  private void execute(VirtualFrame frame, StateRef stateRef, Object target)
      throws UnexpectedResultException {
    Function function = functionNode.executeFunction(frame);
    throw new BranchSelectedException(
        executeCallNode.executeCall(function, stateRef, new Object[0]));
  }

  /**
   * Executes the case expression catch-all case.
   *
   * <p>It has no direct return value and instead uses a {@link BranchSelectedException} to signal
   * the correct result back to the parent of the case expression.
   *
   * @param frame the stack frame in which to execute
   * @param target the constructor to destructure
   * @throws UnexpectedResultException when the result of desctructuring {@code target} can't be
   *     represented as a value of the expected return type
   */
  @Override
  public void executeAtom(VirtualFrame frame, StateRef stateRef, Atom target)
      throws UnexpectedResultException {
    execute(frame, stateRef, target);
  }

  /**
   * Executes the case expression catch-all case.
   *
   * <p>It has no direct return value and instead uses a {@link BranchSelectedException} to signal
   * the correct result back to the parent of the case expression.
   *
   * @param frame the stack frame in which to execute
   * @param target the constructor to destructure
   * @throws UnexpectedResultException when the result of desctructuring {@code target} can't be
   *     represented as a value of the expected return type
   */
  @Override
  public void executeFunction(VirtualFrame frame, StateRef stateRef, Function target)
      throws UnexpectedResultException {
    execute(frame, stateRef, target);
  }

  /**
   * Executes the case expression catch-all case.
   *
   * <p>It has no direct return value and instead uses a {@link BranchSelectedException} to signal
   * the correct result back to the parent of the case expression.
   *
   * @param frame the stack frame in which to execute
   * @param target the constructor to destructure
   * @throws UnexpectedResultException when the result of desctructuring {@code target} can't be
   *     represented as a value of the expected return type
   */
  @Override
  public void executeNumber(VirtualFrame frame, StateRef stateRef, long target)
      throws UnexpectedResultException {
    execute(frame, stateRef, target);
  }
}
