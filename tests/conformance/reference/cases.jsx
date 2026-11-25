// Framework-owned reference fixture: every bench case by id, for the browser entry and the
// Node server alike.
import { StateCounter } from './cases/state-counter.jsx';
import { RuntimeUtilities } from './cases/runtime-utilities.jsx';
import { CombinedPortalLifecycle } from './cases/combined-portal-lifecycle.jsx';
import { CombinedResourceHydration } from './cases/combined-resource-hydration.jsx';
import { StateLazyInit } from './cases/state-lazy-init.jsx';
import { CallableState } from './cases/callable-state.jsx';
import { ReducerBatch } from './cases/reducer-batch.jsx';
import { EffectCleanup } from './cases/effect-cleanup.jsx';
import { EffectDependencies } from './cases/effect-dependencies.jsx';
import { LayoutEffectOrder } from './cases/layout-effect-order.jsx';
import { BatchedUpdates } from './cases/batched-updates.jsx';
import { RefPersist } from './cases/ref-persist.jsx';
import { DomRefFocus } from './cases/dom-ref-focus.jsx';
import { ContextNested } from './cases/context-nested.jsx';
import { MemoRecompute } from './cases/memo-recompute.jsx';
import { CallbackIdentity } from './cases/callback-identity.jsx';
import { UseId } from './cases/use-id.jsx';
import { ExternalStoreCase } from './cases/external-store.jsx';
import { ErrorBoundaryCase } from './cases/error-boundary.jsx';
import { ControlledInput } from './cases/controlled-input.jsx';
import { CheckboxCase } from './cases/checkbox.jsx';
import { KeyEvent } from './cases/key-event.jsx';
import { PreventDefault } from './cases/prevent-default.jsx';
import { StopPropagation } from './cases/stop-propagation.jsx';
import { KeyedList } from './cases/keyed-list.jsx';
import { RemountByKey } from './cases/remount-by-key.jsx';
import { PropsUpdate } from './cases/props-update.jsx';
import { Conditional } from './cases/conditional.jsx';
import { EmptyRender } from './cases/empty-render.jsx';
import { MemoScalars } from './cases/memo-scalars.jsx';
import { FragmentCase } from './cases/fragment.jsx';
import { ChildrenProp } from './cases/children-prop.jsx';
import { TextEscaping } from './cases/text-escaping.jsx';
import { ChildValues } from './cases/child-values.jsx';
import { AttributesCase } from './cases/attributes.jsx';
import { SvgCase } from './cases/svg.jsx';
import { CustomHook } from './cases/custom-hook.jsx';
import { FunctionProps } from './cases/function-props.jsx';
import { DynamicComponent } from './cases/dynamic-component.jsx';
import { NamespaceComponents } from './cases/namespace-components.jsx';
import { ChoiceInputs } from './cases/choice-inputs.jsx';
import { UncontrolledInputs } from './cases/uncontrolled-inputs.jsx';
import { CapturePhase } from './cases/capture-phase.jsx';
import { CustomElements } from './cases/custom-elements.jsx';
import { DocumentMetadata } from './cases/document-metadata.jsx';
import { ResourceElements } from './cases/resource-elements.jsx';
import { ErrorReporting } from './cases/error-reporting.jsx';
import { InsertionEffect } from './cases/insertion-effect.jsx';
import { EffectEvent } from './cases/effect-event.jsx';
import { ImperativeHandle } from './cases/imperative-handle.jsx';
import { ForwardRef } from './cases/forward-ref.jsx';
import { RefAsProp } from './cases/ref-as-prop.jsx';
import { RefCallback } from './cases/ref-callback.jsx';
import { CreateRef } from './cases/create-ref.jsx';
import { ReducerLazyInit } from './cases/reducer-lazy-init.jsx';
import { StrictNested } from './cases/strict-nested.jsx';
import { IsValidElement } from './cases/is-valid-element.jsx';
import { ChildSlots } from './cases/child-slots.jsx';
import { MemoSkip } from './cases/memo-skip.jsx';
import { TransitionPending } from './cases/transition-pending.jsx';
import { StartTransition } from './cases/start-transition.jsx';
import { DeferredValue } from './cases/deferred-value.jsx';
import { Optimistic } from './cases/optimistic.jsx';
import { ActionState } from './cases/action-state.jsx';
import { FormStatus } from './cases/form-status.jsx';
import { FormActions } from './cases/form-actions.jsx';
import { FormReset } from './cases/form-reset.jsx';
import { UsePromise } from './cases/use-promise.jsx';
import { DomAttributes, DomElements, DomEvents } from './cases/dom-tables.jsx';
import { HydratedPage } from './cases/hydrate-server.jsx';
import { RootOptions } from './cases/root-options.jsx';
import { FormState } from './cases/form-state.jsx';
import { AsyncFunction } from './cases/async-function.jsx';
import { TransitionPromise } from './cases/transition-promise.jsx';
import { LazyComponent } from './cases/lazy-component.jsx';
import { ActivityCase } from './cases/activity.jsx';
import { ProfilerCase } from './cases/profiler.jsx';
import { ViewTransitionCase } from './cases/view-transition.jsx';
import { CloneElement } from './cases/clone-element.jsx';
import { ChildrenUtilities } from './cases/children-utilities.jsx';
import { Portal } from './cases/portal.jsx';
import { FlushSync } from './cases/flush-sync.jsx';
import { ResourceHints } from './cases/resource-hints.jsx';
import { ImagePreload } from './cases/image-preload.jsx';
import { SpreadProps } from './cases/spread-props.jsx';
import { DefaultProps } from './cases/default-props.jsx';
import { GenericList } from './cases/generic-list.jsx';
import { EventPayload } from './cases/event-payload.jsx';
import { RawHtml } from './cases/raw-html.jsx';
import { HydrateRoot } from './cases/hydrate-root.jsx';
import { DebugValue } from './cases/debug-value.jsx';
import { PointerEvents } from './cases/pointer-events.jsx';
import { TransferEvents } from './cases/transfer-events.jsx';
import { MediaEvents } from './cases/media-events.jsx';
import { LayoutEffectDependencies } from './cases/layout-effect-dependencies.jsx';
import { CombinedKeyedContext } from './cases/combined-keyed-context.jsx';
import { CombinedActivityStore } from './cases/combined-activity-store.jsx';
import { CombinedChildrenMap, CombinedChildrenList } from './cases/combined-children-map.jsx';
import { CombinedChildrenContract } from './cases/combined-children-contract.jsx';
import { CombinedKeyedRefs } from './cases/combined-keyed-refs.jsx';
import { CombinedImperativeHandles } from './cases/combined-imperative-handles.jsx';
import { CombinedActionQueue } from './cases/combined-action-queue.jsx';
import { CombinedOptimisticStore } from './cases/combined-optimistic-store.jsx';
import { CombinedTransitionCancel } from './cases/combined-transition-cancel.jsx';
import { ClassRuntime } from './cases/class-runtime.jsx';
import { ClassKeyed } from './cases/class-keyed.jsx';
import { ClassContextCase } from './cases/class-context.jsx';
import { ClassDerivedProps } from './cases/class-derived-props.jsx';
import { ClassErrorLifecycle } from './cases/class-error-lifecycle.jsx';
import { ClassAuthoring } from './cases/class-authoring.jsx';
import { ClassLifecycleAuthoring } from './cases/class-lifecycle-authoring.jsx';
import { ClassNextContext } from './cases/class-next-context.jsx';
import { ClassNullPatch } from './cases/class-null-patch.jsx';

