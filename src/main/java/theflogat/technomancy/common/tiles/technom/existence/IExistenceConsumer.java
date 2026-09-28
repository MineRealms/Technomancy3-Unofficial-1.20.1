package theflogat.technomancy.common.tiles.technom.existence;

/** A block that spends Existence power ({@code IExistenceConsumer}). */
public interface IExistenceConsumer {
    int getPower();
    int getPowerCap();
    int getMaxRate();
    void addPower(int value);
    boolean canInput();
}
