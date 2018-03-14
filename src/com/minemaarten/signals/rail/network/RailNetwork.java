package com.minemaarten.signals.rail.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.minemaarten.signals.rail.RailObjectHolder;

public class RailNetwork<TPos extends IPosition<TPos>> {

    public final RailObjectHolder<TPos> railObjects;
    private Set<RailSection<TPos>> railSections = new HashSet<>();//TODO
    //private Map<TPos, RailSection<TPos>> railPosToRailSections = new HashMap<>();//TODO
    private Set<RailEdge<TPos>> allEdges = new HashSet<>();

    /**
     * Given a position of a path node, which edges move away from this node?
     */
    //  private Multimap<TPos, RailEdge<TPos>> positionsToEdgesForward = ArrayListMultimap.create();//TODO

    /**
     * Given a position of a path node, which edges can end up in this node?
     */
    private Multimap<TPos, RailEdge<TPos>> positionsToEdgesBackward = ArrayListMultimap.create();

    /**
     * given any rail pos, which edge belongs to this rail?
     * Intersections don't return an edge.
     */
    private Map<TPos, RailEdge<TPos>> railPosToRailEdges = new HashMap<>();

    public RailNetwork(List<NetworkObject<TPos>> allNetworkObjects){
        this.railObjects = new RailObjectHolder<>(allNetworkObjects);
        //TODO build network cache

        buildRailEdges();
    }

    /* private void buildRailSections(){
         //Wrap in HashSet because java doesn't guarentee mutability with Collectors.toSet()
         Set<TPos> toTraverse = new HashSet<>(getRails().map(r -> r.pos).collect(Collectors.toList()));

         while(!toTraverse.isEmpty()) {
             TPos first = toTraverse.iterator().next();
             Map<TPos, NetworkObject<TPos>> sectionObjects = new HashMap<>();

         }
     }

     private void addSection(RailSection<TPos> section){
         railSections.add(section);

     }*/

    /*public RailSection<TPos> findSection(TPos pos){
        return railPosToRailSections.get(pos);
    }*/

    //TODO reduce to intersections
    private void buildRailEdges(){
        //Wrap in HashSet because java doesn't guarantee mutability with Collectors.toSet()
        Set<NetworkRail<TPos>> toTraverse = new HashSet<>(railObjects.getRails().collect(Collectors.toList()));

        while(!toTraverse.isEmpty()) {
            Iterator<NetworkRail<TPos>> toTraverseIterator = toTraverse.iterator();
            NetworkRail<TPos> first = toTraverseIterator.next();
            toTraverseIterator.remove(); //Remove so we don't evaluate it again.

            List<NetworkRail<TPos>> edge = new ArrayList<>();
            Set<NetworkRail<TPos>> edgeSet = new HashSet<>();
            edge.add(first);
            edgeSet.add(first);

            Stack<NetworkRail<TPos>> edgeToTraverse = new Stack<>();
            edgeToTraverse.push(first);

            boolean startHitIntersection = false;//Used to keep track if the edge stopped because of a dead end or intersection.
            boolean endHitIntersection = false;

            while(!edgeToTraverse.isEmpty()) {
                NetworkRail<TPos> curRail = edgeToTraverse.pop();
                List<NetworkRail<TPos>> neighbors = railObjects.getNeighborRails(curRail.getPotentialNeighborRailLocations()).collect(Collectors.toList());
                if(neighbors.size() < 3) {
                    for(NetworkRail<TPos> neighbor : neighbors) {
                        if(toTraverse.remove(neighbor)) {//If not on an intersection, expand further
                            edgeToTraverse.push(neighbor);
                        }

                        //Add to the current edge, make sure the list is in sequence, so that index 0 is a neighbor of index 1, which is
                        //a neighbor of index 2, etc.
                        if(edgeSet.add(neighbor)) {
                            if(edge.get(edge.size() - 1).pos.equals(curRail.pos)) {
                                edge.add(neighbor);
                            } else if(edge.get(0).pos.equals(curRail.pos)) {
                                edge.add(0, neighbor);
                            } else {
                                throw new IllegalStateException("Currently evaluated pos is not at the start or end of an edge!");
                            }
                        }
                    }
                } else {
                    if(edge.get(edge.size() - 1).pos.equals(curRail.pos)) endHitIntersection = true;
                    if(edge.get(0).pos.equals(curRail.pos)) startHitIntersection = true;
                }
            }

            //Only create edges of 2 or higher, as we are not interested in just intersections
            if(edge.size() > 1) {
                RailEdge<TPos> railEdge = new RailEdge<>(railObjects, ImmutableList.copyOf(edge));
                if(!edge.get(0).pos.equals(railEdge.startPos)) { //If the order got reversed, switch the hit flags
                    boolean swap = startHitIntersection;
                    startHitIntersection = endHitIntersection;
                    endHitIntersection = swap;
                }
                addEdge(railEdge, startHitIntersection, endHitIntersection);
            }
        }
    }

    private void addEdge(RailEdge<TPos> edge, boolean startHitIntersection, boolean endHitIntersection){
        if(allEdges.add(edge)) {
            if(!edge.unidirectional) positionsToEdgesBackward.put(edge.startPos, edge);
            positionsToEdgesBackward.put(edge.endPos, edge);
            for(int i = startHitIntersection ? 1 : 0; i < edge.length - (endHitIntersection ? 1 : 0); i++) {
                railPosToRailEdges.put(edge.get(i), edge);
            }
        }
    }

    /**
     * given any rail pos, which edge belongs to this rail?
     * Intersections don't return an edge.
     */
    public RailEdge<TPos> findEdge(TPos pos){
        return railPosToRailEdges.get(pos);
    }

    public Collection<RailEdge<TPos>> findConnectedEdgesBackwards(TPos intersection){
        return positionsToEdgesBackward.get(intersection);
    }

    /**
     * Pathfinds from 'from' to one of the 'destinations', in the given direction 'direction'.
     * @param from
     * @param direction
     * @param destinations
     * @return a route, or null, if no path was found
     */
    public RailRoute<TPos> pathfind(TPos from, EnumHeading direction, Set<TPos> destinations){

        return new RailPathfinder<TPos>(this).pathfindToDestination(from, direction, destinations);
    }
}
