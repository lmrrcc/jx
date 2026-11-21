// Framework-owned reference fixture: the same cases in plain React.
import { StrictMode } from 'react';
import { createRoot, hydrateRoot } from 'react-dom/client';
import { renderToString } from 'react-dom/server';
import { caseId, unknown } from './bench.js';
import { StateCounter } from './cases/state-counter.jsx';
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
import { LazyComponent } from './cases/lazy-component.jsx';
import { ActivityCase } from './cases/activity.jsx';
import { ProfilerCase } from './cases/profiler.jsx';
import { ViewTransitionCase } from './cases/view-transition.jsx';
import { CloneElement } from './cases/clone-element.jsx';
import { ChildrenUtilities } from './cases/children-utilities.jsx';
import { Portal } from './cases/portal.jsx';
import { FlushSync } from './cases/flush-sync.jsx';
import { ResourceHints } from './cases/resource-hints.jsx';
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

const cases = {
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
  'lazy-component': LazyComponent,
  'activity': ActivityCase,
  'profiler': ProfilerCase,
  'view-transition': ViewTransitionCase,
  'clone-element': CloneElement,
  'children-utilities': ChildrenUtilities,
  'portal': Portal,
  'flush-sync': FlushSync,
  'resource-hints': ResourceHints,
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

const id = caseId();
const Case = Object.hasOwn(cases, id) ? cases[id] : null;
if (id === 'hydrate-root') {
  // Server markup first, then hydration over the same nodes.
  const container = document.getElementById('app');
  container.innerHTML = renderToString(<StrictMode><Case /></StrictMode>);
  container.querySelector('button').benchServerNode = true;
  hydrateRoot(container, <StrictMode><Case /></StrictMode>);
} else if (id === 'strict-nested') {
  // The only case without a root StrictMode: it places StrictMode itself.
  createRoot(document.getElementById('app')).render(<Case />);
} else if (Case) {
  createRoot(document.getElementById('app')).render(<StrictMode><Case /></StrictMode>);
} else {
  unknown(id);
}
