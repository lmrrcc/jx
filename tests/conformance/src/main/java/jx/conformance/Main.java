package jx.conformance;

import jx.conformance.cases.AttributesCase;
import jx.conformance.cases.BatchedUpdates;
import jx.conformance.cases.CallableState;
import jx.conformance.cases.CallbackIdentity;
import jx.conformance.cases.CheckboxCase;
import jx.conformance.cases.ChildValues;
import jx.conformance.cases.ChildrenProp;
import jx.conformance.cases.Conditional;
import jx.conformance.cases.EmptyRender;
import jx.conformance.cases.MemoScalars;
import jx.conformance.cases.CombinedKeyedContext;
import jx.conformance.cases.CombinedActivityStore;
import jx.conformance.cases.CombinedChildrenMap;
import jx.conformance.cases.CombinedChildrenList;
import jx.conformance.cases.CombinedChildrenContract;
import jx.conformance.cases.CombinedKeyedRefs;
import jx.conformance.cases.CombinedImperativeHandles;
import jx.conformance.cases.CombinedPortalLifecycle;
import jx.conformance.cases.CombinedActionQueue;
import jx.conformance.cases.CombinedOptimisticStore;
import jx.conformance.cases.CombinedTransitionCancel;
import jx.conformance.cases.CombinedResourceHydration;
import jx.conformance.cases.ClassRuntime;
import jx.conformance.cases.ClassKeyed;
import jx.conformance.cases.ClassContextCase;
import jx.conformance.cases.ClassDerivedProps;
import jx.conformance.cases.ClassErrorLifecycle;
import jx.conformance.cases.ClassAuthoring;
import jx.conformance.cases.ClassLifecycleAuthoring;
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
import jx.conformance.cases.ImperativeHandle;
import jx.conformance.cases.ForwardRef;
import jx.conformance.cases.RefAsProp;
import jx.conformance.cases.RefCallbackCase;
import jx.conformance.cases.CreateRef;
import jx.conformance.cases.StrictNested;
import jx.conformance.cases.InsertionEffect;
import jx.conformance.cases.EffectEvent;
import jx.conformance.cases.DebugValue;
import jx.conformance.cases.ProfilerCase;
import jx.conformance.cases.ReducerLazyInit;
import jx.conformance.cases.SpreadProps;
import jx.conformance.cases.DefaultProps;
import jx.conformance.cases.GenericList;
import jx.conformance.cases.Portal;
import jx.conformance.cases.FlushSync;
import jx.conformance.cases.ChildrenUtilities;
import jx.conformance.cases.CloneElement;
import jx.conformance.cases.IsValidElement;
import jx.conformance.cases.ChildSlots;
import jx.conformance.cases.TransitionPending;
import jx.conformance.cases.StartTransition;
import jx.conformance.cases.DeferredValue;
import jx.conformance.cases.UsePromise;
import jx.conformance.cases.AsyncFunction;
import jx.conformance.cases.TransitionPromise;
import jx.conformance.cases.LazyComponent;
import jx.conformance.cases.ActivityCase;
import jx.conformance.cases.ViewTransitionCase;
import jx.conformance.cases.Optimistic;
import jx.conformance.cases.ActionStateCase;
import jx.conformance.cases.FormStatusCase;
import jx.conformance.cases.FormActions;
import jx.conformance.cases.FormReset;
import jx.conformance.cases.RawHtmlCase;
import jx.conformance.cases.ResourceHints;
import jx.conformance.cases.FormStateCase;
import jx.conformance.cases.RootOptionsCase;
import jx.conformance.cases.HydrateRoot;
import jx.conformance.cases.HydratedPage;
import jx.conformance.cases.DomElements;
import jx.conformance.cases.DomAttributes;
import jx.conformance.cases.DomEvents;
import jx.react.React;
import jx.conformance.cases.RuntimeUtilities;

/** Mounts exactly one bench case, chosen by the {@code case} query parameter, under Strict Mode. */
public final class Main {
    private Main() {}

