package org.enso.interpreter.node.expression.literal;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.enso.interpreter.node.ExpressionNode;

public class StringLiteralNode extends ExpressionNode {
  private final String value;

  public StringLiteralNode(String value) {
    this.value = value;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return value;
  }
}
