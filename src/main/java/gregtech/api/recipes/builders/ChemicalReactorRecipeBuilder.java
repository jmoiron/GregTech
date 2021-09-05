package gregtech.api.recipes.builders;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.util.ValidationResult;

public class ChemicalReactorRecipeBuilder extends RecipeBuilder<ChemicalReactorRecipeBuilder> {

    private boolean disableLargeRecipe = false;

    public ChemicalReactorRecipeBuilder() {

    }

    public ChemicalReactorRecipeBuilder(Recipe recipe, RecipeMap<ChemicalReactorRecipeBuilder> recipeMap) {
        super(recipe, recipeMap);
    }

    public ChemicalReactorRecipeBuilder(RecipeBuilder<ChemicalReactorRecipeBuilder> recipeBuilder) {
        super(recipeBuilder);
    }

    @Override
    public ChemicalReactorRecipeBuilder copy() {
        return new ChemicalReactorRecipeBuilder(this);
    }

    //todo expose to CT
    public ChemicalReactorRecipeBuilder disableLargeRecipe() {
        disableLargeRecipe = true;
        return this;
    }

    public ValidationResult<Recipe> build() {
        if (!disableLargeRecipe) {
            RecipeBuilder<?> builder = RecipeMaps.LARGE_CHEMICAL_RECIPES.recipeBuilder().copy()
                    .inputsIngredients(this.inputs)
                    .outputs(this.outputs)
                    .fluidInputs(this.fluidInputs)
                    .fluidOutputs(this.fluidOutputs)
                    .duration(this.duration)
                    .EUt(this.EUt);

            if (this.cleanroomLevel != null)
                builder.cleanroomLevel(this.cleanroomLevel);
            builder.buildAndRegister();
        }

        return ValidationResult.newResult(finalizeAndValidate(),
                new Recipe(inputs, outputs, chancedOutputs, fluidInputs, fluidOutputs, duration, EUt, hidden));
    }
}