    /** The component of a case that mounts in the ordinary way, or null. Cases with a root of
     * their own are handled in {@link #main}. */
    static jx.ComponentType<?> component(String id) {
        return switch (id) {
            case "runtime-utilities" -> RuntimeUtilities.JX;
            case "combined-portal-lifecycle" -> CombinedPortalLifecycle.JX;
            case "state-counter" -> StateCounter.JX;
            case "state-lazy-init" -> StateLazyInit.JX;
            case "callable-state" -> CallableState.JX;
            case "reducer-batch" -> ReducerBatch.JX;
            case "effect-cleanup" -> EffectCleanup.JX;
            case "effect-dependencies" -> EffectDependencies.JX;
            case "layout-effect-order" -> LayoutEffectOrder.JX;
            case "batched-updates" -> BatchedUpdates.JX;
            case "ref-persist" -> RefPersist.JX;
            case "dom-ref-focus" -> DomRefFocus.JX;
            case "context-nested" -> ContextNested.JX;
            case "memo-recompute" -> MemoRecompute.JX;
            case "callback-identity" -> CallbackIdentity.JX;
            case "use-id" -> UseId.JX;
            case "external-store" -> ExternalStoreCase.JX;
            case "error-boundary" -> ErrorBoundaryCase.JX;
            case "controlled-input" -> ControlledInput.JX;
            case "checkbox" -> CheckboxCase.JX;
            case "key-event" -> KeyEvent.JX;
            case "prevent-default" -> PreventDefault.JX;
            case "stop-propagation" -> StopPropagation.JX;
            case "keyed-list" -> KeyedList.JX;
            case "remount-by-key" -> RemountByKey.JX;
            case "props-update" -> PropsUpdate.JX;
            case "conditional" -> Conditional.JX;
            case "empty-render" -> EmptyRender.JX;
            case "memo-scalars" -> MemoScalars.JX;
            case "fragment" -> FragmentCase.JX;
            case "children-prop" -> ChildrenProp.JX;
            case "text-escaping" -> TextEscaping.JX;
            case "child-values" -> ChildValues.JX;
            case "attributes" -> AttributesCase.JX;
            case "svg" -> SvgCase.JX;
            case "custom-hook" -> CustomHook.JX;
            case "function-props" -> FunctionProps.JX;
            case "dynamic-component" -> DynamicComponent.JX;
            case "namespace-components" -> NamespaceComponents.JX;
            case "choice-inputs" -> ChoiceInputs.JX;
            case "uncontrolled-inputs" -> UncontrolledInputs.JX;
            case "capture-phase" -> CapturePhase.JX;
            case "custom-elements" -> CustomElements.JX;
            case "document-metadata" -> DocumentMetadata.JX;
            case "resource-elements" -> ResourceElements.JX;
            case "error-reporting" -> ErrorReporting.JX;
            case "pointer-events" -> PointerEvents.JX;
            case "transfer-events" -> TransferEvents.JX;
            case "media-events" -> MediaEvents.JX;
            case "event-payload" -> EventPayload.JX;
            case "layout-effect-dependencies" -> LayoutEffectDependencies.JX;
            case "memo-skip" -> MemoSkip.JX;
            case "imperative-handle" -> ImperativeHandle.JX;
            case "forward-ref" -> ForwardRef.JX;
            case "ref-as-prop" -> RefAsProp.JX;
            case "ref-callback" -> RefCallbackCase.JX;
            case "create-ref" -> CreateRef.JX;
            case "strict-nested" -> StrictNested.JX;
            case "insertion-effect" -> InsertionEffect.JX;
            case "effect-event" -> EffectEvent.JX;
            case "debug-value" -> DebugValue.JX;
            case "profiler" -> ProfilerCase.JX;
            case "reducer-lazy-init" -> ReducerLazyInit.JX;
            case "spread-props" -> SpreadProps.JX;
            case "default-props" -> DefaultProps.JX;
            case "generic-list" -> GenericList.JX;
            case "portal" -> Portal.JX;
            case "flush-sync" -> FlushSync.JX;
            case "children-utilities" -> ChildrenUtilities.JX;
            case "clone-element" -> CloneElement.JX;
            case "is-valid-element" -> IsValidElement.JX;
            case "child-slots" -> ChildSlots.JX;
            case "transition-pending" -> TransitionPending.JX;
            case "start-transition" -> StartTransition.JX;
            case "deferred-value" -> DeferredValue.JX;
            case "use-promise" -> UsePromise.JX;
            case "async-function" -> AsyncFunction.JX;
            case "transition-promise" -> TransitionPromise.JX;
            case "lazy-component" -> LazyComponent.JX;
            case "activity" -> ActivityCase.JX;
            case "view-transition" -> ViewTransitionCase.JX;
            case "optimistic" -> Optimistic.JX;
            case "action-state" -> ActionStateCase.JX;
            case "form-status" -> FormStatusCase.JX;
            case "form-actions" -> FormActions.JX;
            case "form-reset" -> FormReset.JX;
            case "raw-html" -> RawHtmlCase.JX;
            case "resource-hints" -> ResourceHints.JX;
            case "form-state" -> FormStateCase.JX;
            case "dom-elements" -> DomElements.JX;
            case "dom-attributes" -> DomAttributes.JX;
            case "dom-events" -> DomEvents.JX;
            case "combined-keyed-context" -> CombinedKeyedContext.JX;
            case "combined-activity-store" -> CombinedActivityStore.JX;
            case "combined-children-map" -> CombinedChildrenMap.JX;
            case "combined-children-list" -> CombinedChildrenList.JX;
            case "combined-children-contract" -> CombinedChildrenContract.JX;
            case "combined-keyed-refs" -> CombinedKeyedRefs.JX;
            case "combined-imperative-handles" -> CombinedImperativeHandles.JX;
            case "combined-action-queue" -> CombinedActionQueue.JX;
            case "combined-optimistic-store" -> CombinedOptimisticStore.JX;
            case "combined-transition-cancel" -> CombinedTransitionCancel.JX;
            case "combined-resource-hydration" -> CombinedResourceHydration.JX;
            case "class-runtime" -> ClassRuntime.JX;
            case "class-keyed" -> ClassKeyed.JX;
            case "class-context" -> ClassContextCase.JX;
            case "class-derived-props" -> ClassDerivedProps.JX;
            case "class-error-lifecycle" -> ClassErrorLifecycle.JX;
            case "class-authoring" -> ClassAuthoring.JX;
            case "class-lifecycle-authoring" -> ClassLifecycleAuthoring.JX;
            default -> null;
        };
    }

