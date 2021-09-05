package gregtech.api.recipes.builders;

import gregtech.api.GTValues;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.EnumValidationResult;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;
import gregtech.api.util.ValidationResult;

import javax.annotation.Nonnull;

public class CircuitAssemblerRecipeBuilder extends RecipeBuilder<CircuitAssemblerRecipeBuilder> {

    private int solderMultiplier = 1;

    public CircuitAssemblerRecipeBuilder() {
    }

    public CircuitAssemblerRecipeBuilder(Recipe recipe, RecipeMap<CircuitAssemblerRecipeBuilder> recipeMap) {
        super(recipe, recipeMap);
    }

    public CircuitAssemblerRecipeBuilder(RecipeBuilder<CircuitAssemblerRecipeBuilder> recipeBuilder) {
        super(recipeBuilder);
    }

    @Override
    @Nonnull
    public CircuitAssemblerRecipeBuilder copy() {
        return new CircuitAssemblerRecipeBuilder(this);
    }

    public CircuitAssemblerRecipeBuilder solderMultiplier(int multiplier) {
        if (!GTUtility.isBetweenInclusive(1, 64000, (long) GTValues.L * multiplier)) {
            GTLog.logger.error("Fluid multiplier cannot exceed 64000mb total. Multiplier: {}", multiplier);
            GTLog.logger.error("Stacktrace:", new IllegalArgumentException());
            recipeStatus = EnumValidationResult.INVALID;
        }
        this.solderMultiplier = multiplier;
        return this;
    }

    @Override
    @Nonnull
    public ValidationResult<Recipe> build() {
        RecipeBuilder<?> builder = this.copy();
        RecipeBuilder<?> builder2 = this.copy();
        if (fluidInputs.isEmpty()) {
            builder.fluidInputs(Materials.SolderingAlloy.getFluid(Math.max(1, (GTValues.L / 2) * solderMultiplier)));
            if (this.cleanroomLevel != null)
                builder.cleanroomLevel(this.cleanroomLevel);
            builder.buildAndRegister();

            builder2.fluidInputs(Materials.Tin.getFluid(Math.max(1, GTValues.L * solderMultiplier)));
            if (this.cleanroomLevel != null)
                builder2.cleanroomLevel(this.cleanroomLevel);
            builder2.buildAndRegister();
        }

        return ValidationResult.newResult(finalizeAndValidate(),
                new Recipe(inputs, outputs, chancedOutputs, fluidInputs, fluidOutputs, duration, EUt, hidden));
    }
}
