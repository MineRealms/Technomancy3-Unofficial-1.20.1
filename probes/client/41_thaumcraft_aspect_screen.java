//@wait 2
// Regression test for the reported crash: opening Thaumcraft's "aspect sources" category in JEI
// died with NoSuchMethodError on ITextWidget.setPosition(int, int), thrown from
// AspectSourceRecipeCategory.createRecipeExtras. That overload only exists from JEI 15.56.0.202,
// so the category works exactly as long as the jei_version pin stays on TC4R's side of the
// JEI/GTCEu conflict. Building a layout is what JEI itself does when the player opens the
// category, so this reproduces the crash without needing a screen.
//
// The layout call goes through reflection because IRecipeManager.createRecipeLayoutDrawable is
// generic over the recipe type and the category here is only known as a wildcard.
net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
if (mc.level == null) {
    return "FAIL: the client is not in a world\n";
}
StringBuilder out = new StringBuilder();
java.util.List<String> bad = new java.util.ArrayList<String>();

java.util.Optional<mezz.jei.api.runtime.IJeiRuntime> rtOpt = mezz.jei.common.Internal.getOptionalJeiRuntime();
if (rtOpt.isEmpty()) {
    return "FAIL: JEI exposes no runtime, so nothing can be checked\n";
}
mezz.jei.api.recipe.IRecipeManager rm = rtOpt.get().getRecipeManager();
net.minecraft.resources.ResourceLocation uid =
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", "aspect_sources");
java.util.Optional<mezz.jei.api.recipe.RecipeType<?>> typeOpt = rm.getRecipeType(uid);
out.append("thaumcraft:aspect_sources registered = ").append(typeOpt.isPresent()).append("\n");
if (typeOpt.isEmpty()) {
    bad.add("thaumcraft:aspect_sources is not registered, so the reported crash cannot be checked");
} else {
    mezz.jei.api.recipe.RecipeType<?> type = typeOpt.get();
    java.util.List<?> recipes = rm.createRecipeLookup(type).get().toList();
    out.append("recipes = ").append(recipes.size()).append("\n");
    if (recipes.isEmpty()) {
        bad.add("thaumcraft:aspect_sources has no recipes");
    }
    mezz.jei.api.recipe.category.IRecipeCategory<?> category = rm.getRecipeCategory(type);
    mezz.jei.api.recipe.IFocusGroup empty =
            rtOpt.get().getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
    java.lang.reflect.Method buildLayout = mezz.jei.api.recipe.IRecipeManager.class.getMethod(
            "createRecipeLayoutDrawable", mezz.jei.api.recipe.category.IRecipeCategory.class,
            Object.class, mezz.jei.api.recipe.IFocusGroup.class);
    int built = 0;
    String firstFailure = null;
    for (Object recipe : recipes) {
        try {
            Object layout = buildLayout.invoke(rm, category, recipe, empty);
            if (layout instanceof java.util.Optional && ((java.util.Optional<?>) layout).isPresent()) {
                built++;
            }
        } catch (java.lang.reflect.InvocationTargetException failure) {
            firstFailure = String.valueOf(failure.getCause());
            break;
        } catch (Throwable failure) {
            firstFailure = String.valueOf(failure);
            break;
        }
    }
    out.append("layouts built = ").append(built).append("/").append(recipes.size()).append("\n");
    if (firstFailure != null) {
        bad.add("building a layout threw " + firstFailure);
    } else if (built != recipes.size()) {
        bad.add("only " + built + " of " + recipes.size() + " layouts could be built");
    }
}
out.append(bad.isEmpty() ? "PASS\n" : "FAIL: " + bad + "\n");
return out.toString();