    private static <P> void start(jx.ComponentType<P> component, boolean hydrate) {
        if (hydrate) {
            // Hydrate mode: over the markup the test rendered in Node for this case.
            Bench.seedMarkup(Bench.serverMarkup());
            React.hydrateStrict("app", component, null);
        } else {
            React.mountStrict("app", component, null);
        }
    }

    public static void main(String[] arguments) {
        if (Server.active()) { Server.install(); return; }
        String id = Bench.caseId();
        switch (id) {
            case "act-utilities" -> UtilitiesExports.start();
            case "strict-nested" -> React.mount("app", StrictNested.JX, null);
            case "root-options" -> React.mountStrict("app", RootOptionsCase.JX, null, RootOptionsCase.OPTIONS);
            case "hydrate-root" -> {
                // Server markup first, then hydration over the same nodes.
                Bench.seed(jx.react.ReactServer.renderToString(React.strictMode(jx.Views.component(HydrateRoot.JX, null))));
                React.hydrateStrict("app", HydrateRoot.JX, null);
            }
            case "combined-resource-hydration" -> {
                // The test server delivered the initial document; preserve those exact nodes.
                React.hydrateStrict("app", CombinedResourceHydration.JX, null);
            }
            case "hydrate-server" -> {
                // The markup was rendered in Node; the test hands it to the page.
                Bench.seed(Bench.serverMarkup());
                React.hydrateStrict("app", HydratedPage.JX, null);
            }
            default -> {
                jx.ComponentType<?> component = component(id);
                if (component == null) Bench.unknown(id);
                else start(component, Bench.mode().equals("hydrate"));
            }
        }
    }
}
