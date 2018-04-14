package com.minemaarten.signals.client.render.signals;

import com.minemaarten.signals.rail.network.NetworkRail;
import com.minemaarten.signals.rail.network.RailEdge;
import com.minemaarten.signals.rail.network.RailObjectHolder;
import com.minemaarten.signals.rail.network.mc.MCPos;
import com.minemaarten.signals.rail.network.mc.RailNetworkManager;

public class RailEdgeRenderer extends AbstractRailRenderer<RailEdge<MCPos>>{

    @Override
    protected boolean isAdjacent(RailEdge<MCPos> s1, RailEdge<MCPos> s2){
        return s1.contains(s2.startPos) || s1.contains(s2.endPos);
    }

    @Override
    protected Iterable<RailEdge<MCPos>> getRenderableSections(){
        return RailNetworkManager.getInstance().getNetwork().getAllEdges();
    }

    @Override
    protected NetworkRail<MCPos> getRootNode(RailEdge<MCPos> section){
        return section.iterator().next();
    }

    @Override
    protected RailObjectHolder<MCPos> getNeighborProvider(RailEdge<MCPos> section){
        return section.railObjects;
    }

    @Override
    protected boolean shouldTraverse(RailEdge<MCPos> section, NetworkRail<MCPos> rail){
        return true;
    }
}
