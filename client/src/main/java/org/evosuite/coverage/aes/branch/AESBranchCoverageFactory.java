package org.evosuite.coverage.aes.branch;

import java.util.ArrayList;
import java.util.List;

import org.evosuite.Properties;
import org.evosuite.TestGenerationContext;
import org.evosuite.coverage.branch.Branch;
import org.evosuite.coverage.branch.BranchCoverageGoal;
import org.evosuite.coverage.branch.BranchCoverageTestFitness;
import org.evosuite.coverage.branch.BranchPool;
import org.evosuite.testsuite.AbstractFitnessFactory;

public class AESBranchCoverageFactory extends
	AbstractFitnessFactory<BranchCoverageTestFitness> {

	@Override
	public List<BranchCoverageTestFitness> getCoverageGoals() {
		
		String className = Properties.TARGET_CLASS;
		Class<?> targetClass = Properties.getTargetClass();
		BranchPool bpinstance = BranchPool.getInstance(TestGenerationContext.getInstance().getClassLoaderForSUT());
		
		List<BranchCoverageTestFitness> goals = new ArrayList<BranchCoverageTestFitness>();
		
		if (targetClass != null) {
			
			//branchless methods
			for (String methodName : bpinstance.getBranchlessMethods(className)) {
				goals.add(createRootBranchTestFitness(className, methodName));
			}
			
			//branches
			for (String methodName : bpinstance.knownMethods(className)) {
				for (Branch b : bpinstance.retrieveBranchesInMethod(className, methodName)) {
					if (!(b.getInstruction().isForcedBranch())) {
						goals.add(createBranchCoverageTestFitness(b, true));
						goals.add(createBranchCoverageTestFitness(b, false));
					}
				}
			}
			
		}
		
		return goals;
	}
	
	public static BranchCoverageTestFitness createRootBranchTestFitness(
			String className, String methodName) {

		return new BranchCoverageTestFitness(new BranchCoverageGoal(className,
				methodName.substring(methodName.lastIndexOf(".") + 1)));
	}
	
	public static BranchCoverageTestFitness createBranchCoverageTestFitness(
			Branch b, boolean branchExpressionValue) {

		return new BranchCoverageTestFitness(new BranchCoverageGoal(b,
				branchExpressionValue, b.getClassName(), b.getMethodName()));
	}
}
