package org.evosuite.coverage.aes;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

public class Spectrum {

	private ArrayList<BitSet> transactions;
	private int numComponents = 0;
	
	public Spectrum() {
	}
	
	public Spectrum(int transactions, int components) {
		this.setSize(transactions, components);
	}
	
	public void setSize(int transactions, int components) {
		this.setNumComponents(components);
		this.setNumTransactions(transactions);
	}
	
	public void setNumComponents(int size) {
		this.numComponents = size;
	}
	
	public void setNumTransactions(int size) {
		if (transactions == null) {
			transactions = new ArrayList<BitSet>(size);
		}
		else {
			transactions.clear();
			transactions.ensureCapacity(size);
		}
		
		for (int i = 0; i < size; i++) {
			transactions.add(new BitSet(numComponents));
		}
	}
	
	public int getNumComponents() {
		return this.numComponents;
	}
	
	public int getNumTransactions() {
		if (transactions == null) {
			return 0;
		}
		return this.transactions.size();
	}
	
	public void setInvolved(int transaction, int component, boolean involvement) {
		if (transaction >= 0 && transaction < this.getNumTransactions() && 
			component   >= 0 && component   < this.getNumComponents()) {
			this.transactions.get(transaction).set(component, involvement);
		}
	}
	
	public void setInvolved(int transaction, int component) {
		this.setInvolved(transaction, component, true);
	}

	public boolean getInvolved(int transaction, int component) {
		if (transaction >= 0 && transaction < this.getNumTransactions() && 
			component   >= 0 && component   < this.getNumComponents()) {
			return this.transactions.get(transaction).get(component);
		}
		return false;
	}

	protected boolean isValidMatrix() {
		return this.getNumComponents() > 0 && this.getNumTransactions() > 0;

	}
	
	public double getRho() {
		
		if (!this.isValidMatrix()) return 0d;
		
		double activityCounter = 0d;
		for (BitSet transaction : this.transactions) {
			activityCounter += transaction.cardinality();
		}
		
		double rho = activityCounter / ( ((double) this.getNumComponents()) * ((double) this.getNumTransactions()) );
		return rho;
	}
	
	public double getDistinctTransactionsRho() {
		
		if (!this.isValidMatrix()) return 0d;
		
		Set<BitSet> distinctTransactionsSet = new HashSet<BitSet>(this.transactions);
		
		double activityCounter = 0d;
		for (BitSet transaction : distinctTransactionsSet) {
			activityCounter += transaction.cardinality();
		}
		
		double rho = activityCounter / ( ((double) this.getNumComponents()) * ((double) distinctTransactionsSet.size()) );
		return rho;
	}
	
	public double getSimpson() {
		
		if (!this.isValidMatrix()) return 0d;
		
		LinkedHashMap<Integer, Integer> species = new LinkedHashMap<Integer, Integer>();
		for (BitSet transaction : transactions) {
			int hash = transaction.hashCode();
			if (species.containsKey(hash)) {
				species.put(hash, species.get(hash) + 1);
			}
			else {
				species.put(hash, 1);
			}
		}
		
		double n = 0.0;
		double N = 0.0;
		
		for (int s : species.keySet()) {
			double ni = species.get(s);
			n += ni * (ni - 1);
			N += ni;
		}
		
		double diversity = ( (N == 0.0) || ((N - 1) == 0) ) ? 1.0 : n / (N * (N - 1));
		return diversity;
	}
	
	public double getAmbiguity() {
		
		if (!this.isValidMatrix()) return 0d;
		
		Set<Integer> ambiguityGroups = new HashSet<Integer>();
		
		int transactions = this.getNumTransactions();
		int components = this.getNumComponents();
		
		for (int c = 0; c < components; c++) {
			BitSet bs = new BitSet(transactions);
			
			for (int t = 0; t < transactions; t++) {
				if (this.getInvolved(t, c)) {
					bs.set(t);
				}
			}
			
			ambiguityGroups.add(bs.hashCode());
		}
		
		double ambiguity = (double) ambiguityGroups.size() / (double) components;
		return ambiguity;
	}
}
