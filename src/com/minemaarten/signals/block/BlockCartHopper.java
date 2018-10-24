package com.minemaarten.signals.block;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.minemaarten.signals.proxy.CommonProxy.EnumGuiId;
import com.minemaarten.signals.tileentity.TileEntityCartHopper;

public class BlockCartHopper extends BlockBase{

    public BlockCartHopper(){
        super(TileEntityCartHopper.class, "cart_hopper");
    }

    @Override
    public EnumGuiId getGuiID(){
        return EnumGuiId.CART_HOPPER;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess worldIn, BlockPos pos, EnumFacing side){
        if(side != EnumFacing.UP && side != EnumFacing.DOWN) return 0;
        TileEntityCartHopper cartHopper = (TileEntityCartHopper)worldIn.getTileEntity(pos);
        return cartHopper.emitsRedstone() ? 15 : 0;
    }

    @Override
    public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, @Nullable EnumFacing side){
        return side == EnumFacing.UP || side == EnumFacing.DOWN;
    }

    @Override
    public boolean canProvidePower(IBlockState state){
        return true;
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState state){
        return true;
    }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos){
        return ((TileEntityCartHopper)world.getTileEntity(pos)).getComparatorInputOverride();
    }

    @Override
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos){
        super.neighborChanged(state, worldIn, pos, blockIn, fromPos);
        if(!worldIn.isRemote) {
            ((TileEntityCartHopper)worldIn.getTileEntity(pos)).updateCartAbove();
        }
    }
}
