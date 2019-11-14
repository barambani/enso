package org.enso.interpreter.instrument;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.ClosureRootNode;
import org.enso.interpreter.node.expression.builtin.debug.EvalFunNode;
import org.enso.interpreter.node.expression.debug.EvalNode;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.scope.FramePointer;
import org.enso.interpreter.runtime.scope.LocalScope;
import org.enso.interpreter.runtime.scope.ModuleScope;
import org.enso.interpreter.runtime.state.Stateful;
import org.enso.syntax.text.ast.meta.Pattern;
import scala.reflect.internal.pickling.UnPickler;

import java.io.PrintStream;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

@TruffleInstrument.Registration(id = "enso-repl", services = ReplDebuggerInstrument.class)
public class ReplDebuggerInstrument extends TruffleInstrument {

  Env env;

  @Override
  protected void onCreate(Env env) {
    this.env = env;
//    SourceSectionFilter filter =
//        SourceSectionFilter.newBuilder().tagIs(DebuggerTags.AlwaysHalt.class).build();
//    Instrumenter instrumenter = env.getInstrumenter();
//    env.registerService(this);
//    instrumenter.attachExecutionEventFactory(filter, ctx -> new MyExecutionEventListener(ctx, env));
  }

//  static class MyExecutionEventListener extends ExecutionEventNode {
//    private final Env env;
//    private @Child EvalNode evalNode = new EvalNode();
//    private Object lastReturn;
//    private Object lastState;
//    private EventContext eventContext;
//
//    public MyExecutionEventListener(EventContext eventContext, Env env) {
//      this.env = env;
//      this.eventContext = eventContext;
//    }
//
//    private Object getValue(MaterializedFrame frame, FramePointer ptr) {
//      return getProperFrame(frame, ptr).getValue(ptr.getFrameSlot());
//    }
//
//    private MaterializedFrame getProperFrame(MaterializedFrame frame, FramePointer ptr) {
//      MaterializedFrame currentFrame = frame;
//      for (int i = 0; i < ptr.getParentLevel(); i++) {
//        currentFrame = getParentFrame(currentFrame);
//      }
//      return currentFrame;
//    }
//
//    private MaterializedFrame getParentFrame(Frame frame) {
//      return Function.ArgumentsHelper.getLocalScope(frame.getArguments());
//    }
//
//    private void runREPLLoop(
//        MaterializedFrame frame, LocalScope localScope, ModuleScope moduleScope) {
//      Scanner scanner = new Scanner(env.in());
//      PrintStream prt = new PrintStream(env.out());
//
//      while (true) {
//        FrameDescriptor fd = frame.getFrameDescriptor();
//        prt.print(">>> ");
//        String[] cmd = scanner.nextLine().trim().split(" ");
//        if (cmd.length == 0) {
//          continue;
//        }
//        if (cmd[0].equals(":continue")) {
//          throw eventContext.createUnwind(lastReturn);
//        } else if (cmd[0].equals(":list")) {
//          Map<String, FramePointer> binds = localScope.flatten();
//          for (String name : binds.keySet()) {
//            prt.println(name + " = " + getValue(frame, binds.get(name)));
//          }
//          //          for (Object ident : idents) {
//          //            FrameSlot fs = fd.findFrameSlot(ident);
//          //            prt.println(ident + ": " + frame.getValue(fs));
//          //          }
//        } else if (cmd.length == 3 && cmd[0].equals(":set")) {
//          String vName = cmd[1];
//          Long vVal = Long.valueOf(cmd[2]);
//          FrameSlot fs = fd.findFrameSlot(vName);
//          if (fs != null) {
//            frame.setLong(fs, vVal);
//          } else {
//            prt.println("No such variable: " + vName);
//          }
//        } else {
//          String expr = String.join(" ", cmd);
//          LocalScope newLocalScope = new LocalScope(localScope);
//          try {
//            EvalNode.Framed result =
//                evalNode.execute(frame, lastState, newLocalScope, moduleScope, expr);
//            frame = result.getFrame();
//            lastState = result.getResult().getState();
//            localScope = newLocalScope;
//            lastReturn = result.getResult().getValue();
//            prt.println("> " + result.getResult().getValue());
//          } catch (Exception e) {
//            prt.println("Error: " + e.getMessage());
//          }
//        }
//      }
//    }
//
//    @Override
//    public void onEnter(VirtualFrame fr) {
//      System.out.println("Entering debug instrument!");
//      lastReturn = lookupContextReference(Language.class).get().getUnit().newInstance();
//      ClosureRootNode rootNode =
//          (ClosureRootNode) Truffle.getRuntime().getCallerFrame().getCallNode().getRootNode();
//      lastState = fr.getValue(rootNode.getStateFrameSlot());
//
//      runREPLLoop(fr.materialize(), rootNode.getLocalScope(), rootNode.getModuleScope());
//    }
//
//    @Override
//    protected Object onUnwind(VirtualFrame frame, Object info) {
//      return new Stateful(lastState, lastReturn);
//    }
//
//    @Override
//    public void onReturnValue(VirtualFrame frame, Object result) {
//      System.out.println("Leaving debug instrument.");
//    }
//
//    //    @Override
//    //    public void onReturnExceptional(
//    //        EventContext context, VirtualFrame frame, Throwable exception) {}
//  }
}
