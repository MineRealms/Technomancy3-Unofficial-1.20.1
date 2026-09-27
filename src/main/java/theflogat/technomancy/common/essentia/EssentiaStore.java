package theflogat.technomancy.common.essentia;

import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * The authoritative essentia contents of one machine: quantities keyed by aspect, plus the
 * separate set of aspect filters that decides what may enter.
 *
 * <p>Quantities and filters are deliberately distinct, as the TC4R
 * {@link dev.tc4port.thaumcraft.api.aspect.AspectContainerView} contract requires: a filter
 * identity is not a phantom amount, so a filtered but empty store reports no contents while
 * still advertising what it wants. Suction and connectivity are block-entity policy and are
 * intentionally not modelled here; a block entity derives them from this store.</p>
 *
 * <p>Simulation never mutates anything. The class is not thread-safe; it is used on the
 * logical server thread only.</p>
 */
public final class EssentiaStore {

    private static final int SCHEMA = 1;

    private EssentiaLimits limits;
    /** Insertion-ordered so displays and {@code visibleAspectOrder} are stable. */
    private final LinkedHashMap<AspectId, Integer> amounts = new LinkedHashMap<>();
    private final LinkedHashSet<AspectId> filters = new LinkedHashSet<>();
    private int total;

    private Runnable listener = () -> {};

