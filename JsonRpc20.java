public class JsonRpc20 {

    private final String jsonrpc;
    private final String method;
    private final int id;

    public JsonRpc20(String method, int id) {
        this.jsonrpc = "2.0";
        this.method = method;
        this.id = id;
    }

    public static class Attr {
    }

    public static class AttributesStrArray extends Attr {
        String[] attributes;
        AttributesStrArray(String[] attributes) {
            this.attributes = attributes;
        }
    }

    public static class Params {

        String eventKind;
        String mode;
        Attr attr;

        Params(String eventKind, String mode, Attr attr) {
            this.eventKind = eventKind;
            this.mode = mode;
            this.attr = attr;
        }
    }

    public static class Command extends JsonRpc20 {

        String[] params;

        Command(String method, int id, String[] params) {
            super(method, id);
            this.params = params;
        }
    }

    public static class Filter extends JsonRpc20 {

        Params params;

        Filter(int id, Params params) {
            super("filter", id);
            this.params = params;
        }
    }

}
