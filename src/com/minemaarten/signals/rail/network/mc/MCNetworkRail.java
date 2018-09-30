package com.minemaarten.signals.rail.network.mc;

import io.netty.buffer.ByteBuf;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRailBase.EnumRailDirection;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import com.google.common.collect.ImmutableList;
import com.minemaarten.signals.lib.EnumSetUtils;
import com.minemaarten.signals.rail.network.EnumHeading;
import com.minemaarten.signals.rail.network.NetworkRail;
import com.minemaarten.signals.rail.network.mc.NetworkSerializer.EnumNetworkObject;

public class MCNetworkRail extends NetworkRail<MCPos> implements ISerializableNetworkObject{

    private static final Object NORMAL_RAIL_TYPE = new Object();
    private static final String NORMAL_RAIL_STRING = "r";
    private static final EnumSet<EnumHeading> STANDARD_NEIGHBOR_HEADINGS = EnumSet.allOf(EnumHeading.class);
    private static final EnumRailDirection[] ALL_RAIL_DIRECTIONS_ARRAY = EnumRailDirection.values();
    private static final EnumSet<EnumRailDirection> ALL_RAIL_DIRECTIONS = EnumSet.allOf(EnumRailDirection.class);

    private final String railType; //The type of rail, usually the block registry name, but NORMAL_RAIL_TYPE for Blocks.RAIL to save memory for serialization
    private final EnumRailDirection curDir; //Used client-side for rendering rail sections.
    private final ImmutableList<MCPos> potentialRailNeighbors, potentialObjectNeighbors;
    private final EnumSet<EnumRailDirection> validRailDirs;
    private final EnumSet<EnumHeading> validNeighborHeadings;
    private final EnumMap<EnumHeading, EnumSet<EnumHeading>> entryDirToNeighborHeadings;
    private final EnumMap<EnumHeading, Set<MCPos>> headingToNeighbors;

    public MCNetworkRail(MCPos pos, Block railBlock, EnumRailDirection curDir, EnumSet<EnumRailDirection> validRailDirs){
        this(pos, railBlock == Blocks.RAIL ? null : railBlock.getRegistryName().toString(), curDir, validRailDirs);
    }

    public MCNetworkRail(MCPos pos, String railType, EnumRailDirection curDir, EnumSet<EnumRailDirection> validRailDirs){
        super(pos);
        this.railType = railType;
        this.curDir = curDir;

        //If standard rail, use mem cache
        if(validRailDirs.size() == ALL_RAIL_DIRECTIONS.size()) {
            this.validRailDirs = ALL_RAIL_DIRECTIONS;
            this.validNeighborHeadings = STANDARD_NEIGHBOR_HEADINGS;

        } else { //Else, compute
            this.validRailDirs = validRailDirs;
            this.validNeighborHeadings = getValidHeadings(validRailDirs);

        }
        potentialObjectNeighbors = computePotentialObjectNeighbors();
        potentialRailNeighbors = ImmutableList.copyOf(potentialObjectNeighbors.stream().flatMap(this::plusOneMinusOneHeight).collect(Collectors.toList()));
        entryDirToNeighborHeadings = computeExitsForEntries(validRailDirs);

        headingToNeighbors = new EnumMap<>(EnumHeading.class);
        for(EnumHeading heading : validNeighborHeadings) {
            headingToNeighbors.put(heading, plusOneMinusOneHeight(pos.offset(heading)).collect(Collectors.toSet()));
        }
    }

    public static MCNetworkRail fromTag(NBTTagCompound tag){
        return fromTag(tag, MCNetworkRail::new);
    }

    protected static <T> T fromTag(NBTTagCompound tag, IRailCreator<T> factory){
        EnumRailDirection curDir = ALL_RAIL_DIRECTIONS_ARRAY[tag.getByte("c")];
        if(tag.hasKey("t")) {
            EnumSet<EnumRailDirection> validRailDirs = EnumSetUtils.toEnumSet(EnumRailDirection.class, ALL_RAIL_DIRECTIONS_ARRAY, tag.getShort("r"));
            return factory.create(new MCPos(tag), tag.getString("t"), curDir, validRailDirs);
        } else {
            return factory.create(new MCPos(tag), (String)null, curDir, ALL_RAIL_DIRECTIONS);
        }
    }

    public static interface IRailCreator<T> {
        public T create(MCPos pos, String railType, EnumRailDirection curDir, EnumSet<EnumRailDirection> validRailDirs);
    }

    public static MCNetworkRail fromByteBuf(ByteBuf b){
        MCPos pos = new MCPos(b);
        EnumRailDirection curDir = ALL_RAIL_DIRECTIONS_ARRAY[b.readByte()];
        String type = ByteBufUtils.readUTF8String(b);
        if(type.equals(NORMAL_RAIL_STRING)) {
            return new MCNetworkRail(pos, (String)null, curDir, ALL_RAIL_DIRECTIONS);
        } else {
            EnumSet<EnumRailDirection> validRailDirs = EnumSetUtils.toEnumSet(EnumRailDirection.class, ALL_RAIL_DIRECTIONS_ARRAY, b.readShort());
            return new MCNetworkRail(pos, type, curDir, validRailDirs);
        }
    }