export const cases = {
  'runtime-utilities': RuntimeUtilities,
  'combined-portal-lifecycle': CombinedPortalLifecycle,
  'combined-keyed-context': CombinedKeyedContext,
  'combined-activity-store': CombinedActivityStore,
  'combined-children-map': CombinedChildrenMap,
  'combined-children-list': CombinedChildrenList,
  'combined-children-contract': CombinedChildrenContract,
  'combined-keyed-refs': CombinedKeyedRefs,
  'combined-imperative-handles': CombinedImperativeHandles,
  'combined-action-queue': CombinedActionQueue,
  'combined-optimistic-store': CombinedOptimisticStore,
  'combined-transition-cancel': CombinedTransitionCancel,
  'class-runtime': ClassRuntime,
  'class-keyed': ClassKeyed,
  'class-context': ClassContextCase,
  'class-derived-props': ClassDerivedProps,
  'class-error-lifecycle': ClassErrorLifecycle,
  'class-authoring': ClassAuthoring,
  'class-lifecycle-authoring': ClassLifecycleAuthoring,
  'class-next-context': ClassNextContext,
  'class-null-patch': ClassNullPatch,
  'state-counter': StateCounter,
  'state-lazy-init': StateLazyInit,
  'callable-state': CallableState,
  'reducer-batch': ReducerBatch,
  'effect-cleanup': EffectCleanup,
  'effect-dependencies': EffectDependencies,
  'layout-effect-order': LayoutEffectOrder,
  'batched-updates': BatchedUpdates,
  'ref-persist': RefPersist,
  'dom-ref-focus': DomRefFocus,
  'context-nested': ContextNested,
  'memo-recompute': MemoRecompute,
  'callback-identity': CallbackIdentity,
  'use-id': UseId,
  'external-store': ExternalStoreCase,
  'error-boundary': ErrorBoundaryCase,
  'controlled-input': ControlledInput,
  'checkbox': CheckboxCase,
  'key-event': KeyEvent,
  'prevent-default': PreventDefault,
  'stop-propagation': StopPropagation,
  'keyed-list': KeyedList,
  'remount-by-key': RemountByKey,
  'props-update': PropsUpdate,
  'conditional': Conditional,
  'empty-render': EmptyRender,
  'memo-scalars': MemoScalars,
  'fragment': FragmentCase,
  'children-prop': ChildrenProp,
  'text-escaping': TextEscaping,
  'child-values': ChildValues,
  'attributes': AttributesCase,
  'svg': SvgCase,
  'custom-hook': CustomHook,
  'function-props': FunctionProps,
  'dynamic-component': DynamicComponent,
  'namespace-components': NamespaceComponents,
  'choice-inputs': ChoiceInputs,
  'uncontrolled-inputs': UncontrolledInputs,
  'capture-phase': CapturePhase,
  'custom-elements': CustomElements,
  'document-metadata': DocumentMetadata,
  'resource-elements': ResourceElements,
  'error-reporting': ErrorReporting,
  'insertion-effect': InsertionEffect,
  'effect-event': EffectEvent,
  'imperative-handle': ImperativeHandle,
  'forward-ref': ForwardRef,
  'ref-as-prop': RefAsProp,
  'ref-callback': RefCallback,
  'create-ref': CreateRef,
  'reducer-lazy-init': ReducerLazyInit,
  'strict-nested': StrictNested,
  'is-valid-element': IsValidElement,
  'child-slots': ChildSlots,
  'memo-skip': MemoSkip,
  'transition-pending': TransitionPending,
  'start-transition': StartTransition,
  'deferred-value': DeferredValue,
  'optimistic': Optimistic,
  'action-state': ActionState,
  'form-status': FormStatus,
  'form-actions': FormActions,
  'form-reset': FormReset,
  'use-promise': UsePromise,
  'dom-elements': DomElements,
  'dom-attributes': DomAttributes,
  'dom-events': DomEvents,
  'hydrate-server': HydratedPage,
  'combined-resource-hydration': CombinedResourceHydration,
  'root-options': RootOptions,
  'form-state': FormState,
  'async-function': AsyncFunction,
  'transition-promise': TransitionPromise,
  'lazy-component': LazyComponent,
  'activity': ActivityCase,
  'profiler': ProfilerCase,
  'view-transition': ViewTransitionCase,
  'clone-element': CloneElement,
  'children-utilities': ChildrenUtilities,
  'portal': Portal,
  'flush-sync': FlushSync,
  'resource-hints': ResourceHints,
  'image-preload': ImagePreload,
  'spread-props': SpreadProps,
  'default-props': DefaultProps,
  'generic-list': GenericList,
  'event-payload': EventPayload,
  'raw-html': RawHtml,
  'hydrate-root': HydrateRoot,
  'debug-value': DebugValue,
  'pointer-events': PointerEvents,
  'transfer-events': TransferEvents,
  'media-events': MediaEvents,
  'layout-effect-dependencies': LayoutEffectDependencies
};
