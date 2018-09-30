package com.minemaarten.signals.rail.network;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.minemaarten.signals.api.access.ISignal.EnumForceMode;
import com.minemaarten.signals.api.access.ISignal.EnumLampStatus;
import com.minemaarten.signals.rail.network.NetworkSignal.EnumSignalType;

/**
 * Contains the mutable state of a rail network, like the trains (positions and routes), the signal statusses
 * @author Maarten
 *
 */
public class NetworkState<TPos extends IPosition<TPos>> {
    private TIntObjectMap<Train<TPos>> trains = new TIntObjectHashMap<>();
    protected Map<TPos, EnumLampStatus> signalToLampStatusses = new HashMap<>();
    protected Map<TPos, EnumForceMode> signalForces = new HashMap<>();
    private Map<NetworkSignal<TPos>, Train<TPos>> trainsAtSignals = new HashMap<>();
    private Map<RailSection<TPos>, Train<TPos>> trainsOnSections = new HashMap<>();

    public void setTrains(Collection<? extends Train<TPos>> trains){
        this.trains = new TIntObjectHashMap<>(trains.size());
        for(Train<TPos> t : trains) {
            this.trains.put(t.id, t);
        }
    }

    public void addTrain(Train<TPos> train){
        trains.put(train.id, train);
    }

    public Train<TPos> getTrain(int id){
        return trains.get(id);
    }

    private Stream<Train<TPos>> getTrainsInAABB(PosAABB<TPos> aabb){
        return trains.valueCollection().stream().filter(t -> t.isInAABB(aabb));
    }

    public Iterable<Train<TPos>> getTrains(){
        return trains.valueCollection();
    }

    public Stream<Train<TPos>> getTrainStream(){
        return trains.valueCollection().stream();
    }

    public void removeTrain(Train<TPos> train){
        Train<TPos> t = trains.remove(train.id);
        if(t != null) {
            t.invalidate(this);
        }
    }

    public void update(RailNetwork<TPos> network){
        trains.valueCollection().forEach(train -> train.updatePositions(this));
        updateTrainsAtSignals(network);
        updateSignalStatusses(network);
        pathfindTrains(network);
        updateRailLinkHolds(network);
    }

    private void updateTrainsAtSignals(RailNetwork<TPos> network){
        Map<NetworkSignal<TPos>, Train<TPos>> newTrainsAtSignals = new HashMap<>(); //Can't use Collectors.toMap because of null values https://stackoverflow.com/questions/24630963/java-8-nullpointerexception-in-collectors-tomap
        network.railObjects.getSignals().forEach(s -> newTrainsAtSignals.put(s, getTrainAtSignal(network, s)));

        for(Map.Entry<NetworkSignal<TPos>, Train<TPos>> entry : newTrainsAtSignals.entrySet()) {
            Train<TPos> curTrain = entry.getValue();
            Train<TPos> prevTrain = trainsAtSignals.get(entry.getKey());
            if(prevTrain != null && curTrain != prevTrain) { //When the prev train left this signal
                setForceMode(network, entry.getKey().getPos(), EnumForceMode.NONE);
            }
        }
        trainsAtSignals = newTrainsAtSignals;
    }

    public void updateTrainAtSections(Train<TPos> train, Iterable<RailSection<TPos>> prevSections, Iterable<RailSection<TPos>> newSections){
        for(RailSection<TPos> prevSection : prevSections) {
            trainsOnSections.remove(prevSection);
        }

        for(RailSection<TPos> newSection : newSections) {
            trainsOnSections.put(newSection, train);
        }
    }

    private void updateSignalStatusses(RailNetwork<TPos> network){
        List<NetworkSignal<TPos>> allSignals = network.railObjects.getSignals();
        Map<TPos, EnumLampStatus> prevLampStatusses = signalToLampStatusses;
        signalToLampStatusses = new HashMap<>();

        //First evaluate the block signal statusses
        //The status of these is independent of other signals
        for(NetworkSignal<TPos> signal : allSignals) {
            if(signal.type == EnumSignalType.BLOCK) {
                EnumLampStatus signalStatus = getForcedStatus(signal.getPos());
                if(signalStatus == null) signalStatus = getBlockSignalStatus(network, signal);
                signalToLampStatusses.put(signal.getPos(), signalStatus);
            }
        }

        //Then evaluate the chain signals
        //We might need multiple iterations, as chain signal statuses are dependent of the next signal's status.
        //TODO more efficient, by arranging a signal list, so that statuses can be determined in one iteration (built when the network is rebuilt)
        //@formatter:off
        Set<NetworkSignal<TPos>> toEvaluate = allSignals.stream().filter(s -> s.type == EnumSignalType.CHAIN).collect(Collectors.toSet());
        while(!toEvaluate.isEmpty()){
            boolean hasEvaluated = false; //Flag to make sure we do evaluate something every cycle.
            Iterator<NetworkSignal<TPos>> iterator = toEvaluate.iterator();
            while(iterator.hasNext()){
                NetworkSignal<TPos> chainSignal = iterator.next();
                EnumLampStatus signalStatus = getForcedStatus(chainSignal.getPos());
                if(signalStatus == null) signalStatus = getChainSignalStatus(network, toEvaluate, chainSignal);
                if(signalStatus != EnumLampStatus.YELLOW_BLINKING){ //If the signal status could be evaluated
                    signalToLampStatusses.put(chainSignal.getPos(), signalStatus);
                    iterator.remove();
                    hasEvaluated = true;
                }
            }
            
            //If we couldn't evaluate any signals, we are probably recursively looking, break this by allowing a signal to turn green.
            if(!hasEvaluated){
                iterator = toEvaluate.iterator();
                NetworkSignal<TPos> chainSignal = iterator.next();
                
                iterator.remove();
                signalToLampStatusses.put(chainSignal.getPos(), EnumLampStatus.GREEN);
            }
        }
        //@formatter:on

        Map<TPos, EnumLampStatus> changedSignals = getChangedSignals(prevLampStatusses, signalToLampStatusses);
        if(!changedSignals.isEmpty()) {
            onSignalsChanged(changedSignals);
        }
    }

