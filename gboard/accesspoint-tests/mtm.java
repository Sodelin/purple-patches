public final class mtm {
    public String d;
    private String token;
    private int icon;
    private Runnable callback;
    public void l(String value) { token = value; }
    public void i(int value) { icon = value; }
    public void q(Runnable value) { callback = value; }
    public mtr a() {
        mtr value = new mtr();
        value.token = token; value.label = d; value.icon = icon; value.callback = callback;
        return value;
    }
}