    public static <T> T fromByteBuf(ByteBuf b, IRailCreator<T> factory){
        MCPos pos = new MCPos(b);
        EnumRailDirection curDir = ALL_RAIL_DIRECTIONS_ARRAY[b.readByte()];
        String type = ByteBufUtils.readUTF8String(b);
        if(type.equals(NORMAL_RAIL_STRING)) {
            return factory.create(pos, (String)null, curDir, ALL_RAIL_DIRECTIONS);
        } else {
            EnumSet<EnumRailDirection> validRailDirs = EnumSetUtils.toEnumSet(EnumRailDirection.class, ALL_RAIL_DIRECTIONS_ARRAY, b.readShort());
            return factory.create(pos, type, curDir, validRailDirs);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag){
        getPos().writeToNBT(tag);
        tag.setByte("c", (byte)curDir.ordinal());
        if(railType != null) {
            tag.setString("t", railType);
            tag.setShort("r", EnumSetUtils.toShort(validRailDirs));
        }
    }

    @Override
    public void writeToBuf(ByteBuf b){
        getPos().writeToBuf(b);
        b.writeByte(curDir.ordinal());
        ByteBufUtils.writeUTF8String(b, railType == null ? NORMAL_RAIL_STRING : railType);
        if(railType != null) {
            b.writeShort(EnumSetUtils.toShort(validRailDirs));
        }
    }

    private Stream<MCPos> plusOneMinusOneHeight(MCPos pos){
        return Stream.of(pos.offset(EnumFacing.UP), pos, pos.offset(EnumFacing.DOWN));
    }

    protected ImmutableList<MCPos> computePotentialObjectNeighbors(){
        return ImmutableList.copyOf(potentialObjectNeighborsStream().collect(Collectors.toList()));
    }

    protected Stream<MCPos> potentialObjectNeighborsStream(){
        return validNeighborHeadings.stream().map(getPos()::offset);
    }

    @Override
    public Object getRailType(){
        return railType == null ? NORMAL_RAIL_TYPE : railType;
    }

    public EnumRailDirection getCurDir(){
        return curDir;
    }

    @Override
    public List<MCPos> getPotentialNeighborRailLocations(){
        return potentialRailNeighbors;
    }

    @Override
    public Collection<MCPos> getPotentialNeighborRailLocations(EnumHeading side){
        return headingToNeighbors.get(side);
    }

    @Override
    public EnumSet<EnumHeading> getPotentialNeighborRailHeadings(){
        return validNeighborHeadings;
    }

    @Override
    public List<MCPos> getPotentialNeighborObjectLocations(){
        return potentialObjectNeighbors;
    }

    private EnumMap<EnumHeading, EnumSet<EnumHeading>> computeExitsForEntries(EnumSet<EnumRailDirection> validRailDirs){
        EnumMap<EnumHeading, EnumSet<EnumHeading>> exitsForEntries = new EnumMap<>(EnumHeading.class);
        for(EnumHeading heading : EnumHeading.VALUES) {
            exitsForEntries.put(heading, EnumSet.noneOf(EnumHeading.class));
        }

        for(EnumRailDirection railDir : validRailDirs) {
            EnumSet<EnumHeading> railDirDirs = getDirections(railDir);
            for(EnumHeading heading : railDirDirs) {
                exitsForEntries.get(heading.getOpposite()).addAll(railDirDirs);
            }
        }

        return exitsForEntries;
    }

    private static EnumSet<EnumHeading> getValidHeadings(EnumSet<EnumRailDirection> validRailDirs){
        EnumSet<EnumHeading> headings = EnumSet.noneOf(EnumHeading.class);
        for(EnumRailDirection dir : validRailDirs) {
            headings.addAll(getDirections(dir));
        }
        return headings;
    }

    private static EnumSet<EnumHeading> getDirections(EnumRailDirection railDir){
        switch(railDir){
            case NORTH_SOUTH:
            case ASCENDING_NORTH:
            case ASCENDING_SOUTH:
                return EnumSet.of(EnumHeading.NORTH, EnumHeading.SOUTH);
            case EAST_WEST:
            case ASCENDING_EAST:
            case ASCENDING_WEST:
                return EnumSet.of(EnumHeading.EAST, EnumHeading.WEST);
            case SOUTH_EAST:
                return EnumSet.of(EnumHeading.SOUTH, EnumHeading.EAST);
            case SOUTH_WEST:
                return EnumSet.of(EnumHeading.SOUTH, EnumHeading.WEST);
            case NORTH_WEST:
                return EnumSet.of(EnumHeading.NORTH, EnumHeading.WEST);
            case NORTH_EAST:
                return EnumSet.of(EnumHeading.NORTH, EnumHeading.EAST);
            default:
                return EnumSet.noneOf(EnumHeading.class);
        }
    }

    @Override
    public EnumNetworkObject getType(){
        return EnumNetworkObject.RAIL;
    }

    @Override
    public boolean equals(Object obj){
        if(obj instanceof MCNetworkRail) {
            MCNetworkRail other = (MCNetworkRail)obj;
            return super.equals(obj) && validRailDirs.equals(other.validRailDirs) && curDir.isAscending() == other.curDir.isAscending(); //Only check ascending because that effects rendering
        } else {
            return false;
        }
    }

    @Override
    public int hashCode(){
        return super.hashCode() * 31 + validRailDirs.hashCode() * 2 + (curDir.isAscending() ? 1 : 0);
    }

    @Override
    public EnumSet<EnumHeading> getPathfindHeading(EnumHeading entryDir){
        if(entryDir == null) return EnumSet.noneOf(EnumHeading.class);
        return entryDirToNeighborHeadings.get(entryDir);
    }
}
