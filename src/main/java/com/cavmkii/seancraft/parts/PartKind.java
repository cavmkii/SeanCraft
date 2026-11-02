package com.cavmkii.seancraft.parts;

/**
 * The kinds of part (design doc 4.3). Heads come in several shapes and the grip in two lengths, each fitting the families
 * that use it; the rest fit any family that has their slot.
 */
public enum PartKind {
    BLADE("Blade", PartSlot.HEAD, Material.METAL),
    LONG_BLADE("Long blade", PartSlot.HEAD, Material.METAL),
    KNIFE_BLADE("Knife blade", PartSlot.HEAD, Material.METAL),
    AXE_HEAD("Axe head", PartSlot.HEAD, Material.METAL),
    MACE_HEAD("Mace head", PartSlot.HEAD, Material.METAL),
    SPEARHEAD("Spearhead", PartSlot.HEAD, Material.METAL),
    HALBERD_HEAD("Halberd head", PartSlot.HEAD, Material.METAL),
    LIMBS("Limbs", PartSlot.HEAD, Material.METAL),
    GUARD("Guard", PartSlot.GUARD, Material.METAL),
    PLATE("Plates", PartSlot.PLATE, Material.METAL),
    HANDLE("Handle", PartSlot.HANDLE, Material.WOOD),
    HAFT("Haft", PartSlot.HANDLE, Material.WOOD),
    WRAP("Wrap", PartSlot.WRAP, Material.HIDE),
    PADDING("Padding", PartSlot.PADDING, Material.HIDE);

    public enum Material { METAL, WOOD, HIDE }

    public final String label;
    public final PartSlot slot;
    public final Material material;

    PartKind(String label, PartSlot slot, Material material) {
        this.label = label;
        this.slot = slot;
        this.material = material;
    }
}
