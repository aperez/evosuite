package org.evosuite.coverage.aes;

import org.evosuite.coverage.aes.AbstractAESCoverageSuiteFitness.Metric;
import org.evosuite.coverage.aes.branch.AESBranchCoverageSuiteFitness;
import org.evosuite.coverage.aes.method.AESMethodCoverageSuiteFitness;
import org.evosuite.coverage.aes.method.AESPublicMethodCoverageSuiteFitness;
import org.evosuite.rmi.ClientServices;
import org.evosuite.statistics.RuntimeVariable;
import org.evosuite.testsuite.TestSuiteChromosome;

public class AESUtils {

	public static void trackAESMetrics(TestSuiteChromosome testSuite) {
		AESBranchCoverageSuiteFitness branchFitness = new AESBranchCoverageSuiteFitness();
		ClientServices.getInstance().getClientNode().trackOutputVariable(RuntimeVariable.BasicBranchCoverage, branchFitness.getBasicCoverage(testSuite));
		ClientServices.getInstance().getClientNode().trackOutputVariable(RuntimeVariable.AESBranchCoverage, branchFitness.getMetric(testSuite));
		branchFitness = new AESBranchCoverageSuiteFitness(Metric.DTR);
		ClientServices.getInstance().getClientNode().trackOutputVariable(RuntimeVariable.AESBranchCoverageDTR, branchFitness.getMetric(testSuite));
		
		AESMethodCoverageSuiteFitness methodFitness = new AESMethodCoverageSuiteFitness();
		ClientServices.getInstance().getClientNode().trackOutputVariable(RuntimeVariable.BasicMethodCoverage, methodFitness.getBasicCoverage(testSuite));
		ClientServices.getInstance().getClientNode().trackOutputVariable(RuntimeVariable.AESMethodCoverage, methodFitness.getMetric(testSuite));
		methodFitness = new AESMethodCoverageSuiteFitness(Metric.DTR);
		ClientServices.getInstance().getClientNode().trackOutputVariable(RuntimeVariable.AESMethodCoverageDTR, methodFitness.getMetric(testSuite));
		
		AESPublicMethodCoverageSuiteFitness publicMethodFitness = new AESPublicMethodCoverageSuiteFitness();
		ClientServices.getInstance().getClientNode().trackOutputVariable(RuntimeVariable.BasicPublicMethodCoverage, publicMethodFitness.getBasicCoverage(testSuite));
		ClientServices.getInstance().getClientNode().trackOutputVariable(RuntimeVariable.AESPublicMethodCoverage, publicMethodFitness.getMetric(testSuite));
		publicMethodFitness = new AESPublicMethodCoverageSuiteFitness(Metric.DTR);
		ClientServices.getInstance().getClientNode().trackOutputVariable(RuntimeVariable.AESPublicMethodCoverageDTR, publicMethodFitness.getMetric(testSuite));
	}
	
}
