package com.simibubi.create.modules.contraptions;

import java.util.HashMap;
import java.util.Map;

import com.simibubi.create.modules.contraptions.base.KineticTileEntity;

public class KineticNetwork {

	public Long id;
	public boolean initialized;
	public Map<KineticTileEntity, Float> sources;
	public Map<KineticTileEntity, Float> members;

	private float currentCapacity;
	private float currentStress;
	private float unloadedCapacity;
	private float unloadedStress;

	public KineticNetwork() {
		sources = new HashMap<>();
		members = new HashMap<>();
	}

	public void initFromTE(float maxStress, float currentStress) {
		unloadedCapacity = maxStress;
		unloadedStress = currentStress;
		initialized = true;
		updateStress();
		updateCapacity();
	}

	public void addSilently(KineticTileEntity te) {
		if (members.containsKey(te))
			return;
		if (te.isSource()) {
			float capacity = te.getAddedStressCapacity();
			unloadedCapacity -= capacity * getStressMultiplierForSpeed(te.getGeneratedSpeed());
			sources.put(te, capacity);
		}
		float stressApplied = te.getStressApplied();
		unloadedStress -= stressApplied * getStressMultiplierForSpeed(te.getTheoreticalSpeed());
		members.put(te, stressApplied);
	}

	public void add(KineticTileEntity te) {
		if (members.containsKey(te))
			return;
		if (te.isSource())
			sources.put(te, te.getAddedStressCapacity());
		members.put(te, te.getStressApplied());
		te.updateStressFromNetwork(currentCapacity, currentStress);
		te.networkDirty = true;
	}

	public void updateCapacityFor(KineticTileEntity te, float capacity) {
		sources.put(te, capacity);
		updateCapacity();
	}

	public void updateStressFor(KineticTileEntity te, float stress) {
		members.put(te, stress);
		updateStress();
	}

	public void remove(KineticTileEntity te) {
		if (!members.containsKey(te))
			return;
		if (te.isSource())
			sources.remove(te);
		members.remove(te);
		te.updateStressFromNetwork(0, 0);

		if (members.isEmpty()) {
			TorquePropagator.networks.get(te.getWorld()).remove(this.id);
			return;
		}

		members.keySet().stream().findFirst().map(member -> member.networkDirty = true);
	}

	public void sync() {
		for (KineticTileEntity te : members.keySet())
			te.updateStressFromNetwork(currentCapacity, currentStress);
	}

	public void updateCapacity() {
		float newMaxStress = calculateCapacity();
		if (currentCapacity != newMaxStress) {
			currentCapacity = newMaxStress;
			sync();
		}
	}

	public void updateStress() {
		float newStress = calculateStress();
		if (currentStress != newStress) {
			currentStress = newStress;
			sync();
		}
	}

	public void updateNetwork() {
		float newStress = calculateStress();
		float newMaxStress = calculateCapacity();
		if (currentStress != newStress || currentCapacity != newMaxStress) {
			currentStress = newStress;
			currentCapacity = newMaxStress;
			sync();
		}
	}

	public float calculateCapacity() {
		float presentCapacity = 0;
		for (KineticTileEntity te : sources.keySet())
			presentCapacity += sources.get(te) * getStressMultiplierForSpeed(te.getGeneratedSpeed());
		float newMaxStress = presentCapacity + unloadedCapacity;
		return newMaxStress;
	}

	public float calculateStress() {
		float presentStress = 0;
		for (KineticTileEntity te : members.keySet())
			presentStress += members.get(te) * getStressMultiplierForSpeed(te.getTheoreticalSpeed());
		float newStress = presentStress + unloadedStress;
		return newStress;
	}

	private float getStressMultiplierForSpeed(float speed) {
		return Math.abs(speed);
	}

}
