package com.simibubi.create.modules.contraptions.relays.belt;

import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IEnviromentBlockReader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
class BeltColor implements IBlockColor {

	@Override
	public int getColor(BlockState state, IEnviromentBlockReader reader, BlockPos pos, int layer) {
		TileEntity tileEntity = reader.getTileEntity(pos);
		if (tileEntity instanceof BeltTileEntity) {
			BeltTileEntity te = (BeltTileEntity) tileEntity;
			if (te.color != -1)
				return te.color;
		}
		return 0;
	}

}