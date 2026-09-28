package theflogat.technomancy.common.tiles.technom.existence;

/** A block that makes Existence power ({@code IExistenceProducer}). */
public interface IExistenceProducer {
    int getPower();
    int getPowerCap();
    int getMaxRate();
    void addPower(int value);
    boolean canInput();
    boolean canOutput();
}
