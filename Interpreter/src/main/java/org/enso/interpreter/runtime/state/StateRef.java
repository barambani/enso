package org.enso.interpreter.runtime.state;

import com.oracle.truffle.api.CompilerDirectives;

@CompilerDirectives.ValueType
public class StateRef {
  private Object stateVal;

  public StateRef() {
    this(null);
  }

  public StateRef(Object val) {
    stateVal = val;
  }

  public Object getStateVal() {
    return stateVal;
  }

  public void setStateVal(Object stateVal) {
    this.stateVal = stateVal;
  }
}