    protected void onSignalsChanged(Map<TPos, EnumLampStatus> changedSignals){

    }

    /**
     * Cleanup the forced signals when the signal is removed.
     * @param network
     */
    public void onNetworkChanged(RailNetwork<TPos> network){
        signalForces.keySet().removeIf(pos -> !(network.railObjects.get(pos) instanceof NetworkSignal));
    }

    private Map<TPos, EnumLampStatus> getChangedSignals(Map<TPos, EnumLampStatus> prevStatusses, Map<TPos, EnumLampStatus> newStatusses){
        Map<TPos, EnumLampStatus> changedSignals = new HashMap<>();
        for(Map.Entry<TPos, EnumLampStatus> status : newStatusses.entrySet()) {
            EnumLampStatus prevStatus = prevStatusses.get(status.getKey());
            if(status.getValue() != prevStatus) {
                changedSignals.put(status.getKey(), status.getValue());
            }
        }
        return changedSignals;
    }

    private EnumLampStatus getChainSignalStatus(RailNetwork<TPos> network, Set<NetworkSignal<TPos>> toEvaluate, NetworkSignal<TPos> chainSignal){
        EnumLampStatus blockSignalStatus = getBlockSignalStatus(network, chainSignal);
        if(blockSignalStatus == EnumLampStatus.RED || blockSignalStatus == EnumLampStatus.YELLOW) { //It is not going to get any greener if there's a train in the way, or the next section was claimed
            return blockSignalStatus;
        } else {
            Train<TPos> routedTrain = trainsAtSignals.get(chainSignal);
            if(routedTrain != null) {
                if(routedTrain.getCurRoute() != null) {
                    return evaluateCurRoutedTrain(network, routedTrain, chainSignal);
                } else {
                    return EnumLampStatus.RED; //A cart with no route cannot be routed.
                }
            } else {//If we are not routing a train, the status of this signal is just for visuals
                RailSection<TPos> nextRailSection = chainSignal.getNextRailSection(network);
                if(nextRailSection == null) return EnumLampStatus.GREEN; //No next section is OK
                Set<EnumLampStatus> nextSignalStatusses = nextRailSection.getSignals().map(s -> getLampStatus(s.getPos())).collect(Collectors.toSet());
                if(nextSignalStatusses.size() == 1) {
                    return nextSignalStatusses.iterator().next();
                } else {
                    return EnumLampStatus.YELLOW; //Different signals, we don't know
                }
            }
        }
    }

    private EnumLampStatus evaluateCurRoutedTrain(RailNetwork<TPos> network, Train<TPos> train, NetworkSignal<TPos> curSignal){
        RailRoute<TPos> route = train.getCurRoute();

        if(isTrainClaimingNextSection(network, curSignal, train)) {
            return EnumLampStatus.RED;
        }

        for(NetworkSignal<TPos> signalInRoute : route.routeSignals) {
            if(signalInRoute != curSignal) {
                EnumLampStatus nextSignalStatus = getLampStatus(signalInRoute.getPos());
                if(nextSignalStatus == EnumLampStatus.YELLOW) {
                    if(isTrainClaimingNextSection(network, signalInRoute, train)) {
                        return EnumLampStatus.RED;
                    }
                    if(signalInRoute.type == EnumSignalType.BLOCK) return EnumLampStatus.GREEN;
                } else {
                    return nextSignalStatus; //copy whatever the next signal says (even YELLOW_BLINKING)
                }
            }
        }

        return EnumLampStatus.GREEN;
    }

