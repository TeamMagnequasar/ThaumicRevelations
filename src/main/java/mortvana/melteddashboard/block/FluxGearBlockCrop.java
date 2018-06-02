package mortvana.melteddashboard.block;

import java.util.*;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraftforge.common.util.ForgeDirection;

import mortvana.melteddashboard.util.helpers.ItemHelper;
import mortvana.melteddashboard.util.helpers.WorldHelper;

import mortvana.thaumrev.common.ThaumRevConfig;

public abstract class FluxGearBlockCrop extends FluxGearBlockPlant implements ICrop {

	public FluxGearBlockCrop() {
		super(Material.plants);
		setTickRandomly(true);
		setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 0.25F, 1.0F);
		setCreativeTab(null);
		setHardness(0.0F);
		setStepSound(soundTypeGrass);
		disableStats();
	}

	@Override
	public void updateTick(World world, int x, int y, int z, Random random) {
		checkAndDropBlock(world, x, y, z);

		int light = world.getBlockLightValue(x, y, z);
		if (light >= 8) {
			int meta = world.getBlockMetadata(x, y, z);

			if (getMaxGrowth(meta) != meta) {
				float rate = getGrowthRate(world, x, y, z, meta, light);

				if (rate > 0.0F && random.nextInt((int) (25.0F / rate) + 1) == 0) {
					meta++;
					world.setBlockMetadataWithNotify(x, y, z, meta, 2);
				}
			}
		}
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float posX, float posY, float posZ) {
		if (ThaumRevConfig.rightClickHarvest) {
			if (world.isRemote) {
				return true;
			}
			int meta = world.getBlockMetadata(x, y, z);
			if (getMaxGrowth(meta) == meta) {
				world.setBlock(x, y, z, this, getHarvestMeta(meta), 3);
				EntityItem item;
				for (ItemStack produce : getProduce(world, x, y, z, 0)) {
					item = new EntityItem(world, player.posX, player.posY - 1.0D, player.posZ, produce);
					world.spawnEntityInWorld(item);
					item.onCollideWithPlayer(player);
				}
				ItemStack seed = getSeed(world, x, y, z, 0);
				seed = ItemHelper.cloneStack(seed, seed.stackSize - 1);
				item = new EntityItem(world, player.posX, player.posY - 1.0D, player.posZ, seed);
				world.spawnEntityInWorld(item);
				item.onCollideWithPlayer(player);
				return true;
			}
		}
		return false;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Item getItem(World world, int x, int y, int z) {
		return getSeed(world, x, y, z, 0).getItem();
	}

	@Override
	public int getDamageValue (World world, int x, int y, int z) {
		return getSeed(world, x, y, z, 0).getItemDamage();
	}

	@Override
	public int getPlantMetadata (IBlockAccess world, int x, int y, int z) {
		return world.getBlockMetadata(x, y, z) < 8 ? 0 : 8;
	}

	@Override
	public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
		ArrayList<ItemStack> ret = getProduce(world, x, y, z, 0);
		ret.add(getSeed(world, x, y, z, fortune));
		return ret;
	}

	@Override
	public void getSubBlocks(Item item, CreativeTabs tab, List list) { /* Don't add to a creative tab */ }

	/** IGrowable **/
	@Override //isNotFullyGrown
	public boolean func_149851_a(World world, int x, int y, int z, boolean isRemote) {
		int meta = world.getBlockMetadata(x, y, z);

		return getMaxGrowth(meta) != meta;
	}

	@Override //canUseBonemeal
	public boolean func_149852_a(World world, Random random, int x, int y, int z) {
		return true;
	}

	@Override //onUseBonemeal
	public void func_149853_b(World world, Random random, int x, int y, int z) {
		int meta = world.getBlockMetadata(x, y, z);
		if (getMaxGrowth(meta) != meta) {
			int growth = world.rand.nextInt(4) + 2;
			meta = meta + growth > getMaxGrowth(meta) ? getMaxGrowth(meta) : meta + growth;
			world.setBlockMetadataWithNotify(x, y, z, meta, 3);
		}
	}

	/** ICrop **/
	@Override
	public int getStartGrowth(int meta) {
		return meta < 8 ? 0 : 8;
	}

	@Override
	public int getMaxGrowth(int meta) {
		return meta < 8 ? 7 : 15;
	}

	@Override
	public float getGrowthRate(World world, int x, int y, int z, int meta, int light) {
		float growth = 0.25F * (light - 7);

		if (isValidSoil(world, x, y, z) && light >= requiredSun(meta)) {
			growth += ((WorldHelper.getSunlight(world, x, y, z) - requiredSun(meta)) * .125F);
			if (isCrowded(world, x, y, z)) {
				growth /= 2.0F;
			}

			if (world.canBlockSeeTheSky(x, y, z) || WorldHelper.getSunlight(world, x, y, z) <= requiredSun(meta)) {
				growth += .50F;
			}

			Block soil = world.getBlock(x, y - 1, z);
			if (soil != null && soil.isFertile(world, x, y - 1, z)) {
				growth *= 2F;
			}
			growth += 1.0F;
		} else {
			growth = 0.0F;
		}
		return growth;
	}

	@Override
	public int requiredSun(int meta) {
		return 4;
	}

	@Override
	public boolean isCrowded(IBlockAccess world, int x, int y, int z) {
		int meta = world.getBlockMetadata(x, y, z);
		boolean a = isSameCrop(world, x - 1, y, z, meta) && isSameCrop(world, x + 1, y, z, meta) && isSameCrop(world, x, y, z - 1, meta) && isSameCrop(world, x, y, z + 1, meta);
		boolean b = isSameCrop(world, x - 1, y, z - 1, meta) && isSameCrop(world, x + 1, y, z + 1, meta) && isSameCrop(world, x + 1, y, z - 1, meta) && isSameCrop(world, x - 1, y, z + 1, meta);
		return a || b;
	}

	@Override
	public boolean isSameCrop(IBlockAccess world, int x, int y, int z, int meta) {
		int comp = world.getBlockMetadata(x, y, z);
		return (meta < 8 && comp < 8) || (meta >= 8 && comp >= 8);
	}

	@Override
	public boolean isValidSoil(IBlockAccess world, int x, int y, int z) {
		return world.getBlock(x, y, z).canSustainPlant(world, x, y, z, ForgeDirection.UP, this);
	}
}
