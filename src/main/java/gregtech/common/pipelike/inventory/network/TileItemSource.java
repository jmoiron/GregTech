package gregtech.common.pipelike.inventory.network;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;

import java.lang.ref.WeakReference;

public class TileItemSource extends InventoryItemSource {

    private final BlockPos blockPos;
    private final EnumFacing accessSide;
    private final BlockPos accessedBlockPos;
    private WeakReference<TileEntity> cachedTileEntity = new WeakReference<>(null);

    public TileItemSource(World world, BlockPos blockPos, EnumFacing accessSide) {
        super(world);
        this.blockPos = blockPos;
        this.accessSide = accessSide;
        this.accessedBlockPos = blockPos.offset(accessSide);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public EnumFacing getAccessSide() {
        return accessSide;
    }

    public BlockPos getAccessedBlockPos() {
        return accessedBlockPos;
    }

    @Override
    protected IItemHandler computeItemHandler() {
        if (world.isBlockLoaded(accessedBlockPos)) {
            //we handle unloaded blocks as empty item handlers
            //so when they are loaded, they are refreshed and handled correctly
            return EmptyHandler.INSTANCE;
        }
        //use cached tile entity as long as it's valid and has same position (just in case of frames etc)
        TileEntity tileEntity = cachedTileEntity.get();
        if (tileEntity == null || tileEntity.isInvalid() || !tileEntity.getPos().equals(accessedBlockPos)) {
            tileEntity = world.getTileEntity(accessedBlockPos);
            if (tileEntity == null) {
                //if tile entity doesn't exist anymore, we are invalid now
                //return null which will be handled as INVALID
                return null;
            }
            //update cached tile entity
            this.cachedTileEntity = new WeakReference<>(tileEntity);
        }
        //fetch capability from tile entity
        //if it returns null, item handler info will be removed
        //block should emit block update once it obtains capability again,
        //so handler info will be recreated accordingly
        return tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, accessSide.getOpposite());
    }
}