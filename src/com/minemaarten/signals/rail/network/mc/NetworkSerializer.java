package com.minemaarten.signals.rail.network.mc;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.google.common.collect.ImmutableMap;
import com.minemaarten.signals.rail.network.INetworkObject;
import com.minemaarten.signals.rail.network.NetworkObject;
import com.minemaarten.signals.rail.network.RailNetwork;

public class NetworkSerializer{

    public static enum EnumNetworkObject{
        RAIL, SIGNAL, REMOVAL_MARKER, RAIL_LINK, STATION, TELEPORT_RAIL;

        public static final EnumNetworkObject[] VALUES = values();
    }

    private ISerializableNetworkObject asSerializable(INetworkObject<MCPos> obj){
        if(obj instanceof ISerializableNetworkObject) {
            return (ISerializableNetworkObject)obj;
        } else {
            throw new IllegalStateException("Object " + obj + " of type " + obj.getClass() + " does not implement ISerializableNetworkObject!");
        }
    }

    public void writeToTag(RailNetwork<MCPos> network, NBTTagCompound tag){
        NBTTagList list = new NBTTagList();
        for(INetworkObject<MCPos> obj : network.railObjects.getAllNetworkObjects().values()) {
            NBTTagCompound t = new NBTTagCompound();
            writeToTag(obj, t);
            list.appendTag(t);
        }
        tag.setTag("objects", list);
    }

    public RailNetwork<MCPos> loadNetworkFromTag(NBTTagCompound tag){
        if(tag.hasKey("objects")) {
            List<INetworkObject<MCPos>> objects = new ArrayList<>();
            NBTTagList list = tag.getTagList("objects", Constants.NBT.TAG_COMPOUND);
            for(int i = 0; i < list.tagCount(); i++) {
                objects.add(loadFromTag(list.getCompoundTagAt(i)));
            }
            return new RailNetwork<>(objects);
        } else {
            return new RailNetwork<>(ImmutableMap.<MCPos, INetworkObject<MCPos>> of());
        }
    }

    private void writeToTag(INetworkObject<MCPos> obj, NBTTagCompound tag){
        writeToTag(asSerializable(obj), tag);
    }

    private void writeToTag(ISerializableNetworkObject obj, NBTTagCompound tag){
        tag.setByte("id", (byte)obj.getType().ordinal());
        obj.writeToNBT(tag);
    }

    private NetworkObject<MCPos> loadFromTag(NBTTagCompound tag){
        EnumNetworkObject type = EnumNetworkObject.VALUES[tag.getByte("id")];
        switch(type){
            case RAIL:
                return MCNetworkRail.fromTag(tag);
            case SIGNAL:
                return MCNetworkSignal.fromTag(tag);
            case REMOVAL_MARKER:
                return NetworkRemovalMarker.fromTag(tag);
            case RAIL_LINK:
                return MCNetworkRailLink.fromTag(tag);
            case STATION:
                return MCNetworkStation.fromTag(tag);
            case TELEPORT_RAIL:
                return MCNetworkTeleportRail.fromTag(tag);
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
    }

    public void writeToBuf(Collection<INetworkObject<MCPos>> objects, ByteBuf b){
        b.writeInt(objects.size());
        for(INetworkObject<MCPos> obj : objects) {
            writeToBuf(obj, b);
        }
    }

    public List<INetworkObject<MCPos>> readFromByteBuf(ByteBuf b){
        int count = b.readInt();
        List<INetworkObject<MCPos>> ret = new ArrayList<>(count);
        for(int i = 0; i < count; i++) {
            ret.add(loadFromBuf(b));
        }
        return ret;
    }

    private void writeToBuf(INetworkObject<MCPos> obj, ByteBuf b){
        writeToBuf(asSerializable(obj), b);
    }

    private void writeToBuf(ISerializableNetworkObject obj, ByteBuf b){
        b.writeByte(obj.getType().ordinal());
        obj.writeToBuf(b);
    }

    private INetworkObject<MCPos> loadFromBuf(ByteBuf b){
        EnumNetworkObject type = EnumNetworkObject.VALUES[b.readByte()];
        switch(type){
            case RAIL:
                return MCNetworkRail.fromByteBuf(b);
            case SIGNAL:
                return MCNetworkSignal.fromByteBuf(b);
            case REMOVAL_MARKER:
                return NetworkRemovalMarker.fromByteBuf(b);
            case RAIL_LINK:
                return MCNetworkRailLink.fromByteBuf(b);
            case STATION:
                return MCNetworkStation.fromByteBuf(b);
            case TELEPORT_RAIL:
                return MCNetworkTeleportRail.fromByteBuf(b);
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
    }
}