    private EnumLampStatus getBlockSignalStatus(RailNetwork<TPos> network, NetworkSignal<TPos> signal){
        RailSection<TPos> nextSection = signal.getNextRailSection(network);
        if(nextSection != null) {
            Train<TPos> trainOnSection = trainsOnSections.get(nextSection);

            //When there's a train on the next section, and it is not a train that's exiting this signal
            if(trainOnSection != null && !trainOnSection.getPositions().contains(signal.getRailPos())) {
                return EnumLampStatus.RED;
            } else {
                Train<TPos> trainClaimingSection = getClaimingTrain(nextSection);
                if(trainClaimingSection != null && !trainClaimingSection.equals(trainsAtSignals.get(signal))) {
                    return EnumLampStatus.YELLOW; //Claimed by another train.
                } else {
                    return EnumLampStatus.GREEN;
                }
            }
        } else {
            return EnumLampStatus.GREEN;
        }
    }

    private boolean isTrainClaimingNextSection(RailNetwork<TPos> network, NetworkSignal<TPos> signal, Train<TPos> ignoredTrain){
        RailSection<TPos> nextSection = signal.getNextRailSection(network);
        if(nextSection == null) return false;

        Train<TPos> trainClaimingSection = getClaimingTrain(nextSection);
        return trainClaimingSection != null && !trainClaimingSection.equals(ignoredTrain);
    }

    public EnumForceMode getForceMode(TPos signalPos){
        EnumForceMode forceMode = signalForces.get(signalPos);
        return forceMode != null ? forceMode : EnumForceMode.NONE;
    }

    private EnumLampStatus getForcedStatus(TPos signalPos){
        EnumForceMode forceMode = getForceMode(signalPos);
        if(forceMode == EnumForceMode.FORCED_GREEN_ONCE) return EnumLampStatus.GREEN;
        if(forceMode == EnumForceMode.FORCED_RED) return EnumLampStatus.RED;
        return null;
    }

    public void setForceMode(RailNetwork<TPos> network, TPos signalPos, EnumForceMode forceMode){
        if(network.railObjects.get(signalPos) instanceof NetworkSignal) {
            if(forceMode != EnumForceMode.NONE) {
                signalForces.put(signalPos, forceMode);
            } else {
                signalForces.remove(signalPos);
            }
            onForceModeChanged(signalPos, forceMode);
        }
    }

    protected void onForceModeChanged(TPos signalPos, EnumForceMode forceMode){

    }

    public Train<TPos> getTrainAtPositions(List<TPos> positions){
        PosAABB<TPos> aabb = new PosAABB<>(positions);
        return getTrainsInAABB(aabb).findFirst().orElse(null);
    }

    private Train<TPos> getTrainAtSignal(RailNetwork<TPos> network, NetworkSignal<TPos> signal){
        return getTrainAtPositions(network.getPositionsInFront(signal));
    }

    protected void setLampStatus(TPos signalPos, EnumLampStatus status){
        signalToLampStatusses.put(signalPos, status);
    }

    public EnumLampStatus getLampStatus(TPos signalPos){
        return signalToLampStatusses.getOrDefault(signalPos, EnumLampStatus.YELLOW_BLINKING);
    }

    public Train<TPos> getClaimingTrain(RailSection<TPos> section){
        for(Train<TPos> train : trains.valueCollection()) {
            if(train.getClaimedSections().contains(section)) {
                return train;
            }
        }
        return null;
    }

    private void pathfindTrains(RailNetwork<TPos> network){
        for(NetworkSignal<TPos> signal : network.railObjects.getSignals()) {
            if(signal.type == EnumSignalType.CHAIN || getLampStatus(signal.getPos()) == EnumLampStatus.GREEN) {
                pathfindTrains(network, signal); //Only check signals that signal green, or are route dependent
            }
        }
    }

    private void pathfindTrains(RailNetwork<TPos> network, NetworkSignal<TPos> signal){
        Train<TPos> trainAtSignal = trainsAtSignals.get(signal);
        if(trainAtSignal != null && trainAtSignal.shouldPathfind(signal.getPos())) {
            RailRoute<TPos> route = trainAtSignal.pathfind(signal.getRailPos(), signal.heading);
            if(trainAtSignal.tryUpdatePath(network, this, route) && signal.type == EnumSignalType.CHAIN) {
                EnumLampStatus status = getChainSignalStatus(network, new HashSet<>(), signal);
                if(status != EnumLampStatus.GREEN) {
                    trainAtSignal.setPath(null); //Only claim sections when the train can actually travel to the other side of the intersection.
                }
            }
            onCartRouted(trainAtSignal, trainAtSignal.getCurRoute());
        }
    }

    protected void onCartRouted(Train<TPos> train, RailRoute<TPos> route){

    }

    private void updateRailLinkHolds(RailNetwork<TPos> network){
        for(Train<TPos> train : trains.valueCollection()) {
            for(TPos trainPos : train.getPositions()) {
                int holdDelay = network.getRailLinkDelayFor(trainPos);
                if(holdDelay > 0) {
                    train.addRailLinkHold(trainPos, holdDelay);
                }
            }
        }
    }
}
