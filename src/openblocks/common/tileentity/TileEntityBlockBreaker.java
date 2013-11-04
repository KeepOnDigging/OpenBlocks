package openblocks.common.tileentity;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.common.ForgeHooks;
import openblocks.common.GenericInventory;
import openblocks.common.api.IAwareTile;
import openblocks.sync.ISyncableObject;
import openblocks.sync.SyncableBoolean;
import openblocks.utils.BlockUtils;
import openblocks.utils.InventoryUtils;
import openblocks.utils.OpenBlocksFakePlayer;

public class TileEntityBlockBreaker extends NetworkedTileEntity
		implements IAwareTile, IInventory {

	public enum Slots {
		buffer
	}

	private GenericInventory fakeInventory = new GenericInventory("blockbreaker", true, 1);
	private boolean _redstoneSignal;
	private int _redstoneAnimTimer;
	private SyncableBoolean activated;
	
	public TileEntityBlockBreaker() {
		addSyncedObject(activated = new SyncableBoolean(false));
	}
	
	@SideOnly(Side.CLIENT)
	public boolean isActivated() {
		return activated.getValue();
	}
	
	public void updateEntity() {
		super.updateEntity();
		if(!worldObj.isRemote) {
			if(activated.getValue() && _redstoneAnimTimer > 0) 
				_redstoneAnimTimer--;
			if(activated.getValue() && _redstoneAnimTimer <= 0) {
				activated.setValue(false);
			}

			sync();
		}
	}

	public void setRedstoneSignal(boolean redstoneSignal) {
		if (redstoneSignal != _redstoneSignal) {
			_redstoneSignal = redstoneSignal;
			if (_redstoneSignal) {
				if(!worldObj.isRemote) {
					_redstoneAnimTimer = 5;
					activated.setValue(true);
					sync();
				}
				breakBlock();
			}
		}
	}
	

	private void breakBlock() {
		if (worldObj.isRemote) return;

		ForgeDirection direction = getRotation();
		int x = xCoord + direction.offsetX, y = yCoord + direction.offsetY, z = zCoord
				+ direction.offsetZ;

		if (worldObj.blockExists(x, y, z)) {
			int blockId = worldObj.getBlockId(x, y, z);
			Block block = Block.blocksList[blockId];
			if (block != null) {
				int metadata = worldObj.getBlockMetadata(x, y, z);
				if (block != Block.bedrock
						&& block.getBlockHardness(worldObj, z, y, z) > -1.0F) {
					EntityPlayer fakePlayer = OpenBlocksFakePlayer.getPlayerForWorld(worldObj);
					fakePlayer.inventory.currentItem = 0;
					fakePlayer.inventory.setInventorySlotContents(0, new ItemStack(Item.pickaxeDiamond));
					if (ForgeHooks.canHarvestBlock(block, fakePlayer, metadata)) {
						ArrayList<ItemStack> items = block.getBlockDropped(worldObj, x, y, z, metadata, 0);
						if (items != null) {
							ForgeDirection back = direction.getOpposite();
							ejectAt(worldObj,
									xCoord + back.offsetX,
									yCoord + back.offsetY,
									zCoord + back.offsetZ,
									back, items);
						}
					}
                    worldObj.playAuxSFX(2001, x, y, z, blockId + (metadata << 12));
					worldObj.setBlockToAir(x, y, z);
				}
			}
		}
	}

	public void ejectAt(World world, int x, int y, int z, ForgeDirection direction, ArrayList<ItemStack> itemStacks) {

		TileEntity targetInventory = getTileInDirection(direction);
		for (ItemStack stack : itemStacks) {
			// if there's any stack in our buffer slot, eject it. Why is it there?
			ItemStack currentStack = fakeInventory.getStackInSlot(Slots.buffer);
			if (currentStack != null) {
				BlockUtils.ejectItemInDirection(world, x, y, z, direction, currentStack);
			}

			// clear the buffer slot
			fakeInventory.setInventorySlotContents(Slots.buffer.ordinal(), stack);

			// push the item out into a pipe or inventory
			InventoryUtils.moveItemInto(this, Slots.buffer.ordinal(), targetInventory, -1, 64, direction, true);
			// if there's anything left for whatever reason (maybe no inventory)
			ItemStack buffer = fakeInventory.getStackInSlot(Slots.buffer);
			if (buffer != null) {
				// eject it
				BlockUtils.ejectItemInDirection(world, x, y, z, direction, buffer);
				fakeInventory.setInventorySlotContents(Slots.buffer.ordinal(), null);
			}
		}
	}

	@Override
	public void onBlockBroken() {}

	@Override
	public void onBlockAdded() {}

	@Override
	public boolean onBlockActivated(EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		return false;
	}

	@Override
	public void onNeighbourChanged(int blockId) {
		if (!worldObj.isRemote) {
			setRedstoneSignal(worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord));
		}
	}

	@Override
	public void onBlockPlacedBy(EntityPlayer player, ForgeDirection side, ItemStack stack, float hitX, float hitY, float hitZ) {
		setRotation(BlockUtils.get3dOrientation(player));
		sync();
	}

	@Override
	public boolean onBlockEventReceived(int eventId, int eventParam) {
		return false;
	}

	@Override
	public int getSizeInventory() {
		return fakeInventory.getSizeInventory();
	}

	@Override
	public ItemStack getStackInSlot(int i) {
		return fakeInventory.getStackInSlot(i);
	}

	@Override
	public ItemStack decrStackSize(int i, int j) {
		return fakeInventory.decrStackSize(i, j);
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int i) {
		return fakeInventory.getStackInSlotOnClosing(i);
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack itemstack) {
		fakeInventory.setInventorySlotContents(i, itemstack);
	}

	@Override
	public String getInvName() {
		return fakeInventory.getInvName();
	}

	@Override
	public boolean isInvNameLocalized() {
		return fakeInventory.isInvNameLocalized();
	}

	@Override
	public int getInventoryStackLimit() {
		return fakeInventory.getInventoryStackLimit();
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer entityplayer) {
		return fakeInventory.isUseableByPlayer(entityplayer);
	}

	@Override
	public void openChest() {}

	@Override
	public void closeChest() {}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemstack) {
		return false;
	}

	@Override
	public void onSynced(List<ISyncableObject> changes) {
		if(changes.contains(activated)) {
			worldObj.markBlockForRenderUpdate(xCoord, yCoord, zCoord);
		}
	}

}
