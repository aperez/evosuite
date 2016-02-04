package org.evosuite.coverage.aes.method;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.evosuite.Properties;
import org.evosuite.coverage.method.MethodCoverageTestFitness;
import org.evosuite.setup.TestUsageChecker;
import org.evosuite.testsuite.AbstractFitnessFactory;
import org.objectweb.asm.Type;

public class AESMethodCoverageFactory extends 
	AbstractFitnessFactory<MethodCoverageTestFitness> {

	@Override
	public List<MethodCoverageTestFitness> getCoverageGoals() {
		String className = Properties.TARGET_CLASS;
		Class<?> targetClass = Properties.getTargetClass();
		List<MethodCoverageTestFitness> goals = new ArrayList<MethodCoverageTestFitness>();
		
		if (targetClass != null) {
			Constructor<?>[] allConstructors = targetClass.getDeclaredConstructors();
			
			for (Constructor<?> c : allConstructors) {
				if (TestUsageChecker.canUse(c)) {
					String methodName = "<init>" + Type.getConstructorDescriptor(c);
					goals.add(new MethodCoverageTestFitness(className, methodName));
				}
			}
			
			Method[] allMethods = targetClass.getDeclaredMethods();
			for (Method m : allMethods) {
				if (TestUsageChecker.canUse(m)) {
					String methodName = m.getName() + Type.getMethodDescriptor(m);
					goals.add(new MethodCoverageTestFitness(className, methodName));
				}
			}
		}
		
		return goals;
	}
}
