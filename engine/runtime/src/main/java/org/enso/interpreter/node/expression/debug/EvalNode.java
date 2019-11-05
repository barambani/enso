package org.enso.interpreter.node.expression.debug;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.ClosureRootNode;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.expression.builtin.debug.EvalFunNode;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.scope.LocalScope;
import org.enso.interpreter.runtime.scope.ModuleScope;
import org.enso.interpreter.runtime.state.Stateful;

public class EvalNode extends Node {

    public static class Framed {
      private final MaterializedFrame frame;
      private final Stateful result;

      public Framed(MaterializedFrame frame, Stateful result) {
        this.frame = frame;
        this.result = result;
      }

      public MaterializedFrame getFrame() {
        return frame;
      }

      public Stateful getResult() {
        return result;
      }
    }

    private static class FrameLeakingRootNode extends ClosureRootNode {
      /**
       * Creates a new root node.
       *
       * @param language the language identifier
       * @param localScope
       * @param moduleScope
       * @param body the program body to be executed
       * @param section a mapping from {@code body} to the program source
       * @param name a name for the node
       */
      public FrameLeakingRootNode(
          Language language,
          LocalScope localScope,
          ModuleScope moduleScope,
          ExpressionNode body,
          SourceSection section,
          String name) {
        super(language, localScope, moduleScope, body, section, name);
      }

      @Override
      public Object execute(VirtualFrame frame) {
        return new Framed(frame.materialize(), (Stateful) super.execute(frame));
      }
    }

    private @Child
    IndirectCallNode indirectCallNode =
        Truffle.getRuntime().createIndirectCallNode();

    public Framed execute(
        VirtualFrame frame,
        Object state,
        LocalScope scope,
        ModuleScope moduleScope,
        String expression) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      Language language = lookupLanguageReference(Language.class).get();
      Context context = lookupContextReference(Language.class).get();
      ExpressionNode expr =
          lookupContextReference(Language.class)
              .get()
              .compiler()
              .runInline(expression, language, scope, moduleScope);
      RootNode framedNode =
          new FrameLeakingRootNode(
              lookupLanguageReference(Language.class).get(),
              scope,
              moduleScope,
              expr,
              null,
              "interactive");
      CallTarget ct = Truffle.getRuntime().createCallTarget(framedNode);
      return (Framed) indirectCallNode.call(ct, frame.materialize(), state, new Object[0]);
    }
  }