    public EssentiaStore(EssentiaLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public EssentiaLimits limits() {
        return limits;
    }

    /**
     * Replaces the limits (e.g. after an upgrade change). Contents beyond a smaller new
     * capacity are kept; nothing is destroyed, the store just stops accepting.
     */
    public void setLimits(EssentiaLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public void setListener(Runnable listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    // ---- inspection ----

    public int amount(AspectId aspect) {
        return aspect == null ? 0 : amounts.getOrDefault(aspect, 0);
    }

    public int total() {
        return total;
    }

    public int totalCapacity() {
        return limits.totalCapacity();
    }

    public boolean isEmpty() {
        return total == 0;
    }

    /** Aspects currently holding a positive amount, in insertion order. */
    public Set<AspectId> aspects() {
        return Collections.unmodifiableSet(amounts.keySet());
    }

    /** An immutable snapshot for {@code AspectContainerView.visibleAspects()}. */
    public AspectAmounts visibleAspects() {
        return amounts.isEmpty() ? AspectAmounts.EMPTY : new AspectAmounts(new LinkedHashMap<>(amounts));
    }

    /** Stored aspects first, then filtered-but-empty ones, so a labelled empty store still shows its label. */
    public List<AspectId> visibleAspectOrder() {
        LinkedHashSet<AspectId> order = new LinkedHashSet<>(amounts.keySet());
        order.addAll(filters);
        return List.copyOf(order);
    }

    /**
     * The aspect a single-slot display or a fuel picker should use: the largest amount, with
     * the oldest entry winning a tie. Returns {@code null} when empty.
     */
    public AspectId dominantAspect() {
        AspectId best = null;
        int bestAmount = 0;
        for (Map.Entry<AspectId, Integer> entry : amounts.entrySet()) {
            if (entry.getValue() > bestAmount) {
                best = entry.getKey();
                bestAmount = entry.getValue();
            }
        }
        return best;
    }

    /** Room left for {@code aspect}, bounded by both its own limit and the shared pool. */
    public int space(AspectId aspect) {
        if (aspect == null || !allows(aspect) || !hasSlotFor(aspect)) {
            return 0;
        }
        int perAspect = limits.effectivePerAspectCapacity() - amount(aspect);
        return Math.max(0, Math.min(perAspect, limits.totalCapacity() - total));
    }

    /**
     * Whether this aspect could ever enter: the filter allows it and an aspect slot is free
     * or already taken by it. A full store still "wants" its aspect, so callers deciding
     * suction must additionally look at {@link #space(AspectId)}.
     */
    public boolean accepts(AspectId aspect) {
        return aspect != null && allows(aspect) && hasSlotFor(aspect);
    }

    // ---- transfers ----

    /**
     * Inserts up to {@code amount} of one aspect.
     *
     * @return the amount accepted; for a void store this is the full request even though the
     *         part above the capacity is destroyed, matching TC4's void jar
     */
    public int add(AspectId aspect, int amount, boolean simulate) {
        if (aspect == null || amount <= 0 || !accepts(aspect)) {
            return 0;
        }
        int room = space(aspect);
        int stored = Math.min(amount, room);
        if (stored <= 0 && !limits.voidOverflow()) {
            return 0;
        }
        if (!simulate && stored > 0) {
            amounts.merge(aspect, stored, Integer::sum);
            total += stored;
            listener.run();
        }
        return limits.voidOverflow() ? amount : stored;
    }

    /** Removes up to {@code amount} of one aspect. Returns the amount removed. */
    public int take(AspectId aspect, int amount, boolean simulate) {
        if (aspect == null || amount <= 0) {
            return 0;
        }
        int taken = Math.min(amount, amount(aspect));
        if (taken <= 0) {
            return 0;
        }
        if (!simulate) {
            remove(aspect, taken);
        }
        return taken;
    }

    /**
     * All-or-nothing removal, as {@link dev.tc4port.thaumcraft.api.essentia.EssentiaSource}
     * requires: a remote drain either yields the complete amount or nothing at all.
     */
    public boolean takeExact(AspectId aspect, int amount, boolean simulate) {
        if (aspect == null || amount <= 0 || amount(aspect) < amount) {
            return false;
        }
        if (!simulate) {
            remove(aspect, amount);
        }
        return true;
    }

    private void remove(AspectId aspect, int amount) {
        int left = amounts.get(aspect) - amount;
        if (left > 0) {
            amounts.put(aspect, left);
        } else {
            amounts.remove(aspect);
        }
        total -= amount;
        listener.run();
    }

    /** Discards all contents; filters are kept, since a label outlives what it held. */
    public void clearContents() {
        if (amounts.isEmpty()) {
            return;
        }
        amounts.clear();
        total = 0;
        listener.run();
    }

    // ---- filters ----

    public Set<AspectId> filters() {
        return Collections.unmodifiableSet(filters);
    }

    /** An empty filter set accepts every aspect. */
    public boolean allows(AspectId aspect) {
        return filters.isEmpty() || filters.contains(aspect);
    }

    /** @return {@code true} if the filter set changed */
    public boolean setFilters(Collection<AspectId> selected) {
        LinkedHashSet<AspectId> replacement = new LinkedHashSet<>();
        for (AspectId aspect : selected) {
            replacement.add(Objects.requireNonNull(aspect, "aspect"));
        }
        if (replacement.size() > limits.maxDistinctAspects()) {
            throw new IllegalArgumentException(
                    "more filters than aspect slots: " + replacement.size() + " > " + limits.maxDistinctAspects());
        }
        if (replacement.equals(filters)) {
            return false;
        }
        filters.clear();
        filters.addAll(replacement);
        listener.run();
        return true;
    }

    /** @return {@code true} if the filter set changed */
    public boolean addFilter(AspectId aspect) {
        Objects.requireNonNull(aspect, "aspect");
        if (filters.contains(aspect)) {
            return false;
        }
        if (filters.size() >= limits.maxDistinctAspects()) {
            return false;
        }
        filters.add(aspect);
        listener.run();
        return true;
    }

    /** @return {@code true} if there was a filter to clear */
    public boolean clearFilters() {
        if (filters.isEmpty()) {
            return false;
        }
        filters.clear();
        listener.run();
        return true;
    }

    // ---- persistence ----

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", SCHEMA);
        ListTag stored = new ListTag();
        amounts.forEach((aspect, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", aspect.serialized());
            entry.putInt("n", amount);
            stored.add(entry);
        });
        tag.put("aspects", stored);
        if (!filters.isEmpty()) {
            ListTag saved = new ListTag();
            filters.forEach(aspect -> saved.add(StringTag.valueOf(aspect.serialized())));
            tag.put("filters", saved);
        }
        return tag;
    }

    /**
     * Restores saved contents, clamping to the current limits and dropping unreadable aspect
     * ids rather than failing the whole block entity.
     *
     * @return {@code true} if anything had to be dropped or clamped, i.e. the caller should
     *         log that the save was not restored verbatim
     */
    public boolean load(CompoundTag tag) {
        amounts.clear();
        filters.clear();
        total = 0;
        if (tag.isEmpty()) {
            return false;
        }
        if (tag.getInt("v") != SCHEMA) {
            return true;
        }
        boolean lossy = false;
        ListTag saved = tag.getList("aspects", Tag.TAG_COMPOUND);
        for (int i = 0; i < saved.size(); i++) {
            CompoundTag entry = saved.getCompound(i);
            AspectId aspect = parse(entry.getString("id"));
            int amount = entry.getInt("n");
            if (aspect == null || amount <= 0) {
                lossy = true;
                continue;
            }
            // Restore against the limits so a shrunken capacity or aspect count cannot be
            // exceeded by an old save; whatever does not fit is reported, not silently kept.
            int room = Math.min(limits.effectivePerAspectCapacity() - amount(aspect),
                    limits.totalCapacity() - total);
            int stored = hasSlotFor(aspect) ? Math.max(0, Math.min(amount, room)) : 0;
            if (stored < amount) {
                lossy = true;
            }
            if (stored > 0) {
                amounts.merge(aspect, stored, Integer::sum);
                total += stored;
            }
        }
        ListTag savedFilters = tag.getList("filters", Tag.TAG_STRING);
        for (int i = 0; i < savedFilters.size(); i++) {
            AspectId aspect = parse(savedFilters.getString(i));
            if (aspect == null || filters.size() >= limits.maxDistinctAspects()) {
                lossy = true;
                continue;
            }
            filters.add(aspect);
        }
        return lossy;
    }

    private boolean hasSlotFor(AspectId aspect) {
        return amounts.containsKey(aspect) || amounts.size() < limits.maxDistinctAspects();
    }

    private static AspectId parse(String serialized) {
        if (serialized.isEmpty()) {
            return null;
        }
        try {
            return AspectId.parse(serialized);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
