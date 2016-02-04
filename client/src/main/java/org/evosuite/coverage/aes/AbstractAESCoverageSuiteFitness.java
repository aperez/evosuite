package org.evosuite.coverage.aes;

import java.util.List;

import org.evosuite.testcase.ExecutableChromosome;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testsuite.AbstractTestSuiteChromosome;
import org.evosuite.testsuite.TestSuiteFitnessFunction;

public abstract class AbstractAESCoverageSuiteFitness extends TestSuiteFitnessFunction {

	private static final long serialVersionUID = 5184507726269266351L;
	
	public static enum Metric { AES, DTR };
	private Metric metric;
	
	public AbstractAESCoverageSuiteFitness(Metric metric) {
		this.metric = metric;
	}
	
	@Override
	public double getFitness(AbstractTestSuiteChromosome<? extends ExecutableChromosome> suite) {
		List<ExecutionResult> results = runTestSuite(suite);
		
		Spectrum spectrum = getSpectrum(results);
		double fitness = metricToFitness( getMetric(spectrum) );
		
		updateIndividual(this, suite, fitness);
		return fitness;
	}

	protected abstract Spectrum getSpectrum(List<ExecutionResult> results);

	public double getMetric(Spectrum spectrum) {
		switch (this.metric) {
		case DTR:
			return spectrum.getDistinctTransactionsRho() * spectrum.getAmbiguity();
		case AES:
		default:
			return spectrum.getRho() * (1.0 - spectrum.getSimpson()) * spectrum.getAmbiguity();
		}
	}
	
	public static double metricToFitness(double metric) {
		return Math.abs(0.5d - metric);
	}
	
}
