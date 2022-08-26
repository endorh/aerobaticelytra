package endorh.aerobaticelytra.common.tile;

import endorh.aerobaticelytra.client.block.BrokenLeavesBlockModel;
import endorh.aerobaticelytra.common.block.BrokenLeavesBlock;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants.BlockFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Tile Entity for {@link BrokenLeavesBlock}
 * used to store the leaves block it replaces.
 *
 * @see BrokenLeavesBlockModel
 */
public class BrokenLeavesTileEntity extends TileEntity {
	public static final String NAME = "broken_leaves_te";
	public static final String TAG_REPLACED_LEAVES = "ReplacedLeaves";
	
	public BlockState replacedLeaves = null;
	
	public BrokenLeavesTileEntity() {
		super(ModTileEntities.BROKEN_LEAVES_TE);
	}
	
	@Nullable
	@Override
	public SUpdateTileEntityPacket getUpdatePacket() {
		return new SUpdateTileEntityPacket(worldPosition, 0, getUpdateTag());
	}
	
	@Override public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
		super.onDataPacket(net, pkt);
		CompoundNBT updateNBT = pkt.getTag();
		replacedLeaves = NBTUtil.readBlockState(updateNBT.getCompound(TAG_REPLACED_LEAVES));
		final BlockState state = getBlockState();
		assert level != null;
		level.sendBlockUpdated(worldPosition, state, state, BlockFlags.DEFAULT_AND_RERENDER);
	}
	
	@Override public @NotNull CompoundNBT save(@NotNull CompoundNBT compound) {
		CompoundNBT nbt = super.save(compound);
		if (replacedLeaves != null)
			nbt.put(TAG_REPLACED_LEAVES, NBTUtil.writeBlockState(replacedLeaves));
		return nbt;
	}
	
	@Override public void load(@NotNull BlockState state, @NotNull CompoundNBT nbt) {
		super.load(state, nbt);
		if (nbt.contains(TAG_REPLACED_LEAVES))
			replacedLeaves = NBTUtil.readBlockState(nbt.getCompound(TAG_REPLACED_LEAVES));
	}
	
	@Override public @NotNull CompoundNBT getUpdateTag() {
		CompoundNBT tag = super.getUpdateTag();
		if (replacedLeaves != null)
			tag.put(TAG_REPLACED_LEAVES, NBTUtil.writeBlockState(replacedLeaves));
		return tag;
	}
	
	@Override public void handleUpdateTag(BlockState state, CompoundNBT tag) {
		super.handleUpdateTag(state, tag);
		if (tag.contains(TAG_REPLACED_LEAVES))
			replacedLeaves = NBTUtil.readBlockState(tag.getCompound(TAG_REPLACED_LEAVES));
	}
}