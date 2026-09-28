// The Botania lexicon is loaded through its use_resource_pack, so our technomancy
// category and entries should be in the client's book contents. The server side
// cannot see this: Patchouli books only exist on a client.
net.minecraft.world.level.Level level = net.minecraft.client.Minecraft.getInstance().level;
if (level == null) {
    return "FAIL: the client is not in a world\n";
}
StringBuilder out = new StringBuilder();
java.util.List<String> bad = new java.util.ArrayList<String>();
vazkii.patchouli.common.book.Book book = vazkii.patchouli.common.book.BookRegistry.INSTANCE.books
        .get(new net.minecraft.resources.ResourceLocation("botania", "lexicon"));
if (book == null) {
    bad.add("botania:lexicon is not a registered book");
} else {
    if (book.getContents().isErrored()) {
        bad.add("botania:lexicon contents errored: " + book.getContents().getException());
    }
    out.append("total categories=").append(book.getContents().categories.size())
       .append(" entries=").append(book.getContents().entries.size()).append("\n");
    net.minecraft.resources.ResourceLocation category =
            new net.minecraft.resources.ResourceLocation("botania", "technomancy");
    if (!book.getContents().categories.containsKey(category)) {
        bad.add("missing category " + category);
    }
    String[] entries = {
        "technomancy/techno_basics",
        "technomancy/flower_dynamo",
        "technomancy/mana_fabricator",
        "technomancy/processor_bo",
        "technomancy/mana_exchanger"
    };
    for (String entry : entries) {
        net.minecraft.resources.ResourceLocation id = new net.minecraft.resources.ResourceLocation("botania", entry);
        boolean present = book.getContents().entries.containsKey(id);
        out.append(entry).append(" = ").append(present).append("\n");
        if (!present) {
            bad.add("missing entry " + id);
        }
    }
}
out.append(bad.isEmpty() ? "PASS\n" : "FAIL: " + bad + "\n");
return out.toString();
