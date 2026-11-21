package jx.conformance;

import jx.conformance.cases.AttributesCase;
import jx.conformance.cases.BatchedUpdates;
import jx.conformance.cases.CallableState;
import jx.conformance.cases.CallbackIdentity;
import jx.conformance.cases.CheckboxCase;
import jx.conformance.cases.ChildValues;
import jx.conformance.cases.ChildrenProp;
import jx.conformance.cases.Conditional;
import jx.conformance.cases.ContextNested;
import jx.conformance.cases.ControlledInput;
import jx.conformance.cases.DomRefFocus;
import jx.conformance.cases.EffectCleanup;
import jx.conformance.cases.EffectDependencies;
import jx.conformance.cases.ErrorBoundaryCase;
import jx.conformance.cases.ExternalStoreCase;
import jx.conformance.cases.FragmentCase;
import jx.conformance.cases.KeyEvent;
import jx.conformance.cases.KeyedList;
import jx.conformance.cases.LayoutEffectOrder;
import jx.conformance.cases.MemoRecompute;
import jx.conformance.cases.PreventDefault;
import jx.conformance.cases.PropsUpdate;
import jx.conformance.cases.ReducerBatch;
import jx.conformance.cases.RefPersist;
import jx.conformance.cases.RemountByKey;
import jx.conformance.cases.StateCounter;
import jx.conformance.cases.StateLazyInit;
import jx.conformance.cases.StopPropagation;
import jx.conformance.cases.SvgCase;
import jx.conformance.cases.TextEscaping;
import jx.conformance.cases.UseId;
import jx.conformance.cases.CustomHook;
import jx.conformance.cases.FunctionProps;
import jx.conformance.cases.DynamicComponent;
import jx.conformance.cases.NamespaceComponents;
import jx.conformance.cases.ChoiceInputs;
import jx.conformance.cases.UncontrolledInputs;
import jx.conformance.cases.CapturePhase;
import jx.conformance.cases.CustomElements;
import jx.conformance.cases.DocumentMetadata;
import jx.conformance.cases.ResourceElements;
import jx.conformance.cases.ErrorReporting;
import jx.conformance.cases.PointerEvents;
import jx.conformance.cases.TransferEvents;
import jx.conformance.cases.MediaEvents;
import jx.conformance.cases.EventPayload;
import jx.conformance.cases.LayoutEffectDependencies;
import jx.conformance.cases.MemoSkip;
import jx.react.React;

/** Mounts exactly one bench case, chosen by the {@code case} query parameter, under Strict Mode. */
public final class Main {
    private Main() {}

    public static void main(String[] arguments) {
        String id = Bench.caseId();
        switch (id) {
            case "state-counter" -> React.mountStrict("app", StateCounter.JX, null);
            case "state-lazy-init" -> React.mountStrict("app", StateLazyInit.JX, null);
            case "callable-state" -> React.mountStrict("app", CallableState.JX, null);
            case "reducer-batch" -> React.mountStrict("app", ReducerBatch.JX, null);
            case "effect-cleanup" -> React.mountStrict("app", EffectCleanup.JX, null);
            case "effect-dependencies" -> React.mountStrict("app", EffectDependencies.JX, null);
            case "layout-effect-order" -> React.mountStrict("app", LayoutEffectOrder.JX, null);
            case "batched-updates" -> React.mountStrict("app", BatchedUpdates.JX, null);
            case "ref-persist" -> React.mountStrict("app", RefPersist.JX, null);
            case "dom-ref-focus" -> React.mountStrict("app", DomRefFocus.JX, null);
            case "context-nested" -> React.mountStrict("app", ContextNested.JX, null);
            case "memo-recompute" -> React.mountStrict("app", MemoRecompute.JX, null);
            case "callback-identity" -> React.mountStrict("app", CallbackIdentity.JX, null);
            case "use-id" -> React.mountStrict("app", UseId.JX, null);
            case "external-store" -> React.mountStrict("app", ExternalStoreCase.JX, null);
            case "error-boundary" -> React.mountStrict("app", ErrorBoundaryCase.JX, null);
            case "controlled-input" -> React.mountStrict("app", ControlledInput.JX, null);
            case "checkbox" -> React.mountStrict("app", CheckboxCase.JX, null);
            case "key-event" -> React.mountStrict("app", KeyEvent.JX, null);
            case "prevent-default" -> React.mountStrict("app", PreventDefault.JX, null);
            case "stop-propagation" -> React.mountStrict("app", StopPropagation.JX, null);
            case "keyed-list" -> React.mountStrict("app", KeyedList.JX, null);
            case "remount-by-key" -> React.mountStrict("app", RemountByKey.JX, null);
            case "props-update" -> React.mountStrict("app", PropsUpdate.JX, null);
            case "conditional" -> React.mountStrict("app", Conditional.JX, null);
            case "fragment" -> React.mountStrict("app", FragmentCase.JX, null);
            case "children-prop" -> React.mountStrict("app", ChildrenProp.JX, null);
            case "text-escaping" -> React.mountStrict("app", TextEscaping.JX, null);
            case "child-values" -> React.mountStrict("app", ChildValues.JX, null);
            case "attributes" -> React.mountStrict("app", AttributesCase.JX, null);
            case "svg" -> React.mountStrict("app", SvgCase.JX, null);
            case "custom-hook" -> React.mountStrict("app", CustomHook.JX, null);
            case "function-props" -> React.mountStrict("app", FunctionProps.JX, null);
            case "dynamic-component" -> React.mountStrict("app", DynamicComponent.JX, null);
            case "namespace-components" -> React.mountStrict("app", NamespaceComponents.JX, null);
            case "choice-inputs" -> React.mountStrict("app", ChoiceInputs.JX, null);
            case "uncontrolled-inputs" -> React.mountStrict("app", UncontrolledInputs.JX, null);
            case "capture-phase" -> React.mountStrict("app", CapturePhase.JX, null);
            case "custom-elements" -> React.mountStrict("app", CustomElements.JX, null);
            case "document-metadata" -> React.mountStrict("app", DocumentMetadata.JX, null);
            case "resource-elements" -> React.mountStrict("app", ResourceElements.JX, null);
            case "error-reporting" -> React.mountStrict("app", ErrorReporting.JX, null);
            case "pointer-events" -> React.mountStrict("app", PointerEvents.JX, null);
            case "transfer-events" -> React.mountStrict("app", TransferEvents.JX, null);
            case "media-events" -> React.mountStrict("app", MediaEvents.JX, null);
            case "event-payload" -> React.mountStrict("app", EventPayload.JX, null);
            case "layout-effect-dependencies" -> React.mountStrict("app", LayoutEffectDependencies.JX, null);
            case "memo-skip" -> React.mountStrict("app", MemoSkip.JX, null);
            default -> Bench.unknown(id);
        }
    }
}
