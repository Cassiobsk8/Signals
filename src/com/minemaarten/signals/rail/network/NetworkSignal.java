package com.minemaarten.signals.rail.network;

import java.util.Collections;
import java.util.List;

/**
 * Immutable, and should expect to be reused in transitions from old to new networks to prevent allocations.
 * @author Maarten
 *
 * @param <TPos>
 */
public class NetworkSignal<TPos extends IPosition<TPos>> extends NetworkObject<TPos>{

    public static enum EnumSignalType{
        BLOCK, CHAIN;

        public static EnumSignalType[] VALUES = values();
    }

    public final EnumHeading heading;
    public final EnumSignalType type;

    public NetworkSignal(TPos pos, EnumHeading heading, EnumSignalType type){
        super(pos);
        this.heading = heading;
        this.type = type;
    }

    @Override
    public List<TPos> getNetworkNeighbors(){
        return Collections.singletonList(getRailPos());
    }

    public TPos getRailPos(){
        return getPos().offset(heading.rotateCCW());
    }

    //@formatter:off
    //TODO improve perf by querying smarter.
    public RailSection<TPos> getNextRailSection(RailNetwork<TPos> network){
        NetworkRail<TPos> rail = (NetworkRail<TPos>)network.railObjects.get(getRailPos()); //Safe to cast, as invalid signals have been filtered
        NetworkRail<TPos> nextSectionRail = network.railObjects.getNeighborRails(rail.getPotentialNeighborRailLocations())
                                                               .filter(r -> r.getPos().getRelativeHeading(rail.getPos()) == heading)
                                                               .findFirst()
                                                               .orElse(null);
        return nextSectionRail != null ? network.findSection(nextSectionRail.getPos()) : null;
    }
    //@formatter:on

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj){
        if(obj instanceof NetworkSignal) {
            NetworkSignal<TPos> other = (NetworkSignal<TPos>)obj;
            return super.equals(obj) && heading == other.heading && type == other.type;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode(){
        return (super.hashCode() * 31 + heading.hashCode()) * 31 + type.hashCode();
    }
}
