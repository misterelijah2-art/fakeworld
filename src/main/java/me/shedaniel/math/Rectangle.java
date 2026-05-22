package me.shedaniel.math;

public class Rectangle {
    public double x;
    public double y;
    public double width;
    public double height;

    public Rectangle() {
        this(0,0,0,0);
    }

    public Rectangle(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public Rectangle(Rectangle other) {
        this.x = other.x;
        this.y = other.y;
        this.width = other.width;
        this.height = other.height;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }

    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setWidth(double width) { this.width = width; }
    public void setHeight(double height) { this.height = height; }

    @Override
    public String toString() {
        return "Rectangle["+x+", "+y+", "+width+", "+height+"]";
    }
}

