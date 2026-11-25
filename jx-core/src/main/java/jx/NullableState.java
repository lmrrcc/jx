package jx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Explicit JX compiler opt-in to null aggregate state on a direct {@link Component}
 * class extending {@link ClassComponent} or {@link PureComponent}. Required properties
 * remain strict; this does not make props nullable or make an omitted state field valid.
 * Use {@link Presence} fields for sparse state and observe nullable pending/previous
 * lifecycle inputs. Manual descriptors use {@link ClassComponentType#withNullableState()}.
 * Nullable or sparse checkpoints remain unqualified and are rejected by the descriptor.
 * This source marker has no runtime reflection or inference from constructor bodies. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface NullableState {}
