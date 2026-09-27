// Registration: every id this module expects must exist, and the TC4R ids it
// depends on must be read from the registry rather than assumed.
StringBuilder out = new StringBuilder();
int failures = 0;

String[] blocks = {"quantum_jar", "quantized_glass"};
for (String name : blocks) {
    net.minecraft.resources.ResourceLocation id = new net.minecraft.resources.ResourceLocation("technom", name);
    boolean present = net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id);
    out.append("block technom:").append(name).append(" -> ")
       .append(present ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id).getClass().getSimpleName() : "MISSING")
       .append("\n");
    if (!present) failures++;
}

String[] items = {"quantum_jar", "quantized_glass", "neutronized_metal", "enchanted_coil",
        "neutronized_gear", "pen_core", "potency_gem"};
for (String name : items) {
    net.minecraft.resources.ResourceLocation id = new net.minecraft.resources.ResourceLocation("technom", name);
    if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) {
        out.append("item technom:").append(name).append(" MISSING\n");
        failures++;
    }
}
out.append("items checked: ").append(items.length).append("\n");

net.minecraft.resources.ResourceLocation jarType = new net.minecraft.resources.ResourceLocation("technom", "quantum_jar");
boolean beType = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(jarType);
out.append("block entity type technom:quantum_jar -> ").append(beType ? "present" : "MISSING").append("\n");
if (!beType) failures++;

// The TC4R content this module interoperates with.
String[] required = {"warded_jar", "essentia_tube"};
for (String name : required) {
    net.minecraft.resources.ResourceLocation id = new net.minecraft.resources.ResourceLocation("thaumcraft", name);
    boolean present = net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id);
    out.append("thaumcraft:").append(name).append(" -> ").append(present ? "present" : "MISSING").append("\n");
    if (!present) failures++;
}

// A creative tab that reports nothing would hide the whole mod.
net.minecraft.resources.ResourceLocation tab = new net.minecraft.resources.ResourceLocation("technom", "main");
out.append("creative tab technom:main -> ")
   .append(net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(tab) ? "present" : "MISSING")
   .append("\n");
if (!net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(tab)) failures++;

out.append(failures == 0 ? "PASS" : "FAIL: " + failures + " registry problem(s)").append("\n");
return out.toString();
