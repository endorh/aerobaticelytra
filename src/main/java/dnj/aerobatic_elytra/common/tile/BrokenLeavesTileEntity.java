package dnj.aerobatic_elytra.common.tile;

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
 * Tile Entity for {@link dnj.aerobatic_elytra.common.block.BrokenLeavesBlock}
 * used to store the leaves block it replaces.
 *
 * @see dnj.aerobatic_elytra.client.block.BrokenLeavesBlockModel
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
		return new SUpdateTileEntityPacket(pos, 0, getUpdateTag());
	}
	
	@Override public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
		super.onDataPacket(net, pkt);
		CompoundNBT updateNBT = pkt.getNbtCompound();
		replacedLeaves = NBTUtil.readBlockState(updateNBT.getCompound(TAG_REPLACED_LEAVES));
		final BlockState state = getBlockState();
		assert world != null;
		world.notifyBlockUpdate(pos, state, state, BlockFlags.DEFAULT_AND_RERENDER);
	}
	
	@Override public @NotNull CompoundNBT write(@NotNull CompoundNBT compound) {
		CompoundNBT nbt = super.write(compound);
		if (replacedLeaves != null)
			nbt.put(TAG_REPLACED_LEAVES, NBTUtil.writeBlockState(replacedLeaves));
		return nbt;
	}
	
	@Override public void read(@NotNull BlockState state, @NotNull CompoundNBT nbt) {
		super.read(state, nbt);
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
