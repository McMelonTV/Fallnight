package xyz.fallnight.server.domain.holotext;

public final class HolotextEntry {
    private String id;
    private String world;
    private double x;
    private double y;
    private double z;
    private String title;
    private String text;

    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String world() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public double x() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double y() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double z() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String text() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
