package org.enso.interpreter.runtime.state;

public class StateRef {
  private Object stateVal;

  public StateRef() {
  }

  public Object getStateVal() {
    return stateVal;
  }

  public void setStateVal(Object stateVal) {
    this.stateVal = stateVal;
  }
}
