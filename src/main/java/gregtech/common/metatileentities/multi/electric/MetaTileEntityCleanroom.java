package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GTValues;
import gregtech.api.capability.ICleanroomReceiver;
import gregtech.api.capability.ICleanroomTransmitter;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.gui.widgets.AdvancedTextWidget;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.multiblock.BlockPattern;
import gregtech.api.multiblock.BlockWorldState;
import gregtech.api.multiblock.FactoryBlockPattern;
import gregtech.api.multiblock.PatternMatchContext;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.recipeproperties.CleanroomProperty.CleanroomLevel;
import gregtech.api.render.ICubeRenderer;
import gregtech.api.render.OrientedOverlayRenderer;
import gregtech.api.render.Textures;
import gregtech.api.util.GTUtility;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockCleanroomCasing;
import gregtech.common.blocks.MetaBlocks;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public class MetaTileEntityCleanroom extends RecipeMapMultiblockController implements ICleanroomTransmitter {

    public static final int MIN_RADIUS = 2;
    public static final int MAX_RADIUS = 7;

    private int currentSize = 2;

    private static final MultiblockAbility<?>[] ALLOWED_ABILITIES = {
            MultiblockAbility.INPUT_ENERGY
    };

    private CleanroomLevel cleanLevel;
    private int rawLevel;
    private boolean isClean;

    public MetaTileEntityCleanroom(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.CLEANROOM_RECIPES);
        this.recipeMapWorkable = new CleanroomWorkableHandler(this);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(MetaTileEntityHolder holder) {
        return new MetaTileEntityCleanroom(metaTileEntityId);
    }

    @Override
    protected BlockPattern createStructurePattern() {
        StringBuilder wall = new StringBuilder("F");     // FXXXF
        StringBuilder interior = new StringBuilder("X"); // X   X
        StringBuilder edge = new StringBuilder("F");     // FFFFF
        StringBuilder top = new StringBuilder("F");      // FXSXF

        for (int i = 0; i < currentSize * 2 - 1; i++) {
            wall.append("X");
            interior.append(" ");
            edge.append("F");
            if (i == currentSize)
                top.append("S");
            else
                top.append("X");
        }
        wall.append("F");
        interior.append("X");
        edge.append("F");
        top.append("F");

        String[] exteriorSlice = new String[currentSize * 2 + 1];
        String[] interiorSlice = new String[currentSize * 2 + 1];
        String[] controllerSlice = new String[currentSize * 2 + 1];

        exteriorSlice[0] = edge.toString();
        interiorSlice[0] = wall.toString();
        controllerSlice[0] = wall.toString();

        for (int i = 0; i < currentSize * 2 - 1; i++) {
            exteriorSlice[i + 1] = wall.toString();
            interiorSlice[i + 1] = interior.toString();
            controllerSlice[i + 1] = interior.toString();
        }

        exteriorSlice[currentSize * 2] = edge.toString();
        interiorSlice[currentSize * 2] = wall.toString();
        controllerSlice[currentSize * 2] = top.toString();

        System.out.println(Arrays.toString(exteriorSlice));
        System.out.println(Arrays.toString(interiorSlice));
        System.out.println(Arrays.toString(interiorSlice));
        System.out.println(Arrays.toString(controllerSlice));
        System.out.println(Arrays.toString(interiorSlice));
        System.out.println(Arrays.toString(interiorSlice));
        System.out.println(Arrays.toString(exteriorSlice));

        return FactoryBlockPattern.start()
                .aisle(exteriorSlice)
                .aisle(interiorSlice).setRepeatable(currentSize, currentSize)
                .aisle(controllerSlice)
                .aisle(interiorSlice).setRepeatable(currentSize, currentSize)
                .aisle(exteriorSlice)
                .setAmountLimit('d', 0, 8)
                .where('X', maintenancePredicate(getCasingState()).or(abilityPartPredicate(ALLOWED_ABILITIES).or(filterPredicate()).or(doorPredicate())))
                .where('F', borderPredicate())
                .where('d', doorPredicate())
                .where('S', selfPredicate())
                .where(' ', innerPredicate())
                .build();
    }

    private IBlockState getCasingState() {
        return MetaBlocks.CLEANROOM_CASING.getState(BlockCleanroomCasing.casingType.PLASCRETE);
    }

    public static Predicate<BlockWorldState> filterPredicate() {
        return blockWorldState -> {
            IBlockState blockState = blockWorldState.getBlockState();
            if (!(blockState.getBlock() instanceof BlockCleanroomCasing))
                return false;
            BlockCleanroomCasing blockCleanroomCasing = (BlockCleanroomCasing) blockState.getBlock();
            BlockCleanroomCasing.casingType casingType = blockCleanroomCasing.getState(blockState);
            blockWorldState.getMatchContext().increment("filterLevel", casingType.getLevel());
            return true;
        };
    }

    public static Predicate<BlockWorldState> borderPredicate() {
        return blockWorldState -> {
            IBlockState blockState = blockWorldState.getBlockState();
            if (!(blockState.getBlock() instanceof BlockCleanroomCasing))
                return false;
            BlockCleanroomCasing blockCleanroomCasing = (BlockCleanroomCasing) blockState.getBlock();
            BlockCleanroomCasing.casingType casingType = blockCleanroomCasing.getState(blockState);
            return casingType == BlockCleanroomCasing.casingType.PLASCRETE;
        };
    }

    public static Predicate<BlockWorldState> doorPredicate() {
        return blockWorldState -> {
            IBlockState blockState = blockWorldState.getBlockState();
            return blockState.getBlock() instanceof BlockDoor;
        };
    }

    public Predicate<BlockWorldState> innerPredicate() {
        return blockWorldState -> {
            TileEntity tileEntity = blockWorldState.getTileEntity();
            if (!(tileEntity instanceof MetaTileEntityHolder))
                return true;

            MetaTileEntity metaTileEntity = ((MetaTileEntityHolder) tileEntity).getMetaTileEntity();
            if (!(metaTileEntity instanceof ICleanroomReceiver))
                return true;

            ICleanroomReceiver cleanroomReceiver = (ICleanroomReceiver) metaTileEntity;
            if (!cleanroomReceiver.hasCleanroom())
                cleanroomReceiver.setCleanroom(this);
            return true;
        };
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.PLASCRETE;
    }

    @Nonnull
    @Override
    protected OrientedOverlayRenderer getFrontOverlay() {
        return Textures.CLEANROOM_OVERLAY;
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        this.rawLevel = context.getOrDefault("filterLevel", 0);
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.cleanLevel = null;
        this.rawLevel = 0;
        this.isClean = false;
    }

    @Override
    protected void addDisplayText(List<ITextComponent> textList) {
        super.addDisplayText(textList);
        textList.add(new TextComponentTranslation("gregtech.multiblock.cleanroom.size", this.currentSize));
        if (!isStructureFormed()) {
            ITextComponent buttonText = new TextComponentTranslation("gregtech.multiblock.cleanroom.size_modify");
            buttonText.appendText(" ");
            buttonText.appendSibling(AdvancedTextWidget.withButton(new TextComponentString("[-]"), "sub"));
            buttonText.appendText(" ");
            buttonText.appendSibling(AdvancedTextWidget.withButton(new TextComponentString("[+]"), "add"));
            textList.add(buttonText);
        }

        textList.add(new TextComponentTranslation("gregtech.multiblock.cleanroom.cleanliness", new TextComponentString(this.rawLevel + "")
                .setStyle(new Style().setColor(TextFormatting.YELLOW))));

        if (isClean && cleanLevel != null) {
            textList.add(new TextComponentTranslation("gregtech.multiblock.cleanroom.clean_state"));
            textList.add(new TextComponentTranslation("gregtech.multiblock.cleanroom.level", new TextComponentString(cleanLevel.translate())
                    .setStyle(new Style().setColor(TextFormatting.GOLD))));
        } else {
            textList.add(new TextComponentTranslation("gregtech.multiblock.cleanroom.dirty_state").setStyle(new Style().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TextComponentTranslation("gregtech.multiblock.cleanroom.dirty_state.hover_tooltip",
                            new TextComponentString(CleanroomLevel.ISO8.translate()).setStyle(new Style().setColor(TextFormatting.GREEN)))))));
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.3"));
        tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.4"));
        tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.5"));
        tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.6"));
        tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.7"));
    }

    protected void setCleanRecipeCompletion(boolean state) {
        this.isClean = state;
        if (isClean)
            this.cleanLevel = calculateCleanLevel(this.rawLevel);
        else
            this.cleanLevel = null;
    }

    private CleanroomLevel calculateCleanLevel(int rawLevel) {
        if (rawLevel >= 1024)
            return CleanroomLevel.ISO1;
        else if (rawLevel >= 512)
            return CleanroomLevel.ISO2;
        else if (rawLevel >= 256)
            return CleanroomLevel.ISO3;
        else if (rawLevel >= 128)
            return CleanroomLevel.ISO4;
        else if (rawLevel >= 64)
            return CleanroomLevel.ISO5;
        else if (rawLevel >= 32)
            return CleanroomLevel.ISO6;
        else if (rawLevel >= 16)
            return CleanroomLevel.ISO7;
        else if (rawLevel >= 8)
            return CleanroomLevel.ISO8;
        return null;
    }

    @Override
    public CleanroomLevel getCleanRoomLevel() {
        return this.cleanLevel;
    }

    protected int getEnergyUsage() {
        if (rawLevel == GTValues.V[GTUtility.getTierByVoltage(rawLevel)])
            return (int) GTValues.VA[GTUtility.getTierByVoltage(rawLevel)];
        return rawLevel;
    }

    @Override
    public MetaTileEntity getCleanroomTileEntity() {
        return this;
    }

    public void setCurrentSize(int size) {
        if (this.currentSize == size || size < MIN_RADIUS || size > MAX_RADIUS)
            return;
        this.currentSize = size;
        reinitializeStructurePattern();
        checkStructurePattern();
        writeCustomData(1, buf->{
            buf.writeInt(size);
        });
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == 1) {
            this.currentSize = buf.readInt();
            this.reinitializeStructurePattern();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        data.setInteger("currentSize", this.currentSize);
        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.currentSize = data.hasKey("currentSize") ? data.getInteger("currentSize") : this.currentSize;
        reinitializeStructurePattern();
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeInt(this.currentSize);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.currentSize = buf.readInt();
    }

    protected static class CleanroomWorkableHandler extends MultiblockRecipeLogic {

        private static final Recipe cleanroomRecipe = new Recipe(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 600, 1, true);

        public CleanroomWorkableHandler(RecipeMapMultiblockController metaTileEntity) {
            super(metaTileEntity);
        }

        private MetaTileEntityCleanroom getCleanroom() {
            return (MetaTileEntityCleanroom) metaTileEntity;
        }

        @Override
        protected void trySearchNewRecipe() {
            // do not run recipes when there are more than 5 maintenance problems
            MultiblockWithDisplayBase controller = (MultiblockWithDisplayBase) metaTileEntity;
            if (controller.hasMaintenanceMechanics() && controller.getNumMaintenanceProblems() > 5)
                return;

            setupRecipe(cleanroomRecipe);
        }

        @Override
        protected void setupRecipe(Recipe recipe) {
            int[] overclock = calculateOverclock(getCleanroom().getEnergyUsage(), getOverclockVoltage(), recipe.getDuration());
            this.progressTime = 1;
            setMaxProgress(overclock[1]);
            this.recipeEUt = overclock[0];

            // prevent NBT writing NPE on world load
            this.itemOutputs = NonNullList.create();
            this.fluidOutputs = new ArrayList<>();

            if (this.wasActiveAndNeedsUpdate) {
                this.wasActiveAndNeedsUpdate = false;
            } else {
                this.setActive(true);
            }
        }

        @Override
        public long getOverclockVoltage() {
            // the cleanroom overclock voltage is 0 without this override
            return this.getMaxVoltage();
        }

        @Override
        protected int[] calculateOverclock(int EUt, long voltage, int duration) {
            // apply maintenance penalties
            int numMaintenanceProblems = (this.metaTileEntity instanceof MultiblockWithDisplayBase) ?
                    ((MultiblockWithDisplayBase) metaTileEntity).getNumMaintenanceProblems() : 0;

            int tier = getOverclockingTier(voltage);

            // Cannot overclock
            if (GTValues.V[tier] <= EUt || tier == 0)
                return new int[]{EUt, duration};

            int resultEUt = EUt;
            double resultDuration = duration;
            double divisor = ConfigHolder.U.overclockDivisor;
            int maxOverclocks = tier - 1; // exclude ULV overclocking

            //do not overclock further if duration is already too small
            while (resultDuration >= 3 && resultEUt <= GTValues.V[tier - 1] && maxOverclocks != 0) {
                resultEUt *= 4;
                resultDuration /= divisor;
                maxOverclocks--;
            }

            resultDuration *= (1 + 0.1 * numMaintenanceProblems);

            return new int[]{resultEUt, (int) Math.ceil(resultDuration)};
        }

        @Override
        protected void completeRecipe() {
            // increase total on time
            MultiblockWithDisplayBase controller = (MultiblockWithDisplayBase) metaTileEntity;
            if (controller.hasMaintenanceMechanics())
                controller.calculateMaintenance(this.progressTime);

            // complete cleaning
            if (!getCleanroom().isClean)
                getCleanroom().setCleanRecipeCompletion(true);

            this.progressTime = 0;
            setMaxProgress(0);
            this.recipeEUt = 0;

            // prevent NBT writing NPE on world load
            this.fluidOutputs = null;
            this.itemOutputs = null;

            this.hasNotEnoughEnergy = false;
            this.wasActiveAndNeedsUpdate = true;
        }

        @Override
        protected void updateRecipeProgress() {
            super.updateRecipeProgress();

            if (isHasNotEnoughEnergy() && getCleanroom().isClean)
                getCleanroom().setCleanRecipeCompletion(false);
        }
    }
}