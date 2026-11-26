package jx.conformance.cases;

import java.util.List;
import java.util.Set;
import jx.*;
import jx.react.React;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/** Explicit schema consumer: native defaults resolve raw input before Java construction. */
public final class ClassDefaultsFixture {
    private ClassDefaultsFixture() {}
    private record Marker(String name) {}
    private static final Marker DEFAULT_MARKER = new Marker("default");
    private static final Marker OTHER_MARKER = new Marker("other");
    public record Props(String required, String label, int amount, boolean enabled, String text,
                        Marker marker, Presence<String> nothing, Presence<String> unknown) {}
    public record Model(int clicks, String derived) {}
    private static final ClassSchema.Field<Props,String> REQUIRED = ClassSchema.field("required", ClassSchema.STRING, Props::required);
    private static final ClassSchema.Field<Props,String> LABEL = ClassSchema.field("label", ClassSchema.STRING.nullable(), Props::label);
    private static final ClassSchema.Field<Props,Integer> AMOUNT = ClassSchema.field("amount", ClassSchema.INTEGER, Props::amount);
    private static final ClassSchema.Field<Props,Boolean> ENABLED = ClassSchema.field("enabled", ClassSchema.BOOLEAN, Props::enabled);
    private static final ClassSchema.Field<Props,String> TEXT = ClassSchema.field("text", ClassSchema.STRING, Props::text);
    private static final ClassSchema.Field<Props,Marker> MARKER = ClassSchema.field("marker", ClassSchema.reference(value -> {
        if (value instanceof Marker marker) return marker;
        throw new IllegalArgumentException("Expected default marker");
    }), Props::marker);
    private static final ClassSchema.Field<Props,String> NOTHING = ClassSchema.optionalField("nothing", ClassSchema.STRING.nullable(), Props::nothing);
    private static final ClassSchema.Field<Props,String> UNKNOWN = ClassSchema.optionalField("unknown", ClassSchema.STRING, Props::unknown);
    private static final ClassSchema<Props> PROPS = new ClassSchema<>(List.of(REQUIRED,LABEL,AMOUNT,ENABLED,TEXT,MARKER,NOTHING,UNKNOWN),
            reader -> new Props(reader.get(REQUIRED),reader.get(LABEL),reader.get(AMOUNT),reader.get(ENABLED),reader.get(TEXT),
                    reader.get(MARKER),reader.find(NOTHING),reader.find(UNKNOWN)));
    private static final ClassSchema.Field<Model,Integer> CLICKS = ClassSchema.field("clicks",ClassSchema.INTEGER,Model::clicks);
    private static final ClassSchema.Field<Model,String> DERIVED = ClassSchema.field("derived",ClassSchema.STRING,Model::derived);
    private static final ClassSchema<Model> MODEL = new ClassSchema<>(List.of(CLICKS,DERIVED), r -> new Model(r.get(CLICKS),r.get(DERIVED)));
    private static final ClassComponentType<Props,Model,Peer> TYPE = ClassComponentType.pure("DefaultedClass",PROPS,MODEL,Peer::new,Set.of())
            .withDefaultProps(ClassProps.empty(PROPS).set(LABEL,"fallback").set(AMOUNT,7).set(ENABLED,true).set(TEXT,"fallback-text")
                    .set(MARKER,DEFAULT_MARKER).set(NOTHING,null).undefined(UNKNOWN))
            .withDerivedStateFromProps((props,state) -> MODEL.patch().set(DERIVED, summary(props)));

    public static View.ClassPropsView<Props,Model,Peer> view(int mode, boolean reset, ClassRef<Peer> ref) {
        var input = ClassProps.empty(PROPS).set(REQUIRED,"required");
        if (mode == 1 || mode >= 4) input = input.undefined(LABEL).undefined(AMOUNT).undefined(ENABLED).undefined(TEXT).undefined(MARKER);
        if (mode == 2) input = input.set(LABEL,null).set(AMOUNT,0).set(ENABLED,false).set(TEXT,"").set(MARKER,OTHER_MARKER);
        if (mode == 3) input = input.set(LABEL,"explicit").set(AMOUNT,3).set(ENABLED,true).set(TEXT,"written").set(MARKER,DEFAULT_MARKER)
                .set(NOTHING,"value").set(UNKNOWN,"known");
        var view = Views.classPropsView(TYPE,input,reset ? "replacement" : "stable",ref);
        if (mode == 4) return Views.cloneClassProps(view,null);
        if (mode >= 5) view = Views.cloneClassProps(view,ClassProps.empty(PROPS).undefined(LABEL));
        return mode == 6 ? Views.cloneClassProps(view,null) : view;
    }
    public static ClassRef<Peer> ref() {
        return new ClassRef<>(value -> {
            if (value instanceof Peer peer) return peer;
            throw new IllegalArgumentException("Expected defaulted class peer");
        });
    }
    public static String inspect(View view) { return inspectNative(React.element(view)); }
    @JSBody(params="element",script="return Object.prototype.hasOwnProperty.call(element.props,'label') + ':' + String(element.props.label) + ':' + (element.props.marker === element.type.defaultProps.marker) + ':' + (Object.getPrototypeOf(element.type.defaultProps) === null);")
    private static native String inspectNative(JSObject element);
    private static String presence(Presence<String> value) {
        return switch(value) {
            case Presence.Absent<String> ignored -> "absent";
            case Presence.Undefined<String> ignored -> "undefined";
            case Presence.Present<String> present -> String.valueOf(present.value());
        };
    }
    private static String summary(Props props) {
        return props.required()+"|"+props.label()+"|"+props.amount()+"|"+props.enabled()+"|"+props.text()+"|"
                +(props.marker()==DEFAULT_MARKER ? "default" : "other")+"|"+presence(props.nothing())+"|"+presence(props.unknown());
    }
    public static final class Peer extends PureComponent<Props,Model,Void> {
        private final String constructed;
        Peer(Props props) { super(props,new Model(0,"initial")); constructed=summary(props); }
        @Override public View render() {
            return Views.element("section",Attributes.create().attr("data-testid","defaulted-class"),
                    Views.element("output",Attributes.create().attr("data-testid","default-values"),summary(props())),
                    Views.element("p",Attributes.create().attr("data-testid","default-derived"),state().derived()),
                    Views.element("p",Attributes.create().attr("data-testid","default-constructor"),constructed),
                    Views.element("button",Attributes.create().on("onClick",event -> setState((pending,next) -> MODEL.patch().set(CLICKS,pending.clicks()+1))),"Default clicks: "+state().clicks()));
        }
    }
}
